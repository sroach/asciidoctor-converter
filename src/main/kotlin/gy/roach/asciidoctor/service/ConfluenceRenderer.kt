package gy.roach.asciidoctor.service

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.jira.converter.JiraConverterExtension
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.util.misc.Extension
import gy.roach.asciidoctor.config.ConverterSettings
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.Base64
import java.util.zip.GZIPOutputStream

@Service
class JiraConverter(private val converterSettings: ConverterSettings) {

    private  val DEFAULT_SCALE = "1.0"
    private  val DEFAULT_TYPE = "svg"
    private  val DEFAULT_USE_DARK = "false"
    private  val DEFAULT_USE_GLASS = "false"
    private  val DEFAULT_BACKEND = "html5"

    private val docOpsMacroRegex = Regex(
        pattern = """(?s)\[docops:(\w+)(.*?)]\s*(.*?)\s*\[/docops]"""
    )

    private fun compressAndEncode(content: String): String {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(content.toByteArray(Charsets.UTF_8))
        }
        return Base64.getUrlEncoder().encodeToString(output.toByteArray())
    }

    private fun parseOptions(optionsStr: String): Map<String, String> {
        if (optionsStr.isBlank()) return emptyMap()
        val options = mutableMapOf<String, String>()
        val optionRegex = Regex("""(\w+)=(?:"([^"]*)"|'([^']*)'|(\S+))""")
        optionRegex.findAll(optionsStr).forEach { m ->
            val key = m.groupValues[1]
            val value = m.groupValues[2].ifBlank {
                m.groupValues[3].ifBlank { m.groupValues[4] }
            }
            options[key] = value
        }
        return options
    }

    private fun buildDocOpsSvgUrl(
        webserver: String,
        kind: String,
        body: String,
        opts: Map<String, String>
    ): String {
        val payload = compressAndEncode(body)
        return buildString {
            append("$webserver/api/docops/svg?")
            append("kind=${URLEncoder.encode(kind, Charsets.UTF_8)}")
            append("&payload=${URLEncoder.encode(payload, Charsets.UTF_8)}")
            append("&scale=${opts["scale"] ?: DEFAULT_SCALE}")
            append("&type=${opts["type"] ?: DEFAULT_TYPE}")
            append("&useDark=${opts["useDark"] ?: DEFAULT_USE_DARK}")
            opts["title"]?.let { append("&title=${URLEncoder.encode(it, Charsets.UTF_8)}") }
            append("&useGlass=${opts["useGlass"] ?: DEFAULT_USE_GLASS}")
            append("&backend=${opts["backend"] ?: DEFAULT_BACKEND}")
            opts["docname"]?.let { append("&docname=${URLEncoder.encode(it, Charsets.UTF_8)}") }
            append("&filename=generated.svg")
        }
    }

    private fun replaceDocOpsMacrosWithWikiImages(markdown: String, webserver: String): String {
        return docOpsMacroRegex.replace(markdown) { match ->
            val kind = match.groupValues[1]
            val rawOptions = match.groupValues[2].trim()
            val body = match.groupValues[3].trim()
            val opts = parseOptions(rawOptions)

            val url = buildDocOpsSvgUrl(
                webserver = webserver,
                kind = kind,
                body = body,
                opts = opts
            )

            val caption = opts["caption"]?.takeIf { it.isNotBlank() }
            if (caption != null) {
                "!$url!\n_Figure. ${caption}_"
            } else {
                "!$url!"
            }
        }
    }

    fun markdownToJira(markdown: String): String {
        val markdownWithDocOpsImages = replaceDocOpsMacrosWithWikiImages(
            markdown = markdown,
            webserver = converterSettings.panelWebserver
        )
        val extensions = mutableListOf<Extension>(JiraConverterExtension.create())
        val options = MutableDataSet().apply {
            set(Parser.EXTENSIONS, extensions)
            set(HtmlRenderer.TYPE, "JIRA")
        }

        val parser: Parser = Parser.builder(options).build()
        val renderer: HtmlRenderer = HtmlRenderer.builder(options).build()
        return renderer.render(parser.parse(markdownWithDocOpsImages))
    }
}
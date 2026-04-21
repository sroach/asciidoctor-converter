package gy.roach.asciidoctor.service

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import gy.roach.asciidoctor.config.ConverterSettings
import org.asciidoctor.Asciidoctor
import org.asciidoctor.Attributes
import org.asciidoctor.Options
import org.asciidoctor.SafeMode
import org.jsoup.Jsoup
import org.springframework.stereotype.Service
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2

@Service
class AsciiDoctorToWiki(private val converter : JiraConverter, private val converterSettings: ConverterSettings) {


    fun convertToWiki(file: File, targetPath: String, targetWikiFile: File) {
        val localAsciidoctor = Asciidoctor.Factory.create()
        localAsciidoctor.requireLibrary("asciidoctor-diagram")

        localAsciidoctor.rubyExtensionRegistry().loadClass(AsciiDoctorConverter::class.java.getResourceAsStream("/lib/docops-extension.rb"))

        val options = Options.builder()
            .backend("xhtml")
            .safe(SafeMode.UNSAFE)
           .option("header_footer", true)
            .toFile(false)
            .attributes(buildAttrs())
            .build()
        options.setToDir(targetPath)
        val html = localAsciidoctor.convertFile(file, options).toString()
        val (htmlWithTokens, tokenMap) = replaceDocOpsBlocksWithTokens(html)
        var markdown = FlexmarkHtmlConverter.builder().build().convert(htmlWithTokens)

        // Replace tokens with Jira wiki image syntax
        tokenMap.forEach { (token, jiraImageMarkup) ->
            markdown = markdown.replace(token, jiraImageMarkup)
        }
        val wiki = converter.markdownToJira(markdown)
        targetWikiFile.writeText(wiki)
    }


    fun buildAttrs(): Attributes {
        val attrs = Attributes.builder()
            .attribute("panel-server", converterSettings.panelServer)
            .attribute("panel-webserver", converterSettings.panelWebserver)
            .attribute("local-debug", converterSettings.localDebug.toString())
            .noFooter(true)
            .build()

        return attrs
    }

    private fun replaceDocOpsBlocksWithTokens(html: String): Pair<String, Map<String, String>> {
        val doc = Jsoup.parseBodyFragment(html)
        val map = linkedMapOf<String, String>()
        var idx = 0

        // Matches HTML emitted by docops block wrappers that carry data-url
        val cards = doc.select(".docops-media-card[data-url]")

        cards.forEach { card ->
            val url = card.attr("data-url").trim()
            if (url.isNotEmpty()) {
                val token = "DOCOPS_IMG_TOKEN_${idx++}"
                map[token] = "!$url!"
                card.after(token)
                card.remove()
            }
        }

        return doc.body().html() to map
    }

}
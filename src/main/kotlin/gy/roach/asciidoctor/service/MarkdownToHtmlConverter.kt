package gy.roach.asciidoctor.service

import com.vladsch.flexmark.ext.toc.TocExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import gy.roach.asciidoctor.config.ConverterSettings
import gy.roach.asciidoctor.md.extension.DocOpsMacroExtension

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File

@Service
class MarkdownConverter(private val converterSettings: ConverterSettings) {
    
    private val logger = LoggerFactory.getLogger(MarkdownConverter::class.java)

    private val options = MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(DocOpsMacroExtension.create(), TocExtension.create()))
        set(DocOpsMacroExtension.WEBSERVER, converterSettings.panelServer)
        set(DocOpsMacroExtension.DEFAULT_SCALE, "1.0")
        set(DocOpsMacroExtension.DEFAULT_USE_DARK, "false")
    }
    fun convertMarkdownToHtml(sourceFile: File, outputDir: String, cssTheme: String = "md-light.css"): Boolean {
        return try {

            val parser = Parser.builder(options).build()
            val renderer = HtmlRenderer.builder(options).build()
            val markdownContent = sourceFile.readText()
            val document = parser.parse(markdownContent)
            val htmlBody = renderer.render(document)
            
            // Wrap in full HTML document with styling
            val fullHtml = buildHtmlDocument(
                title = sourceFile.nameWithoutExtension,
                body = htmlBody,
                cssTheme = cssTheme
            )
            
            // Write output file
            val outputFile = File(outputDir, "${sourceFile.nameWithoutExtension}.html")
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(fullHtml)
            
            logger.info("Converted Markdown file: ${sourceFile.name} -> ${outputFile.name}")
            true
        } catch (e: Exception) {
            logger.error("Failed to convert Markdown file: ${sourceFile.name}", e)
            false
        }
    }
    
    private fun buildHtmlDocument(title: String, body: String, cssTheme: String): String {
        val mdStyleSheet = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/$cssTheme")?.readAllBytes()?.decodeToString()
        //language=html
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title</title>
                <style>
                $mdStyleSheet
                </style>
            </head>
            <body>
                <article class="markdown-body">
                    $body
                </article>
            </body>
            </html>
        """.trimIndent()
    }
}

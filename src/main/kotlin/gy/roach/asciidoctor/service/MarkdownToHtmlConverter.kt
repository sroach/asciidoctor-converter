package gy.roach.asciidoctor.service

import com.vladsch.flexmark.ext.admonition.AdmonitionExtension
import com.vladsch.flexmark.ext.aside.AsideExtension
import com.vladsch.flexmark.ext.definition.DefinitionExtension
import com.vladsch.flexmark.ext.emoji.EmojiExtension
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughSubscriptExtension
import com.vladsch.flexmark.ext.ins.InsExtension
import com.vladsch.flexmark.ext.superscript.SuperscriptExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.ext.toc.SimTocExtension
import com.vladsch.flexmark.ext.toc.TocExtension
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import gy.roach.asciidoctor.config.ConverterSettings
import gy.roach.asciidoctor.md.extension.DocOpsMacroExtension
import gy.roach.asciidoctor.md.extension.GitHubAdmonitionExtension
import gy.roach.asciidoctor.md.extension.MermaidNodeRendererFactory

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File

@Service
class MarkdownConverter(private val converterSettings: ConverterSettings) {
    
    private val logger = LoggerFactory.getLogger(MarkdownConverter::class.java)

    private val options = MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(DocOpsMacroExtension.create(),
            GitHubAdmonitionExtension.create(),
            TocExtension.create(),
            AsideExtension.create(),
            DefinitionExtension.create(),
            EmojiExtension.create(),
            FootnoteExtension.create(),
            StrikethroughSubscriptExtension.create(),
            InsExtension.create(),
            SuperscriptExtension.create(),
            TablesExtension.create(),
            SimTocExtension.create(),
            AdmonitionExtension.create(),
            WikiLinkExtension.create()))
        set(DocOpsMacroExtension.WEBSERVER, converterSettings.panelServer)
        set(DocOpsMacroExtension.DEFAULT_SCALE, "1.0")
        set(DocOpsMacroExtension.DEFAULT_USE_DARK, "false")

    }
    fun convertMarkdownToHtml(sourceFile: File, outputDir: String, cssTheme: String = "github-markdown-css.css"): Boolean {
        return try {

            val parser = Parser.builder(options).build()
            val renderer = HtmlRenderer.builder(options).nodeRendererFactory(MermaidNodeRendererFactory()).build()
            val markdownContent = sourceFile.readText()
            val document = parser.parse(markdownContent)
            val htmlBody = renderer.render(document)
            
            // Wrap in full HTML document with styling
            val fullHtml = MermaidFlexmark.createFullHtmlWithMermaid(
                markdownContent = htmlBody,
                converterSettings = converterSettings,
                title= sourceFile.nameWithoutExtension,
                cssTheme = cssTheme)

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
    
}

object MermaidFlexmark {
    private fun convertMarkdownWithMermaid(markdown: String, converterSettings: ConverterSettings): String {
        val options = MutableDataSet().apply {
            set(Parser.EXTENSIONS, listOf(DocOpsMacroExtension.create(),
                GitHubAdmonitionExtension.create(),
                TocExtension.create(),
                AsideExtension.create(),
                DefinitionExtension.create(),
                EmojiExtension.create(),
                FootnoteExtension.create(),
                StrikethroughSubscriptExtension.create(),
                InsExtension.create(),
                SuperscriptExtension.create(),
                TablesExtension.create(),
                SimTocExtension.create(),
                AdmonitionExtension.create(),
                WikiLinkExtension.create()))
            set(DocOpsMacroExtension.WEBSERVER, converterSettings.panelServer)
            set(DocOpsMacroExtension.DEFAULT_SCALE, "1.0")
            set(DocOpsMacroExtension.DEFAULT_USE_DARK, "false")

        }

        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options)
            .nodeRendererFactory(MermaidNodeRendererFactory())
            .build()

        val document = parser.parse(markdown)
        return renderer.render(document)
    }

    fun createFullHtmlWithMermaid(markdownContent: String, converterSettings: ConverterSettings, title: String, cssTheme: String): String {
        val htmlBody = convertMarkdownWithMermaid(markdownContent, converterSettings = converterSettings)

        val mdStyleSheet = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/$cssTheme")?.readAllBytes()?.decodeToString()
        val modalOverlay = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/modal-overlay.css")?.readAllBytes()?.decodeToString()
        val admonitionCss = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/admonition.css")?.readAllBytes()?.decodeToString()
        val admonitionJs = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/admonition.js")?.readAllBytes()?.decodeToString()

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
                <style>
                $modalOverlay
                </style>
                <style>
                $admonitionCss
                </style>
                <style>
                    body {
                        box-sizing: border-box;
                        min-width: 200px;
                        max-width: 980px;
                        margin: 0 auto;
                        padding: 45px;
                    }

                    @media (prefers-color-scheme: dark) {
                        body {
                            background-color: #0d1117;
                        }
                    }
                </style>
                <script src="https://cdn.jsdelivr.net/npm/mermaid@11.12.2/dist/mermaid.min.js"></script>
                
            </head>
            <body>
                <article class="markdown-body">
                    $htmlBody
                </article>
                <!-- Modal Overlay -->
                <div class="modal-overlay" id="modalOverlay" onclick="closeModalOnBackdrop(event)">
                    <div class="modal-content" id="modalContent">
                        <button class="close-button" onclick="closeModal()" aria-label="Close modal">×</button>
                        <div id="modalSvgContainer"></div>
                    </div>
                </div>
                <script>
                    const modalOverlay = document.getElementById('modalOverlay');
                    const modalSvgContainer = document.getElementById('modalSvgContainer');

                    function openModal(container) {
                        // Clone the SVG from the clicked container
                        const svg = container.querySelector('svg');
                        const svgClone = svg.cloneNode(true);

                        // Clear previous content and add new SVG
                        modalSvgContainer.innerHTML = '';
                        modalSvgContainer.appendChild(svgClone);

                        // Show modal with animation
                        modalOverlay.classList.add('active');

                        // Prevent body scroll when modal is open
                        document.body.style.overflow = 'hidden';
                    }

                    function closeModal() {
                        modalOverlay.classList.remove('active');
                        document.body.style.overflow = '';
                    }

                    function closeModalOnBackdrop(event) {
                        // Only close if clicking the overlay itself, not the content
                        if (event.target === modalOverlay) {
                            closeModal();
                        }
                    }

                    // Close modal with Escape key
                    document.addEventListener('keydown', function(event) {
                        if (event.key === 'Escape' && modalOverlay.classList.contains('active')) {
                            closeModal();
                        }
                    });
                </script>
                <script>
                    mermaid.initialize({ startOnLoad: true });
                </script>
                <script>
                $admonitionJs
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}

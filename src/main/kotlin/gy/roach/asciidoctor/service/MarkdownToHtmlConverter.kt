package gy.roach.asciidoctor.service

import com.vladsch.flexmark.ext.admonition.AdmonitionExtension
import com.vladsch.flexmark.ext.aside.AsideExtension
import com.vladsch.flexmark.ext.definition.DefinitionExtension
import com.vladsch.flexmark.ext.emoji.EmojiExtension
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension
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
        set(DocOpsMacroExtension.WEBSERVER, converterSettings.panelWebserver)
        set(DocOpsMacroExtension.DEFAULT_SCALE, "1.0")
        set(DocOpsMacroExtension.DEFAULT_USE_DARK, false)

    }
    fun convertMarkdownToHtml(sourceFile: File, outputDir: String, cssTheme: String = "github-markdown-css.css"): Boolean {
        return try {
            val useDark = cssTheme.contains("dark") || cssTheme.contains("brutalist")
            options.set(DocOpsMacroExtension.DEFAULT_USE_DARK, useDark)
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
    private fun convertMarkdownWithMermaid(markdown: String, converterSettings: ConverterSettings, useDark: Boolean): String {
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
            set(DocOpsMacroExtension.DEFAULT_USE_DARK, useDark)

        }

        val parser = Parser.builder(options).build()
        val renderer = HtmlRenderer.builder(options)
            .nodeRendererFactory(MermaidNodeRendererFactory())
            .build()

        val document = parser.parse(markdown)
        return renderer.render(document)
    }

    fun createFullHtmlWithMermaid(markdownContent: String, converterSettings: ConverterSettings, title: String, cssTheme: String): String {
        val useDark = cssTheme.contains("dark") || cssTheme.contains("brutalist")
        val htmlBody = convertMarkdownWithMermaid(markdownContent, converterSettings = converterSettings, useDark = useDark)

        val mdStyleSheet = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/$cssTheme")?.readAllBytes()?.decodeToString()
        val modalOverlay = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/modal-overlay.css")?.readAllBytes()?.decodeToString()
        val admonitionCss = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/admonition.css")?.readAllBytes()?.decodeToString()
        val admonitionJs = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/admonition.js")?.readAllBytes()?.decodeToString()
        val svgData = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/svgdata.js")?.readAllBytes()?.decodeToString()

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
        .docops-data-panel {
            background: linear-gradient(145deg, #1a1a2e 0%, #0d0d12 100%);
            border-top: 1px solid var(--accent-primary);
            padding: 2rem;
            max-height: 400px;
            overflow-y: auto;
            position: relative;
            box-shadow: inset 0 10px 30px rgba(0,0,0,0.5);
        }

        .docops-data-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 1.5rem;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
            padding-bottom: 1rem;
        }
        .docops-data-actions {
            display: flex;
            gap: 12px;
        }
        .docops-action-btn {
            background: rgba(126, 87, 255, 0.1);
            border: 1px solid rgba(126, 87, 255, 0.3);
            color: var(--accent-secondary);
            font-family: 'Syne', sans-serif;
            font-size: 0.65rem;
            font-weight: 800;
            padding: 6px 14px;
            border-radius: 6px;
            cursor: pointer;
            transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .docops-action-btn:hover {
            background: var(--accent-primary);
            color: #fff;
            transform: translateY(-2px);
            box-shadow: 0 4px 15px rgba(126, 87, 255, 0.4);
            border-color: var(--accent-primary);
        }

        .docops-data-header span {
            font-family: 'Syne', sans-serif;
            font-weight: 800;
            text-transform: uppercase;
            letter-spacing: 0.1em;
            color: var(--accent-secondary);
            font-size: 0.9rem;
        }

        /* ui.md: Distinctive Table Design */
        .docops-data-table {
            width: 100%;
            border-collapse: separate;
            border-spacing: 0 4px;
            font-family: 'JetBrains Mono', monospace;
            font-size: 0.8rem;
        }

        .docops-data-table th {
            color: #fff;
            padding: 12px;
            text-align: left;
            border-bottom: 2px solid var(--accent-primary);
            font-weight: 500;
        }

        /* ui.md: Motion - Staggered Row Entry */
        .docops-data-table tr {
            opacity: 0;
            transform: translateX(-10px);
            animation: rowSlideIn 0.4s ease forwards;
        }

        @keyframes rowSlideIn {
            to { opacity: 1; transform: translateX(0); }
        }

        .docops-data-table td {
            padding: 12px;
            background: rgba(255, 255, 255, 0.03);
            color: #94a3b8;
            border-bottom: 1px solid rgba(255, 255, 255, 0.05);
        }

        .docops-data-table tr:hover td {
            background: rgba(126, 87, 255, 0.1);
            color: #fff;
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
                        <div class="modal-zoom-controls">
                            <button class="modal-zoom-btn" onclick="modalZoomOut()" title="Zoom Out">−</button>
                            <span class="modal-zoom-level" id="modalZoomLevel">100%</span>
                            <button class="modal-zoom-btn" onclick="modalZoomIn()" title="Zoom In">+</button>
                            <button class="modal-zoom-btn" onclick="modalZoomReset()" title="Reset Zoom">⟲</button>
                        </div>
                    </div>
                </div>
                <script>
                    const modalOverlay = document.getElementById('modalOverlay');
                    const modalSvgContainer = document.getElementById('modalSvgContainer');
                    
                    // Zoom state
                    let modalZoomLevel = 1;
                    const MODAL_ZOOM_MIN = 0.25;
                    const MODAL_ZOOM_MAX = 4;
                    const MODAL_ZOOM_STEP = 0.25;

                    function openModal(container) {
                        // Reset zoom level
                        modalZoomLevel = 1;
                        updateZoomDisplay();
                        
                        // Clone the SVG from the clicked container
                        const svg = container.querySelector('svg');
                        const svgClone = svg.cloneNode(true);
                        svgClone.style.transform = 'scale(1)';
                        svgClone.style.transition = 'transform 0.2s ease';

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
                        modalZoomLevel = 1;
                    }

                    function closeModalOnBackdrop(event) {
                        // Only close if clicking the overlay itself, not the content
                        if (event.target === modalOverlay) {
                            closeModal();
                        }
                    }
                    
                    function modalZoomIn() {
                        if (modalZoomLevel < MODAL_ZOOM_MAX) {
                            modalZoomLevel = Math.min(modalZoomLevel + MODAL_ZOOM_STEP, MODAL_ZOOM_MAX);
                            applyModalZoom();
                        }
                    }
                    
                    function modalZoomOut() {
                        if (modalZoomLevel > MODAL_ZOOM_MIN) {
                            modalZoomLevel = Math.max(modalZoomLevel - MODAL_ZOOM_STEP, MODAL_ZOOM_MIN);
                            applyModalZoom();
                        }
                    }
                    
                    function modalZoomReset() {
                        modalZoomLevel = 1;
                        applyModalZoom();
                    }
                    
                    function applyModalZoom() {
                        const svg = modalSvgContainer.querySelector('svg');
                        if (svg) {
                            svg.style.transform = `scale(${'$'}{modalZoomLevel})`;
                        }
                        updateZoomDisplay();
                    }
                    
                    function updateZoomDisplay() {
                        const display = document.getElementById('modalZoomLevel');
                        if (display) {
                            display.textContent = `${'$'}{Math.round(modalZoomLevel * 100)}%`;
                        }
                    }

                    // Close modal with Escape key
                    document.addEventListener('keydown', function(event) {
                        if (event.key === 'Escape' && modalOverlay.classList.contains('active')) {
                            closeModal();
                        }
                    });
                    const docopsCopy = {
                        url: (btn) => {
                                const url = btn.closest('.docops-media-card').getAttribute('data-url');
                                navigator.clipboard.writeText(url).then(() => {
                                    const originalText = btn.innerText;
                                    btn.innerText = 'COPIED!';
                                    setTimeout(() => btn.innerText = originalText, 2000);
                                });
                            },
                        svg: (btn) => {
                            const svg = btn.closest('.docops-media-card').querySelector('svg').outerHTML;
                            navigator.clipboard.writeText(svg).then(() => {
                                const originalText = btn.innerText;
                                btn.innerText = 'COPIED!';
                                setTimeout(() => btn.innerText = originalText, 2000);
                            });
                        },
                        png: (btn) => {
                            const svgElement = btn.closest('.docops-media-card').querySelector('svg');
                            const svgData = new XMLSerializer().serializeToString(svgElement);
                            const canvas = document.createElement('canvas');
                            const ctx = canvas.getContext('2d');
                            const img = new Image();
                            
                            img.onload = () => {
                                canvas.width = img.width;
                                canvas.height = img.height;
                                ctx.drawImage(img, 0, 0);
                                canvas.toBlob(blob => {
                                    const item = new ClipboardItem({ "image/png": blob });
                                    navigator.clipboard.write([item]);
                                    const originalText = btn.innerText;
                                    btn.innerText = 'COPIED!';
                                    setTimeout(() => btn.innerText = originalText, 2000);
                                });
                            };
                            img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svgData)));
                        }
                    };
                    
                </script>
                <script>
                    mermaid.initialize({ startOnLoad: true });
                </script>
                <script>
                $admonitionJs
                $svgData
                </script>
                
            </body>
            </html>
        """.trimIndent()
    }
}

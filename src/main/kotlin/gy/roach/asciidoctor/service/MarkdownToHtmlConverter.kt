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
        val themeAttr = if (useDark) "dark" else "light"
        val htmlBody = convertMarkdownWithMermaid(markdownContent, converterSettings = converterSettings, useDark = useDark)
        val bodyBack = if(useDark) {
            "#0d1117"
        } else {
            "#f5f5f5"
        }
        var styleRef = ""
        if(cssTheme.startsWith("http://", true) || cssTheme.startsWith("https://", true))
        {
            styleRef = """
                <link rel="stylesheet" href="$cssTheme">
            """.trimIndent()
        } else {
            val mdStyleSheet = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/$cssTheme")?.readAllBytes()?.decodeToString()
            styleRef = """
                <style>
                    $mdStyleSheet
                </style>
            """.trimIndent()
        }
        val baseMdStyleSheet = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/md.css")?.readAllBytes()?.decodeToString()
        val modalOverlay = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/modal-overlay.css")?.readAllBytes()?.decodeToString()
        val admonitionCss = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/admonition.css")?.readAllBytes()?.decodeToString()
        val admonitionJs = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/admonition.js")?.readAllBytes()?.decodeToString()
        val svgData = MarkdownConverter::class.java.classLoader.getResourceAsStream("themes/svgdata.js")?.readAllBytes()?.decodeToString()

        //language=html
        return """
            <!DOCTYPE html>
            <html lang="en" data-theme="$themeAttr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title</title>
                $styleRef
                <style>
                $baseMdStyleSheet
                </style>
                <style>
                $modalOverlay
                </style>
                <style>
                $admonitionCss
                </style>
                <style>
                          @media (prefers-color-scheme: dark) {
                              body {
                                  background-color: $bodyBack;
                              }
                          }
                          
                           /* Fix for DocOps visuals sizing - prevents them from being too large */
                          .svg-container svg {
                              width: auto !important;
                              height: auto !important;
                              max-width: 100% !important;
                              min-width: 0 !important;
                              margin: 0 auto;
                              display: block;
                          }
                          
                          /* Consistent Modal Styles matching AsciiDoc implementation */
                          .svg-modal-overlay {
                              display: none;
                              position: fixed;
                              top: 0;
                              left: 0;
                              width: 100vw;
                              height: 100vh;
                              background: rgba(15, 20, 25, 0.95);
                              backdrop-filter: blur(8px);
                              z-index: 2147483647;
                              align-items: center;
                              justify-content: center;
                              padding: 40px;
                              box-sizing: border-box;
                          }
                          .svg-modal-overlay.active {
                              display: flex;
                          }
                          .svg-modal-content {
                              width: 95vw;
                              max-width: 1400px;
                              height: 90vh;
                              background: var(--docops-card-bg, #1e293b);
                              border-radius: 20px;
                              padding: 48px 24px 24px 24px;
                              position: relative;
                              border: 1px solid rgba(255,255,255,0.1);
                              display: flex;
                              flex-direction: column;
                              overflow: hidden;
                              box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
                              animation: scaleInModal 0.3s cubic-bezier(0.16, 1, 0.3, 1);
                          }
                          .svg-modal-close {
                              position: absolute;
                              top: 12px;
                              right: 12px;
                              background: rgba(255,255,255,0.1);
                              color: #fff;
                              border: none;
                              width: 36px;
                              height: 36px;
                              border-radius: 50%;
                              font-size: 24px;
                              cursor: pointer;
                              display: flex;
                              align-items: center;
                              justify-content: center;
                              transition: all 0.2s ease;
                              z-index: 10;
                          }
                          .svg-modal-close:hover {
                              background: rgba(255,255,255,0.2);
                              transform: rotate(90deg);
                          }
                          .svg-modal-body {
                              flex: 1;
                              display: flex;
                              align-items: center;
                              justify-content: center;
                              width: 100%;
                              height: 100%;
                              overflow: auto;
                              padding: 10px;
                          }
                          .svg-modal-body svg {
                              max-width: 100%;
                              max-height: 100%;
                              width: auto;
                              height: auto;
                              object-fit: contain;
                          }
                          @keyframes scaleInModal {
                              from { opacity: 0; transform: scale(0.9); }
                              to { opacity: 1; transform: scale(1); }
                          }
                          
                          /* CSV Modal Styles */
                          #globalCsvModal .csv-table {
                              width: 100%;
                              border-collapse: collapse;
                              color: #e2e8f0;
                              font-family: 'JetBrains Mono', monospace;
                              font-size: 13px;
                              background: transparent;
                          }
                          #globalCsvModal .csv-table th {
                              text-align: left;
                              padding: 12px;
                              border-bottom: 1px solid rgba(255,255,255,0.1);
                              background: rgba(255,255,255,0.1);
                              color: #fff;
                              position: sticky;
                              top: 0;
                          }
                          #globalCsvModal .csv-table td {
                              padding: 12px;
                              border-bottom: 1px solid rgba(255,255,255,0.05);
                              color: #cbd5e1;
                          }
                          #globalCsvModal .csv-table tr {
                              background: transparent;
                          }
                          #globalCsvModal .csv-table tr:hover {
                              background: rgba(255,255,255,0.05);
                          }
                          
                          /* Sliding Controls Styles (Consistent with Asciidoctor) */
                          .svg-with-controls {
                              position: relative;
                              display: inline-block;
                              border-radius: 8px;
                              overflow: hidden;
                          }
                          .svg-bottom-controls {
                              position: absolute;
                              bottom: 12px;
                              left: 50%;
                              transform: translateX(-50%) translateY(150%);
                              display: flex;
                              gap: 6px;
                              background: rgba(15, 20, 30, 0.85);
                              padding: 6px 8px;
                              border-radius: 6px;
                              border: 1px solid rgba(255,255,255,0.1);
                              opacity: 0;
                              transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
                              z-index: 100;
                              backdrop-filter: blur(8px);
                              box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
                          }
                          .svg-with-controls:hover .svg-bottom-controls {
                              opacity: 1;
                              transform: translateX(-50%) translateY(0);
                          }
                          .svg-control-btn {
                              background: transparent;
                              border: 1px solid rgba(255,255,255,0.15);
                              color: #94a3b8;
                              font-family: 'JetBrains Mono', monospace;
                              font-size: 10px;
                              font-weight: 600;
                              padding: 4px 10px;
                              border-radius: 4px;
                              cursor: pointer;
                              transition: all 0.2s;
                              text-transform: uppercase;
                              letter-spacing: 0.05em;
                          }
                          .svg-control-btn:hover {
                              background: rgba(255,255,255,0.1);
                              color: #fff;
                              border-color: rgba(255,255,255,0.3);
                              transform: translateY(-1px);
                          }
                    </style>
                <script src="https://cdn.jsdelivr.net/npm/mermaid@11.12.2/dist/mermaid.min.js"></script>
                
            </head>
            <body>
                    <article class="markdown-body">
                        $htmlBody
                    </article>
                    
                    <!-- Global Shared Modal for Markdown View -->
                    <div class="svg-modal-overlay" id="globalSvgModal" onclick="if(event.target === this) closeGlobalModal()">
                        <div class="svg-modal-content">
                            <button class="svg-modal-close" onclick="closeGlobalModal()">×</button>
                            <div id="globalModalBody" class="svg-modal-body"></div>
                        </div>
                    </div>

                    <!-- Global Shared Modal for Data View -->
                        <div class="svg-modal-overlay" id="globalCsvModal" onclick="if(event.target === this) closeGlobalCsvModal()">
                            <div class="svg-modal-content">
                                <button class="svg-modal-close" onclick="closeGlobalCsvModal()">×</button>
                                <div class="svg-modal-header" style="margin-bottom: 20px; border-bottom: 1px solid rgba(255,255,255,0.1); padding-bottom: 10px;">
                                    <h3 style="margin:0; color:white; font-family:'JetBrains Mono', monospace;">Data Source</h3>
                                </div>
                                <div id="globalCsvModalBody" class="svg-modal-body" style="display: block; overflow: auto;"></div>
                            </div>
                        </div>

                        <!-- Global Shared Modal for Source View -->
                        <div class="svg-modal-overlay" id="globalSourceModal" onclick="if(event.target === this) closeGlobalSourceModal()">
                            <div class="svg-modal-content">
                                <button class="svg-modal-close" onclick="closeGlobalSourceModal()">×</button>
                                <div class="svg-modal-header" style="margin-bottom: 20px; border-bottom: 1px solid rgba(255,255,255,0.1); padding-bottom: 10px;">
                                    <h3 style="margin:0; color:white; font-family:'JetBrains Mono', monospace;">Original Source</h3>
                                </div>
                                <div id="globalSourceModalBody" class="svg-modal-body" style="display: block; overflow: auto;"></div>
                            </div>
                        </div>

                    <script>
                            // Unified Modal Logic for Markdown
                            function openModal(container) {
                                const modal = document.getElementById('globalSvgModal');
                                const targetContainer = document.getElementById('globalModalBody');
                            
                                // Find SVG in the container passed from the button
                                const sourceSvg = container.querySelector ? container.querySelector('svg') : container; 
                            
                                if (sourceSvg && targetContainer) {
                                    targetContainer.innerHTML = '';
                                    const clone = sourceSvg.cloneNode(true);
                                
                                    // Reset dimensions to allow CSS to control scaling
                                    clone.removeAttribute('width');
                                    clone.removeAttribute('height');
                                    clone.style.width = '100%';
                                    clone.style.height = '100%';
                                
                                    targetContainer.appendChild(clone);
                                    modal.classList.add('active');
                                    document.body.style.overflow = 'hidden';
                                }
                            }

                            function closeGlobalModal() {
                                const modal = document.getElementById('globalSvgModal');
                                if (modal) {
                                    modal.classList.remove('active');
                                    document.body.style.overflow = '';
                                }
                            }
                        
                            function closeGlobalCsvModal() {
                                const modal = document.getElementById('globalCsvModal');
                                if (modal) {
                                    modal.classList.remove('active');
                                    document.body.style.overflow = '';
                                }
                            }
                            
                            function closeGlobalSourceModal() {
                                const modal = document.getElementById('globalSourceModal');
                                if (modal) {
                                    modal.classList.remove('active');
                                    document.body.style.overflow = '';
                                }
                            }

                            // Close modals with Escape key
                            document.addEventListener('keydown', function(event) {
                                if (event.key === 'Escape') {
                                    closeGlobalModal();
                                    closeGlobalCsvModal();
                                    closeGlobalSourceModal();
                                }
                            });
                        
                            const docopsData = {
                                toggle: function(btn) {
                                    const container = btn.closest('.docops-media-card');
                                    const svg = container.querySelector('.svg-container svg') || container.querySelector('svg');
                                
                                    if (!svg) return;
                                
                                    let csvData = null;
                                
                                    // Method 1: Standard metadata with type
                                    let csvMetadata = svg.querySelector('metadata[type="text/csv"]');
                                    if (csvMetadata) {
                                        try {
                                            csvData = JSON.parse(csvMetadata.textContent.trim());
                                        } catch(e) { console.error("Error parsing CSV JSON", e); }
                                    }

                                    // Method 2: Custom csv-data element
                                    if (!csvData) {
                                        csvMetadata = svg.querySelector('metadata csv-data');
                                        if (csvMetadata) {
                                            try {
                                                csvData = JSON.parse(csvMetadata.textContent.trim());
                                            } catch(e) {}
                                        }
                                    }

                                    // Method 3: Data attribute
                                    if (!csvData) {
                                        const csvDataAttr = svg.getAttribute('data-csv');
                                        if (csvDataAttr) {
                                            try {
                                                csvData = JSON.parse(decodeURIComponent(csvDataAttr));
                                            } catch(e) {}
                                        }
                                    }
                                
                                    if (csvData) {
                                        const modal = document.getElementById('globalCsvModal');
                                        const body = document.getElementById('globalCsvModalBody');
                                    
                                        let html = '<table class="csv-table">';
                                         if (csvData.headers) {
                                            html += '<thead><tr>';
                                            csvData.headers.forEach(h => html += '<th>' + h + '</th>');
                                            html += '</tr></thead>';
                                        }
                                        if (csvData.rows) {
                                            html += '<tbody>';
                                            csvData.rows.forEach(row => {
                                                html += '<tr>';
                                                row.forEach(cell => html += '<td>' + cell + '</td>');
                                                html += '</tr>';
                                            });
                                            html += '</tbody>';
                                        }
                                        html += '</table>';
                                    
                                        body.innerHTML = html;
                                        modal.classList.add('active');
                                        document.body.style.overflow = 'hidden';
                                    } else {
                                        console.log("No CSV data found in SVG");
                                    }
                                }
                            };
                            
                            const docopsSource = {
                                toggle: function(btn) {
                                    const container = btn.closest('.docops-media-card');
                                    const originalContent = container.getAttribute('data-original-content');
                                    const kind = container.getAttribute('data-kind') || 'source';
                                    
                                    if (!originalContent || !originalContent.trim()) {
                                        console.log("No source content available");
                                        return;
                                    }
                                    
                                    // Decode HTML entities
                                    const decoded = originalContent
                                        .replace(/&quot;/g, '"')
                                        .replace(/&amp;/g, '&')
                                        .replace(/&lt;/g, '<')
                                        .replace(/&gt;/g, '>');
                                    
                                    const modal = document.getElementById('globalSourceModal');
                                    const body = document.getElementById('globalSourceModalBody');
                                    
                                    const escapedHtml = decoded
                                        .replace(/&/g, '&amp;')
                                        .replace(/</g, '&lt;')
                                        .replace(/>/g, '&gt;');
                                    
                                    body.innerHTML = `
                                        <div style="display: flex; justify-content: flex-end; margin-bottom: 1rem; padding: 0 1rem;">
                                            <button class="docops-action-btn" onclick="docopsSource.copyFromModal()">
                                                <span style="margin-right: 6px;">📋</span> COPY
                                            </button>
                                        </div>
                                        <pre style="
                                            background: rgba(0, 0, 0, 0.4);
                                            border: 1px solid rgba(255, 255, 255, 0.1);
                                            border-radius: 12px;
                                            padding: 2rem;
                                            overflow: auto;
                                            margin: 0 1rem 1rem 1rem;
                                            box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.3);
                                        "><code style="
                                            font-family: 'JetBrains Mono', 'Fira Code', monospace;
                                            font-size: 0.85rem;
                                            line-height: 1.7;
                                            color: #e2e8f0;
                                            display: block;
                                            white-space: pre-wrap;
                                            word-wrap: break-word;
                                        ">${'$'}{escapedHtml}</code></pre>
                                    `;
                                    
                                    // Store decoded content for copying
                                    body.dataset.sourceContent = decoded;
                                    
                                    modal.classList.add('active');
                                    document.body.style.overflow = 'hidden';
                                },
                                
                                copyFromModal: function() {
                                    const body = document.getElementById('globalSourceModalBody');
                                    const content = body.dataset.sourceContent;
                                    
                                    if (content) {
                                        navigator.clipboard.writeText(content).then(() => {
                                            const btn = document.querySelector('#globalSourceModal .docops-action-btn');
                                            if (btn) {
                                                const originalHtml = btn.innerHTML;
                                                btn.innerHTML = '<span style="margin-right: 6px;">✓</span> COPIED!';
                                                btn.style.background = 'rgba(34, 197, 94, 0.8)';
                                                setTimeout(() => {
                                                    btn.innerHTML = originalHtml;
                                                    btn.style.background = '';
                                                }, 2000);
                                            }
                                        }).catch(err => {
                                            console.error('Copy failed:', err);
                                        });
                                    }
                                }
                            };
                        
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

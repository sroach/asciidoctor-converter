package gy.roach.asciidoctor.md.extension

import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.html.HtmlWriter
import com.vladsch.flexmark.html.renderer.NodeRenderer
import com.vladsch.flexmark.html.renderer.NodeRendererContext
import com.vladsch.flexmark.html.renderer.NodeRendererFactory
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler
import com.vladsch.flexmark.util.data.DataHolder
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader
import java.io.ByteArrayOutputStream

class PlantumlNodeRenderer(private val useDark: Boolean = false) : NodeRenderer {
    override fun getNodeRenderingHandlers(): Set<NodeRenderingHandler<*>> {
        return setOf(
            NodeRenderingHandler(FencedCodeBlock::class.java, ::render)
        )
    }

    private fun loadTheme(): String {
        val themePath = if (useDark) "/themes/plantuml-dark.puml" else "/themes/plantuml-light.puml"
        return javaClass.getResourceAsStream(themePath)?.bufferedReader()?.use { it.readText() } ?: ""
    }

    private fun sanitizePlantUml(raw: String): String {
        val cleaned = raw
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")

        val lines = cleaned.lines().toMutableList()
        if (lines.firstOrNull { it.isNotBlank() }?.trim() != "@startuml") {
            lines.add(0, "@startuml")
        }
        if (lines.lastOrNull { it.isNotBlank() }?.trim() != "@enduml") {
            lines.add("@enduml")
        }
        // Insert theme after @startuml for custom iOS look-and-feel
        val theme = loadTheme()
        if (theme.isNotBlank()) {
            val startIdx = lines.indexOfFirst { it.trim() == "@startuml" }
            if (startIdx >= 0) {
                lines.add(startIdx + 1, theme)
            }
        }
        return lines.joinToString("\n")
    }
    private fun escapeHtmlAttr(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&#39;")
    private fun render(
        node: FencedCodeBlock,
        context: NodeRendererContext,
        html: HtmlWriter
    ) {
        val language = node.info.toString().trim().lowercase()

        if (language == "plantuml" || language == "puml") {
            val normalizedPlantuml = sanitizePlantUml(node.contentChars.toString())

            val svgContent = try {
                val reader = SourceStringReader(normalizedPlantuml)
                val output = ByteArrayOutputStream()
                reader.outputImage(output, FileFormatOption(FileFormat.SVG))
                //reader.generateImage(output, FileFormatOption(FileFormat.SVG))
                output.toString(Charsets.UTF_8)
            } catch (e: Exception) {
                "<div style='color:red'>PlantUML Error: ${escapeHtmlAttr(e.message ?: "unknown error")}</div>"
            }

            val embeddedSvg = svgContent
                .replace(Regex("""<\?xml[^>]*\?>\s*"""), "")
                .replace(Regex("""<\?plantuml[^>]*\?>\s*"""), "")

            val safeOriginal = escapeHtmlAttr(normalizedPlantuml)

            val fullContent = buildString {
                appendLine("<div class=\"docops-media-card\">")
                appendLine("  <script type=\"text/plain\" class=\"docops-source-raw\">")
                appendLine(escapeHtmlAttr(normalizedPlantuml))
                appendLine("  </script>")
                appendLine("  <div class=\"plantuml svg-container\" onclick=\"openModal(this);\">")
                appendLine(embeddedSvg)
                appendLine("  </div>")
                appendLine("  <div class=\"docops-control-bar\">")
                appendLine("    <button class=\"docops-btn\" onclick=\"openModal(this.closest('.docops-media-card').querySelector('.svg-container'))\" title=\"View Large\">VIEW</button>")
                appendLine("    <button class=\"docops-btn\" onclick=\"docopsCopy.svg(this)\" title=\"Copy SVG Source\">SVG</button>")
                appendLine("    <button class=\"docops-btn\" onclick=\"docopsCopy.png(this)\" title=\"Copy as PNG\">PNG</button>")
                appendLine("    <button class=\"docops-btn\" onclick=\"docopsSource.toggle(this)\" title=\"View Original Source\">SOURCE</button>")
                appendLine("  </div>")
                appendLine("  <div class=\"docops-source-panel\" style=\"display: none;\">")
                appendLine("    <div class=\"docops-source-header\">")
                appendLine("      <span>Original Source</span>")
                appendLine("      <button class=\"docops-btn-close\" onclick=\"this.closest('.docops-source-panel').style.display='none'\">×</button>")
                appendLine("    </div>")
                appendLine("    <div class=\"docops-source-container\"></div>")
                appendLine("  </div>")
                appendLine("</div>")
            }

            html.line()
            html.raw(fullContent.trimEnd())
            html.line()
        } else {
            context.delegateRender()
        }
    }

}

class PlantumlNodeRendererFactory(private val useDark: Boolean = false) : NodeRendererFactory {
    override fun apply(options: DataHolder): NodeRenderer {
        return PlantumlNodeRenderer(useDark)
    }
}

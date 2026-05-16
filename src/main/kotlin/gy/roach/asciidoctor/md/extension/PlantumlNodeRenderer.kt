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

class PlantumlNodeRenderer : NodeRenderer {
    override fun getNodeRenderingHandlers(): Set<NodeRenderingHandler<*>> {
        return setOf(
            NodeRenderingHandler(FencedCodeBlock::class.java, ::render)
        )
    }

    private fun render(
        node: FencedCodeBlock,
        context: NodeRendererContext,
        html: HtmlWriter
    ) {
        val language = node.info.toString().lowercase()

        if (language == "plantuml" || language == "puml") {
            val plantumlContent = node.contentChars.toString().trim()

            // Render PlantUML to SVG
            val svgContent = try {
                val reader = SourceStringReader(plantumlContent)
                val output = ByteArrayOutputStream()
                reader.generateImage(output, FileFormatOption(FileFormat.SVG))
                output.toString(Charsets.UTF_8)
            } catch (e: Exception) {
                "<div style='color:red'>PlantUML Error: ${e.message}</div>"
            }

            // Wrap in docops-media-card similar to Mermaid for consistent UI (modal, copy, source)
            val fullContent = buildString {
                appendLine("<div class=\"docops-media-card\" data-original-content=\"${plantumlContent.replace("\"", "&quot;")}\">")
                appendLine("  <div class='plantuml svg-container' onclick='openModal(this);'>")
                appendLine(svgContent)
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
            html.rawPre(fullContent.trimEnd())
            html.line()
        } else {
            context.delegateRender()
        }
    }
}

class PlantumlNodeRendererFactory : NodeRendererFactory {
    override fun apply(options: DataHolder): NodeRenderer {
        return PlantumlNodeRenderer()
    }
}

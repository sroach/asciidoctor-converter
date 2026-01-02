package gy.roach.asciidoctor.md.extension

import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.html.HtmlWriter
import com.vladsch.flexmark.html.renderer.NodeRenderer
import com.vladsch.flexmark.html.renderer.NodeRendererContext
import com.vladsch.flexmark.html.renderer.NodeRendererFactory
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler
import com.vladsch.flexmark.util.data.DataHolder


class MermaidNodeRenderer : NodeRenderer {
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
        val language = node.info.toString()

        if (language == "mermaid") {
            // Remove empty lines that cause paragraph wrapping
            val mermaidContent = node.contentChars.toString()
                .lines()
                .filter { it.isNotBlank() }
                .joinToString("\n")

            // Disable HTML processing temporarily and output everything as one raw block
            val fullContent = buildString {
                appendLine("<div class=\"docops-media-card\">")
                appendLine("  <div class='mermaid svg-container' onclick='openModal(this);'>")
                appendLine(mermaidContent)
                appendLine("  </div>")
                appendLine("  <div class=\"docops-control-bar\">")
                appendLine("    <button class=\"docops-btn\" onclick=\"openModal(this.closest('.docops-media-card').querySelector('.svg-container'))\" title=\"View Large\">VIEW</button>")
                appendLine("    <button class=\"docops-btn\" onclick=\"docopsCopy.svg(this)\" title=\"Copy SVG Source\">SVG</button>")
                appendLine("    <button class=\"docops-btn\" onclick=\"docopsCopy.png(this)\" title=\"Copy as PNG\">PNG</button>")
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

class MermaidNodeRendererFactory : NodeRendererFactory {
    override fun apply(options: DataHolder): NodeRenderer {
        return MermaidNodeRenderer()
    }
}
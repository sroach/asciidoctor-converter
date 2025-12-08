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
            /*html.line()
            html.attr("class", "mermaid")
            html.withAttr().tag("div")
            html.text(node.contentChars.toString())
            html.tag("/div")
            html.line()*/
            //language=html
            val content = """
                <div class="example-item">
                    <div class='mermaid svg-container' onclick='openModal(this);'>
                        ${node.contentChars.toString()}
                    </div>
                </div>
            """.trimIndent()
            html.raw(content)
        } else {
            // Default rendering for other code blocks
            context.delegateRender()
        }
    }
}

class MermaidNodeRendererFactory : NodeRendererFactory {
    override fun apply(options: DataHolder): NodeRenderer {
        return MermaidNodeRenderer()
    }
}
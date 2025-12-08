package gy.roach.asciidoctor.md.extension

import com.vladsch.flexmark.ast.BlockQuote
import com.vladsch.flexmark.ast.Paragraph
import com.vladsch.flexmark.ast.Text
import com.vladsch.flexmark.html.HtmlWriter
import com.vladsch.flexmark.html.renderer.NodeRenderer
import com.vladsch.flexmark.html.renderer.NodeRendererContext
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.ast.Node
import com.vladsch.flexmark.util.data.MutableDataHolder
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html.renderer.NodeRendererFactory
import com.vladsch.flexmark.util.data.DataHolder
import com.vladsch.flexmark.util.sequence.BasedSequence

class GitHubAdmonitionExtension private constructor() : Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension {

    override fun parserOptions(options: MutableDataHolder) {}

    override fun extend(parserBuilder: Parser.Builder) {}

    override fun rendererOptions(options: MutableDataHolder) {}

    override fun extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String) {
        htmlRendererBuilder.nodeRendererFactory(GitHubAdmonitionNodeRendererFactory())
    }

    companion object {
        fun create(): GitHubAdmonitionExtension = GitHubAdmonitionExtension()

        val ADMONITION_TYPES = mapOf(
            "NOTE" to "note",
            "TIP" to "tip",
            "IMPORTANT" to "important",
            "WARNING" to "warning",
            "CAUTION" to "caution"
        )
    }

    class GitHubAdmonitionNodeRendererFactory : NodeRendererFactory {
        override fun apply(options: DataHolder): NodeRenderer {
            return GitHubAdmonitionNodeRenderer()
        }
    }

    class GitHubAdmonitionNodeRenderer : NodeRenderer {
        private var context: NodeRendererContext? = null
        private var html: HtmlWriter? = null

        override fun getNodeRenderingHandlers(): Set<NodeRenderingHandler<*>> {
            return setOf(
                NodeRenderingHandler(
                    BlockQuote::class.java,
                    { node: BlockQuote, ctx: NodeRendererContext, writer: HtmlWriter ->
                        this.context = ctx
                        this.html = writer
                        renderBlockQuote(node)
                    }
                )
            )
        }

        private fun renderBlockQuote(node: BlockQuote) {
            val firstChild = node.firstChild

            // Check if this is a GitHub-style admonition
            if (firstChild is Paragraph) {
                // Get the raw source text of the paragraph
                val rawText = firstChild.chars.toString()


                // Also check all child nodes
                var childNode = firstChild.firstChild
                val allText = StringBuilder()
                while (childNode != null) {
                    println("DEBUG: Child node type: ${childNode.javaClass.simpleName}, content: '${childNode.chars}'")
                    allText.append(childNode.chars.toString())
                    childNode = childNode.next
                }


                // Try to match on raw text first
                val textToMatch = if (rawText.isNotBlank()) rawText else allText.toString()

                // Match [!TYPE] pattern at the start
                val admonitionMatch = Regex("^\\[!([A-Z]+)]\\s*(.*)").find(textToMatch)

                if (admonitionMatch != null) {

                    val type = admonitionMatch.groupValues[1]
                    val remainingText = admonitionMatch.groupValues[2]
                    val admonitionClass = ADMONITION_TYPES[type] ?: type.lowercase()

                    renderGitHubAdmonition(node, admonitionClass, type, remainingText, firstChild)
                    return
                }
            }

            // Not an admonition, render as normal blockquote
            context?.delegateRender()
        }

        private fun renderGitHubAdmonition(
            node: BlockQuote,
            cssClass: String,
            title: String,
            remainingText: String,
            firstParagraph: Paragraph
        ) {

            val html = this.html ?: return
            val context = this.context ?: return

            html.line()
            html.attr("class", "adm-block adm-$cssClass")
            html.withAttr().tag("div")
            html.line()

            // Render heading
            html.attr("class", "adm-heading")
            html.withAttr().tag("div")
            html.raw("""<span class="adm-icon">${getIconSvg(cssClass)}</span>""")
            html.tag("span")
            html.text(title.lowercase().replaceFirstChar { it.uppercase() })
            html.closeTag("span")
            html.closeTag("div")
            html.line()

            // Render body
            html.attr("class", "adm-body")
            html.withAttr().tag("div")

            // Render remaining text from first line if any
            if (remainingText.isNotBlank()) {
                html.tag("p")
                html.text(remainingText)
                html.closeTag("p")
                html.line()
            }

            // Render remaining paragraphs
            var currentNode = firstParagraph.next
            while (currentNode != null) {
                context.render(currentNode)
                currentNode = currentNode.next
            }

            html.closeTag("div")
            html.line()
            html.closeTag("div")
            html.line()
        }

        private fun getIconSvg(type: String): String {
            return when (type) {
                "note" -> """<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>"""
                "tip" -> """<svg viewBox="0 0 24 24" fill="currentColor"><path d="M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z"/></svg>"""
                "warning" -> """<svg viewBox="0 0 24 24" fill="currentColor"><path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/></svg>"""
                "important" -> """<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>"""
                "caution" -> """<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2L1 21h22L12 2zm0 3.5L19.5 19h-15L12 5.5zM11 16v2h2v-2h-2zm0-6v4h2v-4h-2z"/></svg>"""
                else -> """<svg viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="10"/></svg>"""
            }
        }
    }
}
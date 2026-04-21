package gy.roach.asciidoctor.jira

data class ConversionResult(
    val output: String,
    val warnings: List<String>
)

class ConversionContext {
    val warnings: MutableList<String> = mutableListOf()
    fun warn(message: String) {
        warnings += message
    }
}

interface AsciiDocParser {
    fun parse(asciidoc: String): DocumentNode
}

interface Node {
    val children: List<Node>
        get() = emptyList()
}

data class DocumentNode(
    override val children: List<Node> = emptyList()
) : Node

data class HeadingNode(val level: Int, val text: String) : Node
data class ParagraphNode(val text: String) : Node
data class CodeBlockNode(val language: String?, val code: String) : Node

interface NodeRenderer<T : Node> {
    fun render(node: T, out: StringBuilder, engine: JiraRenderer, context: ConversionContext)
}

class JiraRenderer(
    private val renderers: Map<Class<out Node>, NodeRenderer<out Node>>,
    private val unsupported: NodeRenderer<Node>
) {
    fun render(node: Node, context: ConversionContext): String {
        val out = StringBuilder()
        renderNode(node, out, context)
        return out.toString()
    }

    @Suppress("UNCHECKED_CAST")
    fun renderNode(node: Node, out: StringBuilder, context: ConversionContext) {
        val renderer = renderers[node.javaClass] as? NodeRenderer<Node> ?: unsupported
        renderer.render(node, out, this, context)
    }

    fun renderChildren(parent: Node, out: StringBuilder, context: ConversionContext) {
        parent.children.forEach { child -> renderNode(child, out, context) }
    }
}

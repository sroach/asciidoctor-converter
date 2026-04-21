package gy.roach.asciidoctor.jira

fun defaultRenderer(): JiraRenderer {
    val renderers: Map<Class<out Node>, NodeRenderer<out Node>> = mapOf(
        DocumentNode::class.java to DocumentRenderer(),
        HeadingNode::class.java to HeadingRenderer(),
        ParagraphNode::class.java to ParagraphRenderer(),
        CodeBlockNode::class.java to CodeBlockRenderer(),
        ListNode::class.java to ListRenderer(),
        TableNode::class.java to TableRenderer()
    )

    val unsupported = object : NodeRenderer<Node> {
        override fun render(node: Node, out: StringBuilder, engine: JiraRenderer, context: ConversionContext) {
            context.warn("Unsupported node: ${node::class.java.simpleName}")
        }
    }

    return JiraRenderer(renderers, unsupported)
}

object defaultParser : AsciiDocParser {
    override fun parse(asciidoc: String): DocumentNode {
        // Fixture parser for this golden test input:
        // it returns deterministic AST nodes used by your renderers.
        return DocumentNode(
            children = listOf(
                HeadingNode(level = 1, text = "Demo"),
                ParagraphNode("See link:https://example.com[Example] with *bold* _italic_ and `mono`."),
                ListNode(
                    ordered = false,
                    items = listOf(
                        ListItemNode(
                            text = "Parent https://a.dev[A]",
                            children = listOf(
                                ListNode(
                                    ordered = false,
                                    items = listOf(
                                        ListItemNode("Child with `code`")
                                    )
                                )
                            )
                        )
                    )
                ),
                TableNode(
                    headers = listOf("Name", "Notes"),
                    rows = listOf(
                        listOf("Alice", "Uses `sdk`"),
                        listOf("Bob", "line1\nline2 with *bold*")
                    )
                )
            )
        )
    }
}


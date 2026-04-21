package gy.roach.asciidoctor.jira

class ListRenderer : NodeRenderer<ListNode> {
    override fun render(
        node: ListNode,
        out: StringBuilder,
        engine: JiraRenderer,
        context: ConversionContext
    ) {
        renderList(node, out, depth = 1)
        out.append("\n")
    }

    private fun renderList(node: ListNode, out: StringBuilder, depth: Int) {
        val markerUnit = if (node.ordered) "#" else "*"

        node.items.forEach { item ->
            val prefix = markerUnit.repeat(depth)
            out.append(prefix).append(" ").append(item.text.trim().toJiraInline()).append("\n")

            item.children.forEach { nested ->
                renderList(nested, out, depth + 1)
            }
        }
    }
}

class TableRenderer : NodeRenderer<TableNode> {
    override fun render(
        node: TableNode,
        out: StringBuilder,
        engine: JiraRenderer,
        context: ConversionContext
    ) {
        if (node.headers.isNotEmpty()) {
            out.append("||")
            out.append(node.headers.joinToString("||") { it.toJiraTableCell() })
            out.append("||\n")
        }

        node.rows.forEach { row ->
            out.append("|")
            out.append(row.joinToString("|") { it.toJiraTableCell() })
            out.append("|\n")
        }

        out.append("\n")
    }

    private fun String.toJiraTableCell(): String {
        return this
            .replace("\r\n", " ")
            .replace("\n", " ")
            .trim()
            .toJiraInline()
    }
}
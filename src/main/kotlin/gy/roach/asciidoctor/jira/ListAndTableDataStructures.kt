package gy.roach.asciidoctor.jira

data class ListNode(
    val ordered: Boolean,
    val items: List<ListItemNode>
) : Node

data class ListItemNode(
    val text: String,
    override val children: List<ListNode> = emptyList()
) : Node

data class TableNode(
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList()
) : Node

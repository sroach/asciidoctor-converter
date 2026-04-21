package gy.roach.asciidoctor.jira

import kotlin.math.max
import kotlin.math.min

class DocumentRenderer : NodeRenderer<DocumentNode> {
    override fun render(
        node: DocumentNode,
        out: StringBuilder,
        engine: JiraRenderer,
        context: ConversionContext
    ) {
        engine.renderChildren(node, out, context)
    }
}



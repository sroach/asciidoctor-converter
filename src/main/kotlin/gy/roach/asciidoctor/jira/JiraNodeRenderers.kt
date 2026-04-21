package gy.roach.asciidoctor.jira

import kotlin.math.max
import kotlin.math.min


internal fun String.toJiraInline(): String {
    var out = this

    // AsciiDoc links: link:https://example.com[Label] or https://example.com[Label]
    out = out.replace(
        Regex("""(?:link:)?(https?://[^\s\[]+)\[([^\]]+)]""")
    ) { m ->
        val url = m.groupValues[1]
        val label = m.groupValues[2]
        "[$label|$url]"
    }

    // AsciiDoc monospace: `code` -> {{code}}
    out = out.replace(Regex("""`([^`]+)`"""), "{{$1}}")

    // AsciiDoc bold: *text* -> *text* (same in Jira, normalize safely)
    out = out.replace(Regex("""(?<!\S)\*([^\n*]+)\*(?!\S)"""), "*$1*")

    // AsciiDoc italic: _text_ -> _text_ (same in Jira, normalize safely)
    out = out.replace(Regex("""(?<!\S)_([^\n_]+)_(?!\S)"""), "_$1_")

    return out
}

class HeadingRenderer : NodeRenderer<HeadingNode> {
    override fun render(
        node: HeadingNode,
        out: StringBuilder,
        engine: JiraRenderer,
        context: ConversionContext
    ) {
        val level = min(6, max(1, node.level))
        out.append("h").append(level).append(". ").append(node.text.trim()).append("\n\n")
    }
}



class ParagraphRenderer : NodeRenderer<ParagraphNode> {
    override fun render(
        node: ParagraphNode,
        out: StringBuilder,
        engine: JiraRenderer,
        context: ConversionContext
    ) {
        val text = node.text.trim().toJiraInline()
        if (text.isNotEmpty()) {
            out.append(text).append("\n\n")
        }
    }
}

class CodeBlockRenderer : NodeRenderer<CodeBlockNode> {
    override fun render(
        node: CodeBlockNode,
        out: StringBuilder,
        engine: JiraRenderer,
        context: ConversionContext
    ) {
        val language = node.language?.trim().orEmpty()
        if (language.isNotEmpty()) {
            out.append("{code:language=").append(language).append("}\n")
        } else {
            out.append("{code}\n")
        }

        out.append(node.code.trimEnd()).append("\n")
        out.append("{code}\n\n")
    }
}

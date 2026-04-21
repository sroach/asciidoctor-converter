package gy.roach.asciidoctor.jira

class SimpleAsciiDocParser : AsciiDocParser {
    override fun parse(asciidoc: String): DocumentNode {
        val lines = asciidoc.lines()
        val nodes = mutableListOf<Node>()
        val paragraphBuffer = mutableListOf<String>()

        fun flushParagraph() {
            val text = paragraphBuffer.joinToString(" ").trim()
            if (text.isNotEmpty()) {
                nodes += ParagraphNode(text)
            }
            paragraphBuffer.clear()
        }

        for (raw in lines) {
            val line = raw.trimEnd()

            when {
                line.isBlank() -> flushParagraph()

                line.startsWith("= ") -> {
                    flushParagraph()
                    nodes += HeadingNode(level = 1, text = line.removePrefix("= ").trim())
                }

                line.startsWith("== ") -> {
                    flushParagraph()
                    nodes += HeadingNode(level = 2, text = line.removePrefix("== ").trim())
                }

                line.startsWith("=== ") -> {
                    flushParagraph()
                    nodes += HeadingNode(level = 3, text = line.removePrefix("=== ").trim())
                }

                else -> paragraphBuffer += line.trim()
            }
        }

        flushParagraph()
        return DocumentNode(children = nodes)
    }
}

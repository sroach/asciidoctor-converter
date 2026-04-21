package gy.roach.asciidoctor.jira

class AsciiDocToJiraConverter(
    private val parser: AsciiDocParser,
    private val renderer: JiraRenderer
) {
    fun convert(asciidoc: String): ConversionResult {
        val document = parser.parse(asciidoc)
        val context = ConversionContext()
        val output = renderer.render(document, context)
        return ConversionResult(
            output = output,
            warnings = context.warnings.toList()
        )
    }
}


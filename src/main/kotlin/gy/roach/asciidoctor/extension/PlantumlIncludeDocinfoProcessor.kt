package gy.roach.asciidoctor.extension

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.DocinfoProcessor
import org.asciidoctor.extension.Location
import org.asciidoctor.extension.LocationType
import java.nio.charset.StandardCharsets

@Location(LocationType.HEADER)
class PlantumlIncludeDocinfoProcessor: DocinfoProcessor() {
    override fun process(document: Document?): String {


       //language=html
        return """
            <script type="text/javascript" src='https://plantuml.github.io/plantuml/js-plantuml/viz-global.js'></script>
            <script type="text/javascript" src='https://plantuml.github.io/plantuml/js-plantuml/plantuml.js'></script>
            <script>plantumlLoad();</script>
        """.trimIndent()
    }
}
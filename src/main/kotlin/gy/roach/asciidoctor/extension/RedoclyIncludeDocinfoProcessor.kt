package gy.roach.asciidoctor.extension

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.DocinfoProcessor
import org.asciidoctor.extension.Location
import org.asciidoctor.extension.LocationType

@Location(LocationType.HEADER)
class RedoclyIncludeDocinfoProcessor : DocinfoProcessor() {
    override fun process(document: Document): String {
        return """
        <script src="https://cdn.redoc.ly/redoc/latest/bundles/redoc.standalone.js"></script>
        <script src ="https://cdn.jsdelivr.net/npm/js-yaml@4.2.0/dist/js-yaml.min.js"></script>        
        """.trimIndent()
    }
}



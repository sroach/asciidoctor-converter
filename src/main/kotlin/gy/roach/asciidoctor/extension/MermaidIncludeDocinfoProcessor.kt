package gy.roach.asciidoctor.extension

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.DocinfoProcessor
import org.asciidoctor.extension.Location
import org.asciidoctor.extension.LocationType


@Location(LocationType.HEADER)
class MermaidIncludeDocinfoProcessor: DocinfoProcessor() {
    override fun process(document: Document): String {
        return "<script src='https://cdnjs.cloudflare.com/ajax/libs/mermaid/11.9.0/mermaid.min.js'></script>"
    }
}
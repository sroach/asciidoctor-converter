package gy.roach.asciidoctor.extension

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.DocinfoProcessor
import org.asciidoctor.extension.Location
import org.asciidoctor.extension.LocationType
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringWriter

@Location(LocationType.FOOTER)
class DocOpsMermaidDocinfoProcessor : DocinfoProcessor(){

    override fun process(document: Document): String {
        //language=html
        return """
            <script>
            mermaid.initialize({
                startOnLoad: true,
                    theme: 'neo',
                    look: 'neo'
                });
                </script>
        """.trimIndent()


    }
}
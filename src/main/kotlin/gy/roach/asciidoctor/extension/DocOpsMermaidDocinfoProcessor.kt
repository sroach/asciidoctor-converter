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
            mermaid.run({
                querySelector: '.mermaid',
                postRenderCallback: (id) => {
                console.log('Post-render callback for diagram:', id);
                const svg = document.getElementById(id);
                const container =svg.parentNode;
    
                // Initialize Panzoom
                const panzoomInstance = svgPanZoom(svg, {
                    controlIconsEnabled: true,
    
                    center: true,
                });
                var sizes= panzoomInstance.getSizes();
    
                container.style.width = sizes.width + 'px';
                container.style.height = sizes.height + 'px';
                console.log('Panzoom instance:', panzoomInstance.getSizes());
                svg.setAttribute('width', sizes.width);
                svg.setAttribute('height', sizes.height);
        }
    });
                </script>
        """.trimIndent()


    }
}
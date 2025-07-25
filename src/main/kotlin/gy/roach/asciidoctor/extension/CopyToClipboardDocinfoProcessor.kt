package gy.roach.asciidoctor.extension

import org.asciidoctor.ast.Document
import org.asciidoctor.extension.DocinfoProcessor
import org.asciidoctor.extension.Location
import org.asciidoctor.extension.LocationType
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringWriter

@Component
@Location(LocationType.FOOTER)
class CopyToClipboardDocinfoProcessor : DocinfoProcessor() {



    override fun process(document: Document): String {
        val css = readResource("/clipboard/clipboard.css")
        val javascript = readResource("/clipboard/clipboard.js")
        return String.format(
            "<style>%n%s%n</style>%n<script type=\"text/javascript\">%n%s%n</script>%n",
            css,
            javascript
        )
    }

    private fun readResource(name: String): String {
        val reader: Reader = InputStreamReader(CopyToClipboardDocinfoProcessor::class.java.getResourceAsStream(name))
        try {
            val writer = StringWriter()
            val buffer = CharArray(8192)
            var read: Int
            while ((reader.read(buffer).also { read = it }) >= 0) {
                writer.write(buffer, 0, read)
            }
            return writer.toString()
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to read '$name'", ex)
        } finally {
            try {
                reader.close()
            } catch (ex: IOException) {
                // Continue
            }
        }
    }
}

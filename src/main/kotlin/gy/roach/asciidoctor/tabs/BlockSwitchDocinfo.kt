package gy.roach.asciidoctor.tabs

import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringWriter


class BlockSwitchDocinfo {


    fun header(): String {
        val css = readResource("/blockswitch/blockSwitch.css")
        val javascript = readResource("/blockswitch/blockSwitch.js")
        return String.format(
            "<style>%n%s%n</style>%n<script type=\"text/javascript\">%n%s%n</script>%n", css,
            javascript
        )
    }
    private fun readResource(name: String): String? {
        val reader: Reader = InputStreamReader(BlockSwitchDocinfo::class.java.getResourceAsStream(name))
        try {
            val writer = StringWriter()
            val buffer = CharArray(8192)
            var read: Int
            while ((reader.read(buffer).also { read = it }) >= 0) {
                writer.write(buffer, 0, read)
            }
            return writer.toString()
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to read '" + name + "'", ex)
        } finally {
            try {
                reader.close()
            } catch (ex: IOException) {
                // Continue
            }
        }
    }
}
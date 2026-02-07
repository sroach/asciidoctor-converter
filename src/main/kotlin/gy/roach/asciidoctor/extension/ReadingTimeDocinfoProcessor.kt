package gy.roach.asciidoctor.extension

import gy.roach.asciidoctor.service.ReadingTimeService
import org.asciidoctor.ast.Document
import org.asciidoctor.extension.DocinfoProcessor
import org.asciidoctor.extension.Location
import org.asciidoctor.extension.LocationType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
@Location(LocationType.FOOTER)
class ReadingTimeDocinfoProcessor(private val readingTimeService: ReadingTimeService) : DocinfoProcessor() {
    
    private val logger = LoggerFactory.getLogger(ReadingTimeDocinfoProcessor::class.java)



    override fun process(document: Document): String {
        // Check if reading time is disabled via attribute
        val readingTimeAttribute = document.getAttribute("docops-reading-time", "off")
        if (readingTimeAttribute == "off") {
            logger.debug("Reading time disabled via docops-reading-time attribute")
            return ""
        }

        // Get the source file path from the document
        val sourcePath = document.getAttribute("docfile") as? String
        if (sourcePath.isNullOrEmpty()) {
            logger.debug("No source file path available in document")
            return ""
        }

        // Calculate reading time
        val readingTime = readingTimeService.calculateReadingTime(sourcePath)
        if (readingTime == null) {
            logger.debug("Could not calculate reading time for: $sourcePath")
            return ""
        }

        // Generate the reading time content with CSS
        return generateReadingTimeFooterContent(readingTime)
    }

    private fun generateReadingTimeFooterContent(readingTime: ReadingTimeService.ReadingTimeResult): String {
        val css = readingTimeService.generateReadingTimeCSS()
        
        return """
<style>
$css
</style>
<div class="reading-time">
    <p>📖 ${readingTime.formattedReadingTime} • ${readingTime.wordCount} words</p>
</div>
        """.trimIndent()
    }
}

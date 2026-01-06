package gy.roach.asciidoctor.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import kotlin.io.path.extension
import kotlin.io.path.name

@Service
class ReadingTimeService {
    private val logger = LoggerFactory.getLogger(ReadingTimeService::class.java)

    @Value("\${reading-time.words-per-minute:300}")
    private val wordsPerMinute: Int = 300

    @Value("\${reading-time.enabled:true}")
    private val readingTimeEnabled: Boolean = true

    data class ReadingTimeResult(
        val wordCount: Int,
        val readingTimeMinutes: Int,
        val readingTimeSeconds: Int,
        val formattedReadingTime: String,
        val processedFiles: List<String>
    )

    /**
     * Calculate reading time for an AsciiDoc file including all its includes
     */
    fun calculateReadingTime(filePath: String): ReadingTimeResult? {
        if (!readingTimeEnabled) {
            return null
        }

        val path = Paths.get(filePath)
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            logger.warn("File not found or not a regular file: $filePath")
            return null
        }

        if (!path.extension.equals("adoc", ignoreCase = true)) {
            logger.warn("File is not an AsciiDoc file: $filePath")
            return null
        }

        return try {
            val processedFiles = mutableSetOf<String>()
            val totalWordCount = processFileWithIncludes(path, processedFiles)

            val readingTimeSeconds = (totalWordCount * 60) / wordsPerMinute
            val readingTimeMinutes = readingTimeSeconds / 60
            val remainingSeconds = readingTimeSeconds % 60

            val formattedTime = formatReadingTime(readingTimeMinutes, remainingSeconds)

            ReadingTimeResult(
                wordCount = totalWordCount,
                readingTimeMinutes = readingTimeMinutes,
                readingTimeSeconds = readingTimeSeconds,
                formattedReadingTime = formattedTime,
                processedFiles = processedFiles.toList()
            )
        } catch (e: Exception) {
            logger.error("Error calculating reading time for file: $filePath", e)
            null
        }
    }

    /**
     * Process a file and all its includes recursively
     */
    private fun processFileWithIncludes(filePath: Path, processedFiles: MutableSet<String>): Int {
        val absolutePath = filePath.toAbsolutePath().toString()

        // Avoid circular includes
        if (processedFiles.contains(absolutePath)) {
            return 0
        }

        processedFiles.add(absolutePath)

        val content = Files.readString(filePath)
        var totalWordCount = countWords(content)

        // Find and process include directives
        val includeFiles = findIncludeFiles(content, filePath.parent)
        for (includeFile in includeFiles) {
            totalWordCount += processFileWithIncludes(includeFile, processedFiles)
        }

        return totalWordCount
    }

    /**
     * Find all include files in the content
     */
    private fun findIncludeFiles(content: String, baseDirectory: Path): List<Path> {
        val includeFiles = mutableListOf<Path>()

        // Pattern to match include directives: include::path[attributes]
        val includePattern = Pattern.compile("""include::([^\[\]]+)(\[.*?\])?""")
        val matcher = includePattern.matcher(content)

        while (matcher.find()) {
            val includePath = matcher.group(1)
            val resolvedPath = resolveIncludePath(includePath, baseDirectory)

            if (resolvedPath != null && Files.exists(resolvedPath) && Files.isRegularFile(resolvedPath)) {
                includeFiles.add(resolvedPath)
            } else {
                logger.warn("Include file not found: $includePath (resolved to: $resolvedPath)")
            }
        }

        return includeFiles
    }

    /**
     * Resolve include path relative to the base directory
     */
    private fun resolveIncludePath(includePath: String, baseDirectory: Path): Path? {
        return try {
            if (Paths.get(includePath).isAbsolute) {
                Paths.get(includePath)
            } else {
                baseDirectory.resolve(includePath).normalize()
            }
        } catch (e: Exception) {
            logger.warn("Failed to resolve include path: $includePath", e)
            null
        }
    }

    /**
     * Count words in AsciiDoc content, excluding markup
     */
    private fun countWords(content: String): Int {
        var cleanContent = content

        // Remove AsciiDoc comments
        cleanContent = cleanContent.replace(Regex("^//.*$", RegexOption.MULTILINE), "")

        // Remove block comments
        cleanContent = cleanContent.replace(Regex("////.*?////", RegexOption.DOT_MATCHES_ALL), "")

        // Remove attribute definitions
        cleanContent = cleanContent.replace(Regex("^:.*?:.*$", RegexOption.MULTILINE), "")

        // Remove section headers (keeping the text but removing markup)
        cleanContent = cleanContent.replace(Regex("^=+\\s*"), "")

        // Remove block delimiters
        cleanContent = cleanContent.replace(Regex("^----.*$", RegexOption.MULTILINE), "")
        cleanContent = cleanContent.replace(Regex("^\\*\\*\\*\\*.*$", RegexOption.MULTILINE), "")
        cleanContent = cleanContent.replace(Regex("^\\+\\+\\+\\+.*$", RegexOption.MULTILINE), "")

        // Remove inline formatting but keep the text
        cleanContent = cleanContent.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1") // Bold
        cleanContent = cleanContent.replace(Regex("\\*([^*]+)\\*"), "$1") // Italic
        cleanContent = cleanContent.replace(Regex("`([^`]+)`"), "$1") // Monospace
        cleanContent = cleanContent.replace(Regex("\\[([^]]+)\\]"), "$1") // Attributes

        // Remove links but keep link text
        cleanContent = cleanContent.replace(Regex("link:([^\\[]+)\\[([^]]+)\\]"), "$2")
        cleanContent = cleanContent.replace(Regex("https?://\\S+\\[([^]]+)\\]"), "$1")

        // Remove image directives
        cleanContent = cleanContent.replace(Regex("image::?[^\\[]+\\[.*?\\]"), "")

        // Remove include directives
        cleanContent = cleanContent.replace(Regex("include::[^\\[]+\\[.*?\\]"), "")

        // Remove table formatting
        cleanContent = cleanContent.replace(Regex("^\\|.*$", RegexOption.MULTILINE), "")

        // Split into words and count
        val words = cleanContent.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.matches(Regex(".*[a-zA-Z].*")) }

        return words.size
    }

    /**
     * Format reading time into a human-readable string
     */
    private fun formatReadingTime(minutes: Int, seconds: Int): String {
        return when {
            minutes == 0 && seconds < 30 -> "< 1 min read"
            minutes == 0 -> "1 min read"
            minutes == 1 -> "1 min read"
            else -> "$minutes min read"
        }
    }

    /**
     * Generate AsciiDoc content for reading time display in top-right corner
     */
    fun generateReadingTimeAdocContent(readingTime: ReadingTimeResult): String {
        return """
[.reading-time]
****
📖 ${readingTime.formattedReadingTime} • ${readingTime.wordCount} words
****
        """.trimIndent()
    }

    /**
     * Generate CSS for reading time display
     */
    fun generateReadingTimeCSS(): String {
        return """
/* Reading Time Styles */
.reading-time {
    position: fixed;
    top: 1.5rem;
    right: 2rem;
    background: var(--bg-base); /* Uses the dynamic background variable */
    backdrop-filter: blur(12px);
    border: 2px solid var(--accent-secondary);
    border-radius: 4px;
    padding: 0.5rem 1rem;
    z-index: 1005;
    
    /* Double-accent shadow follows the light/dark mode logic */
    box-shadow: 6px 6px 0px var(--accent-pink);
    
    font-family: var(--font-mono);
    font-size: 0.75rem;
    color: var(--text-main); /* Switches between grey and dark slate */
    letter-spacing: 0.05em;
    text-transform: uppercase;
    transition: background 0.3s ease, color 0.3s ease, box-shadow 0.3s ease;
}

.reading-time p {
    margin: 0 !important;
    font-weight: 600;
}

/* Light Mode specific opacity tweak */
body.light-mode .reading-time {
    background: rgba(255, 255, 255, 0.85); /* Slightly clearer for light backgrounds */
}
        """.trimIndent()
    }

    /**
     * Check if a file is an include file (typically doesn't have a document title)
     */
    fun isIncludeFile(filePath: String): Boolean {
        val path = Paths.get(filePath)
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return false
        }

        return try {
            val content = Files.readString(path)
            val lines = content.lines().take(10) // Check first 10 lines

            // Look for document title (= Title)
            val hasDocumentTitle = lines.any { line ->
                line.matches(Regex("^=\\s+.+")) && !line.matches(Regex("^==+\\s+.+"))
            }

            !hasDocumentTitle
        } catch (e: Exception) {
            logger.warn("Error checking if file is include file: $filePath", e)
            false
        }
    }

    /**
     * Add reading time to the bottom of an AsciiDoc file
     */
    fun addReadingTimeToFile(filePath: String): Boolean {
        if (isIncludeFile(filePath)) {
            logger.debug("Skipping include file: $filePath")
            return false
        }

        val readingTime = calculateReadingTime(filePath) ?: return false

        return try {
            val path = Paths.get(filePath)

            // Check if reading time is already present
            val existingContent = Files.readString(path)
            if (existingContent.contains("[.reading-time]")) {
                logger.debug("Reading time already present in file: $filePath")
                return true
            }

            // Generate reading time content
            val readingTimeContent = generateReadingTimeAdocContent(readingTime)

            // Append to the end of the file
            val contentToAppend = buildString {
                // Add newlines to separate from existing content
                if (!existingContent.endsWith("\n")) {
                    append("\n")
                }
                append("\n")
                append(readingTimeContent)
                append("\n")
            }

            // Use Files.writeString with APPEND option
            Files.writeString(path, contentToAppend, java.nio.file.StandardOpenOption.APPEND)

            logger.info("Added reading time to the bottom of file: $filePath (${readingTime.formattedReadingTime})")
            true
        } catch (e: Exception) {
            logger.error("Error adding reading time to file: $filePath", e)
            false
        }
    }


    /**
     * Copy reading time CSS to target directory
     */
    fun copyReadingTimeCSS(targetDirectory: String): Boolean {
        return try {
            val targetPath = Paths.get(targetDirectory)
            val cssDirectory = targetPath.resolve("data").resolve("css")
            Files.createDirectories(cssDirectory)

            val cssFileName = "reading-time.css"
            val targetCSSPath = cssDirectory.resolve(cssFileName)

            val cssContent = generateReadingTimeCSS()
            Files.writeString(targetCSSPath, cssContent)

            logger.debug("Created reading time CSS at: {}", targetCSSPath)
            true
        } catch (e: Exception) {
            logger.error("Failed to copy reading time CSS to $targetDirectory", e)
            false
        }
    }
}

package gy.roach.asciidoctor.web

import gy.roach.asciidoctor.service.AsciiDoctorConverter
import gy.roach.asciidoctor.service.ConversionStats
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.nio.file.Path
import java.nio.file.Paths

@Controller
@RequestMapping("/api")
class MainController(private val convert: AsciiDoctorConverter) {
    private val logger = LoggerFactory.getLogger(MainController::class.java)

    // Define allowed base directories for security
    private val allowedBasePaths = listOf(
        "/Users/steveroach/IdeaProjects",
        "/tmp/asciidoc-conversion",
        // Add other allowed base paths as needed
    )

    @GetMapping("/test")
    fun localTest(
        @RequestParam("sourceDir") sourceDirectory: String,
        @RequestParam("outputDir") outputDirectory: String
    ): ResponseEntity<String> {

        // Validate and sanitize the source directory
        val validatedSourceDir = validateAndSanitizePath(sourceDirectory)
        if (validatedSourceDir == null) {
            val errorMessage = "Invalid or unauthorized source directory path"
            logger.warn("Path traversal attempt detected for source directory: $sourceDirectory")
            return ResponseEntity.badRequest().body(errorMessage)
        }

        // Validate and sanitize the output directory
        val validatedOutputDir = validateAndSanitizePath(outputDirectory)
        if (validatedOutputDir == null) {
            val errorMessage = "Invalid or unauthorized output directory path"
            logger.warn("Path traversal attempt detected for output directory: $outputDirectory")
            return ResponseEntity.badRequest().body(errorMessage)
        }

        val localDirectory = validatedSourceDir.toFile()

        // Validate that the source directory exists and is a directory
        if (!localDirectory.exists() || !localDirectory.isDirectory) {
            val errorMessage = "Source directory does not exist or is not a directory"
            logger.error("Source directory validation failed: ${validatedSourceDir}")
            return ResponseEntity.badRequest().body(errorMessage)
        }

        try {
            // Convert files and get statistics
            val stats = convert.convert(localDirectory, validatedOutputDir.toString())

            // Create a response message with the statistics
            val responseMessage = buildResponseMessage(stats)
            logger.info("Conversion completed successfully for source: ${validatedSourceDir}")

            return ResponseEntity.ok(responseMessage)
        } catch (e: Exception) {
            val errorMessage = "Conversion failed: ${e.message}"
            logger.error("Conversion error", e)
            return ResponseEntity.internalServerError().body(errorMessage)
        }
    }

    private fun validateAndSanitizePath(inputPath: String): Path? {
        try {
            // Basic input validation
            if (inputPath.isBlank() || inputPath.length > 500) {
                return null
            }

            // Check for suspicious patterns
            val suspiciousPatterns = listOf("../", "..\\", "..", "%2e%2e", "~")
            if (suspiciousPatterns.any { inputPath.contains(it, ignoreCase = true) }) {
                return null
            }

            // Normalize the path to resolve any relative components
            val normalizedPath = Paths.get(inputPath).normalize().toAbsolutePath()

            // Check if the normalized path is within allowed base paths
            val isAllowed = allowedBasePaths.any { basePath ->
                normalizedPath.startsWith(Paths.get(basePath).normalize().toAbsolutePath())
            }

            if (!isAllowed) {
                return null
            }

            return normalizedPath
        } catch (e: Exception) {
            logger.warn("Path validation failed for: $inputPath", e)
            return null
        }
    }

    private fun buildResponseMessage(stats: ConversionStats): String {
        val message = StringBuilder()
        message.append("Conversion completed:\n")
        message.append("- Files needing conversion: ${stats.filesNeedingConversion}\n")
        message.append("- Files successfully converted: ${stats.filesConverted}\n")
        message.append("- Files copied: ${stats.filesCopied}\n")
        message.append("- Files failed: ${stats.filesFailed}\n")
        message.append("- Files deleted: ${stats.filesDeleted}\n")

        if (stats.filesFailed > 0) {
            message.append("- Failed files: ${stats.failedFiles.joinToString(", ")}\n")
        }

        if (stats.filesDeleted > 0) {
            message.append("- Deleted files: ${stats.deletedFiles.joinToString(", ")}\n")
        }

        if (stats.filesCopied > 0) {
            message.append("- Copied files: ${stats.copiedFiles.joinToString(", ")}\n")
        }

        return message.toString()
    }
}
package gy.roach.asciidoctor.web

import gy.roach.asciidoctor.config.ExecutionHistoryConfig
import gy.roach.asciidoctor.service.AsciiDoctorConverter
import gy.roach.asciidoctor.service.ConversionStats
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

@Controller
@RequestMapping("/api")
class MainController(private val convert: AsciiDoctorConverter,
                     private val historyConfig: ExecutionHistoryConfig,
                     private val htmlTemplateService: gy.roach.asciidoctor.service.HtmlTemplateService
) {
    private val logger = LoggerFactory.getLogger(MainController::class.java)

    // Thread-safe deque to store execution history
    private val executionHistory = ConcurrentLinkedDeque<ExecutionRecord>()

    // Thread-safe map to track active conversions by normalized source directory path
    private val activeConversions = ConcurrentHashMap<String, ActiveConversion>()

    // Define allowed base directories for security
    private val allowedBasePaths = listOf(
        "/Users/steveroach/IdeaProjects",
        "/tmp/asciidoc-conversion",
        // Add other allowed base paths as needed
    )

    @GetMapping("/test", produces = ["text/html"])
    fun localTest(
        @RequestParam("sourceDir") sourceDirectory: String,
        @RequestParam("outputDir") outputDirectory: String
    ): ResponseEntity<String> {

        // Capture start time
        val startTime = System.currentTimeMillis()
        val startTimestamp = LocalDateTime.now()

        // Validate and sanitize the source directory
        val validatedSourceDir = validateAndSanitizePath(sourceDirectory)
        if (validatedSourceDir == null) {
            val errorMessage = "Invalid or unauthorized source directory path"
            logger.warn("Path traversal attempt detected for source directory: $sourceDirectory")
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            recordExecution(startTimestamp, sourceDirectory, outputDirectory, duration, ConversionStats(), false, errorMessage)

            return ResponseEntity.badRequest().body(htmlTemplateService.buildErrorResponseMessage(errorMessage, startTimestamp, duration))
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
            logger.error("Source directory validation failed: $validatedSourceDir")
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            recordExecution(startTimestamp, sourceDirectory, outputDirectory, duration, ConversionStats(), false, errorMessage)

            return ResponseEntity.badRequest().body(htmlTemplateService.buildErrorResponseMessage(errorMessage, startTimestamp, duration))

        }
        // Check if conversion is already in progress for this source directory
        val normalizedSourcePath = validatedSourceDir.toString()
        val existingConversion = activeConversions[normalizedSourcePath]

        if (existingConversion != null) {
            val conflictMessage = "Conversion already in progress for source directory: $normalizedSourcePath (started at ${existingConversion.startTime})"
            logger.warn(conflictMessage)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            recordExecution(startTimestamp, sourceDirectory, outputDirectory, duration, ConversionStats(), false, conflictMessage)

            return ResponseEntity.status(409) // HTTP 409 Conflict
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(htmlTemplateService.buildConflictResponseMessage(conflictMessage, existingConversion, startTimestamp, duration))
        }

        // Generate execution ID for this conversion
        val executionId = java.util.UUID.randomUUID().toString()

        // Register this conversion as active
        val activeConversion = ActiveConversion(
            sourceDirectory = normalizedSourcePath,
            outputDirectory = validatedOutputDir.toString(),
            startTime = startTimestamp,
            executionId = executionId
        )

        activeConversions[normalizedSourcePath] = activeConversion
        logger.info("Started conversion for source directory: $normalizedSourcePath (execution ID: $executionId)")

        try {
            // Convert files and get statistics
            val stats = convert.convert(localDirectory, validatedOutputDir.toString())

            // Calculate execution duration
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // Record successful execution
            recordExecution(startTimestamp, sourceDirectory, outputDirectory, duration, stats, true)

            // Create a response message with the statistics including timing info
            val responseMessage = htmlTemplateService.buildResponseMessage(stats, startTimestamp, duration)


            logger.info("Conversion completed successfully for source: $validatedSourceDir in ${duration}ms (execution ID: $executionId)")

            return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(responseMessage)

        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // Record failed execution
            recordExecution(startTimestamp, sourceDirectory, outputDirectory, duration, ConversionStats(), false, e.message)

            val errorMessage = htmlTemplateService.buildErrorResponseMessage("Conversion failed: ${e.message}", startTimestamp, duration)
            logger.error("Conversion error after ${duration}ms (execution ID: $executionId)", e)
            return ResponseEntity.internalServerError()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(errorMessage)



        }finally {
            // Always remove the active conversion when done (success or failure)
            activeConversions.remove(normalizedSourcePath)
            logger.debug("Removed active conversion for source directory: $normalizedSourcePath (execution ID: $executionId)")
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


    @GetMapping("/active-conversions", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getActiveConversions(): ResponseEntity<Map<String, Any>> {
        val active = activeConversions.values.toList()
        return ResponseEntity.ok(mapOf(
            "activeConversions" to active,
            "count" to active.size
        ))
    }

    @GetMapping("/active-conversions/html", produces = ["text/html"])
    fun getActiveConversionsHtml(): ResponseEntity<String> {
        val active = activeConversions.values.toList()
        return ResponseEntity.ok()
            .header("Content-Type", "text/html; charset=UTF-8")
            .body(htmlTemplateService.buildActiveConversionsResponseMessage(active))
    }

    @GetMapping("/stats", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getStatsJson(): ResponseEntity<Map<String, Any>> {
        val history = executionHistory.toList()
        val summary = calculateSummary(history)

        return ResponseEntity.ok(mapOf(
            "summary" to summary,
            "executions" to history,
        "maxHistorySize" to historyConfig.maxSize
        ))
    }

    @GetMapping("/stats/html", produces = ["text/html"])
    fun getStatsHtml(): ResponseEntity<String> {
        val history = executionHistory.toList()
        val summary = calculateSummary(history)

        return ResponseEntity.ok()
            .header("Content-Type", "text/html; charset=UTF-8")
            .body(htmlTemplateService.buildStatsResponseMessage(summary, history))
    }

    @GetMapping("/stats/{executionId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getExecutionDetails(@PathVariable executionId: String): ResponseEntity<ExecutionRecord> {
        val execution = executionHistory.find { it.id == executionId }
        return if (execution != null) {
            ResponseEntity.ok(execution)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/stats/{executionId}/html", produces = ["text/html"])
    fun getExecutionDetailsHtml(@PathVariable executionId: String): ResponseEntity<String> {
        val execution = executionHistory.find { it.id == executionId }
        return if (execution != null) {
            ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(htmlTemplateService.buildExecutionDetailsResponseMessage(execution))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    private fun recordExecution(
        timestamp: LocalDateTime,
        sourceDir: String,
        outputDir: String,
        duration: Long,
        stats: ConversionStats,
        success: Boolean,
        errorMessage: String? = null
    ) {
        val record = ExecutionRecord(
            timestamp = timestamp,
            sourceDirectory = sourceDir,
            outputDirectory = outputDir,
            durationMs = duration,
            stats = stats,
            success = success,
            errorMessage = errorMessage
        )

        // Add to the front of the deque (most recent first)
        executionHistory.addFirst(record)

        // Remove oldest entries if we exceed max size
        while (executionHistory.size > historyConfig.maxSize) {
            executionHistory.removeLast()
        }

        logger.debug("Recorded execution: ${record.id} - Success: $success")
    }

    private fun calculateSummary(history: List<ExecutionRecord>): ExecutionSummary {
        if (history.isEmpty()) {
            return ExecutionSummary(0, 0, 0, 0, 0, 0, 0, null)
        }

        val successfulExecutions = history.count { it.success }
        val failedExecutions = history.count { !it.success }
        val averageDuration = if (history.isNotEmpty()) history.map { it.durationMs }.average().toLong() else 0L
        val totalFilesConverted = history.sumOf { it.stats.filesConverted }
        val totalFilesCopied = history.sumOf { it.stats.filesCopied }
        val totalFilesDeleted = history.sumOf { it.stats.filesDeleted }

        return ExecutionSummary(
            totalExecutions = history.size,
            successfulExecutions = successfulExecutions,
            failedExecutions = failedExecutions,
            averageDurationMs = averageDuration,
            totalFilesConverted = totalFilesConverted,
            totalFilesCopied = totalFilesCopied,
            totalFilesDeleted = totalFilesDeleted,
            lastExecution = history.firstOrNull()
        )
    }

}

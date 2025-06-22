package gy.roach.asciidoctor.web

import com.fasterxml.jackson.databind.ObjectMapper
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
import java.util.concurrent.ConcurrentLinkedDeque

@Controller
@RequestMapping("/api")
class MainController(private val convert: AsciiDoctorConverter,
                     private val historyConfig: ExecutionHistoryConfig,
                     private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(MainController::class.java)

    // Thread-safe deque to store execution history
    private val executionHistory = ConcurrentLinkedDeque<ExecutionRecord>()

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
        val startTimestamp = java.time.LocalDateTime.now()

        // Validate and sanitize the source directory
        val validatedSourceDir = validateAndSanitizePath(sourceDirectory)
        if (validatedSourceDir == null) {
            val errorMessage = "Invalid or unauthorized source directory path"
            logger.warn("Path traversal attempt detected for source directory: $sourceDirectory")
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            recordExecution(startTimestamp, sourceDirectory, outputDirectory, duration, ConversionStats(), false, errorMessage)

            return ResponseEntity.badRequest().body(buildErrorResponseMessage(errorMessage, startTimestamp, duration))
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
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            recordExecution(startTimestamp, sourceDirectory, outputDirectory, duration, ConversionStats(), false, errorMessage)

            return ResponseEntity.badRequest().body(buildErrorResponseMessage(errorMessage, startTimestamp, duration))

        }

        try {
            // Convert files and get statistics
            val stats = convert.convert(localDirectory, validatedOutputDir.toString())

            // Calculate execution duration
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // Record successful execution
            recordExecution(startTimestamp, sourceDirectory, outputDirectory, duration, stats, true)

            // Create a response message with the statistics including timing info
            val responseMessage = buildResponseMessage(stats, startTimestamp, duration)


            logger.info("Conversion completed successfully for source: ${validatedSourceDir}")

            return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(responseMessage)
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // Record failed execution
            recordExecution(startTimestamp, sourceDirectory, outputDirectory, duration, ConversionStats(), false, e.message)

            val errorMessage = buildErrorResponseMessage("Conversion failed: ${e.message}", startTimestamp, duration)
            logger.error("Conversion error after ${duration}ms", e)
            return ResponseEntity.internalServerError()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(errorMessage)


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

    private fun buildResponseMessage(stats: ConversionStats, startTimestamp: java.time.LocalDateTime, durationMs: Long): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTimestamp = startTimestamp.format(formatter)
        val durationFormatted = formatDuration(durationMs)

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Conversion Results</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    margin: 0;
                    padding: 20px;
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                .container {
                    background: white;
                    border-radius: 20px;
                    box-shadow: 0 20px 40px rgba(0,0,0,0.1);
                    padding: 40px;
                    max-width: 700px;
                    width: 100%;
                }
                .header {
                    text-align: center;
                    margin-bottom: 30px;
                }
                .header h1 {
                    color: #2d3748;
                    font-size: 28px;
                    margin-bottom: 10px;
                    font-weight: 600;
                }
                .success-badge {
                    background: linear-gradient(45deg, #48bb78, #38a169);
                    color: white;
                    padding: 8px 16px;
                    border-radius: 20px;
                    font-size: 14px;
                    font-weight: 500;
                    display: inline-block;
                }
                .timing-info {
                    background: #edf2f7;
                    border-radius: 12px;
                    padding: 20px;
                    margin-bottom: 30px;
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 20px;
                }
                .timing-item {
                    text-align: center;
                }
                .timing-label {
                    color: #718096;
                    font-size: 14px;
                    font-weight: 500;
                    margin-bottom: 8px;
                }
                .timing-value {
                    color: #2d3748;
                    font-size: 18px;
                    font-weight: 600;
                }
                .duration-highlight {
                    color: #805ad5;
                    font-weight: 700;
                }
                .stats-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 20px;
                    margin-bottom: 30px;
                }
                .stat-card {
                    background: #f7fafc;
                    border-radius: 12px;
                    padding: 20px;
                    border-left: 4px solid #4299e1;
                    transition: transform 0.2s ease;
                }
                .stat-card:hover {
                    transform: translateY(-2px);
                }
                .stat-label {
                    color: #718096;
                    font-size: 14px;
                    font-weight: 500;
                    margin-bottom: 8px;
                }
                .stat-value {
                    color: #2d3748;
                    font-size: 24px;
                    font-weight: 700;
                }
                .details-section {
                    margin-top: 30px;
                }
                .details-title {
                    color: #2d3748;
                    font-size: 18px;
                    font-weight: 600;
                    margin-bottom: 15px;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                }
                .file-list {
                    background: #f7fafc;
                    border-radius: 8px;
                    padding: 15px;
                    margin-bottom: 20px;
                }
                .file-item {
                    color: #4a5568;
                    font-size: 14px;
                    padding: 4px 0;
                    border-bottom: 1px solid #e2e8f0;
                }
                .file-item:last-child {
                    border-bottom: none;
                }
                .error-section .stat-card {
                    border-left-color: #f56565;
                }
                .success-section .stat-card {
                    border-left-color: #48bb78;
                }
                .icon {
                    width: 20px;
                    height: 20px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>🚀 Conversion Completed</h1>
                    <span class="success-badge">✅ Operation Successful</span>
                </div>
                
                <div class="timing-info">
                    <div class="timing-item">
                        <div class="timing-label">🕐 Execution Started</div>
                        <div class="timing-value">$formattedTimestamp</div>
                    </div>
                    <div class="timing-item">
                        <div class="timing-label">⏱️ Execution Duration</div>
                        <div class="timing-value duration-highlight">$durationFormatted</div>
                    </div>
                </div>
                
                <div class="stats-grid">
                    <div class="stat-card success-section">
                        <div class="stat-label">Files Needing Conversion</div>
                        <div class="stat-value">${stats.filesNeedingConversion}</div>
                    </div>
                    <div class="stat-card success-section">
                        <div class="stat-label">Successfully Converted</div>
                        <div class="stat-value">${stats.filesConverted}</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-label">Files Copied</div>
                        <div class="stat-value">${stats.filesCopied}</div>
                    </div>
                    <div class="stat-card ${if (stats.filesFailed > 0) "error-section" else ""}">
                        <div class="stat-label">Failed Files</div>
                        <div class="stat-value">${stats.filesFailed}</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-label">Files Deleted</div>
                        <div class="stat-value">${stats.filesDeleted}</div>
                    </div>
                </div>
                
                ${if (stats.filesFailed > 0) """
                <div class="details-section">
                    <div class="details-title">
                        ⚠️ Failed Files
                    </div>
                    <div class="file-list">
                        ${stats.failedFiles.joinToString("") { "<div class=\"file-item\">$it</div>" }}
                    </div>
                </div>
                """ else ""}
                
                ${if (stats.filesDeleted > 0) """
                <div class="details-section">
                    <div class="details-title">
                        🗑️ Deleted Files
                    </div>
                    <div class="file-list">
                        ${stats.deletedFiles.joinToString("") { "<div class=\"file-item\">$it</div>" }}
                    </div>
                </div>
                """ else ""}
                
                ${if (stats.filesCopied > 0) """
                <div class="details-section">
                    <div class="details-title">
                        📋 Copied Files
                    </div>
                    <div class="file-list">
                        ${stats.copiedFiles.joinToString("") { "<div class=\"file-item\">$it</div>" }}
                    </div>
                </div>
                """ else ""}
            </div>
        </body>
        </html>
    """.trimIndent()
    }

    private fun buildErrorResponseMessage(errorMessage: String, startTimestamp: java.time.LocalDateTime, durationMs: Long): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTimestamp = startTimestamp.format(formatter)
        val durationFormatted = formatDuration(durationMs)

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Conversion Error</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                    background: linear-gradient(135deg, #fc8181 0%, #f56565 100%);
                    margin: 0;
                    padding: 20px;
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                .container {
                    background: white;
                    border-radius: 20px;
                    box-shadow: 0 20px 40px rgba(0,0,0,0.1);
                    padding: 40px;
                    max-width: 600px;
                    width: 100%;
                    text-align: center;
                }
                .error-icon {
                    font-size: 48px;
                    margin-bottom: 20px;
                }
                .error-title {
                    color: #e53e3e;
                    font-size: 24px;
                    font-weight: 600;
                    margin-bottom: 15px;
                }
                .error-message {
                    color: #4a5568;
                    font-size: 16px;
                    line-height: 1.5;
                    margin-bottom: 20px;
                }
                .timing-info {
                    background: #f7fafc;
                    border-radius: 12px;
                    padding: 20px;
                    margin-top: 20px;
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 20px;
                }
                .timing-item {
                    text-align: center;
                }
                .timing-label {
                    color: #718096;
                    font-size: 14px;
                    font-weight: 500;
                    margin-bottom: 8px;
                }
                .timing-value {
                    color: #2d3748;
                    font-size: 16px;
                    font-weight: 600;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="error-icon">❌</div>
                <div class="error-title">Conversion Failed</div>
                <div class="error-message">$errorMessage</div>
                <div class="timing-info">
                    <div class="timing-item">
                        <div class="timing-label">🕐 Started At</div>
                        <div class="timing-value">$formattedTimestamp</div>
                    </div>
                    <div class="timing-item">
                        <div class="timing-label">⏱️ Failed After</div>
                        <div class="timing-value">$durationFormatted</div>
                    </div>
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()
    }
    private fun formatDuration(durationMs: Long): String {
        return when {
            durationMs < 1000 -> "${durationMs}ms"
            durationMs < 60000 -> String.format("%.2fs", durationMs / 1000.0)
            durationMs < 3600000 -> {
                val minutes = durationMs / 60000
                val seconds = (durationMs % 60000) / 1000.0
                String.format("%dm %.1fs", minutes, seconds)
            }
            else -> {
                val hours = durationMs / 3600000
                val minutes = (durationMs % 3600000) / 60000
                val seconds = (durationMs % 60000) / 1000.0
                String.format("%dh %dm %.1fs", hours, minutes, seconds)
            }
        }
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
            .body(buildStatsResponseMessage(summary, history))
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
                .body(buildExecutionDetailsResponseMessage(execution))
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
    private fun buildStatsResponseMessage(summary: ExecutionSummary, history: List<ExecutionRecord>): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Execution Statistics</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    margin: 0;
                    padding: 20px;
                    min-height: 100vh;
                }
                .container {
                    background: white;
                    border-radius: 20px;
                    box-shadow: 0 20px 40px rgba(0,0,0,0.1);
                    padding: 40px;
                    max-width: 1200px;
                    margin: 0 auto;
                }
                .header {
                    text-align: center;
                    margin-bottom: 30px;
                }
                .header h1 {
                    color: #2d3748;
                    font-size: 32px;
                    margin-bottom: 10px;
                    font-weight: 600;
                }
                .summary-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 20px;
                    margin-bottom: 40px;
                }
                .summary-card {
                    background: #f7fafc;
                    border-radius: 12px;
                    padding: 20px;
                    border-left: 4px solid #4299e1;
                    text-align: center;
                }
                .summary-card.success {
                    border-left-color: #48bb78;
                }
                .summary-card.error {
                    border-left-color: #f56565;
                }
                .summary-label {
                    color: #718096;
                    font-size: 14px;
                    font-weight: 500;
                    margin-bottom: 8px;
                }
                .summary-value {
                    color: #2d3748;
                    font-size: 28px;
                    font-weight: 700;
                }
                .history-section {
                    margin-top: 40px;
                }
                .history-title {
                    color: #2d3748;
                    font-size: 24px;
                    font-weight: 600;
                    margin-bottom: 20px;
                }
                .execution-item {
                    background: #f7fafc;
                    border-radius: 12px;
                    padding: 20px;
                    margin-bottom: 15px;
                    border-left: 4px solid #4299e1;
                }
                .execution-item.success {
                    border-left-color: #48bb78;
                }
                .execution-item.failed {
                    border-left-color: #f56565;
                }
                .execution-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 15px;
                }
                .execution-id {
                    font-family: monospace;
                    font-size: 12px;
                    color: #718096;
                }
                .execution-time {
                    color: #4a5568;
                    font-size: 14px;
                }
                .execution-stats {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
                    gap: 15px;
                    font-size: 14px;
                    color: #4a5568;
                }
                .stat-item {
                    display: flex;
                    justify-content: space-between;
                }
                .badge {
                    padding: 4px 12px;
                    border-radius: 12px;
                    font-size: 12px;
                    font-weight: 600;
                }
                .badge.success {
                    background: #c6f6d5;
                    color: #22543d;
                }
                .badge.failed {
                    background: #fed7d7;
                    color: #742a2a;
                }
                .refresh-button {
                    background: linear-gradient(45deg, #4299e1, #3182ce);
                    color: white;
                    border: none;
                    padding: 12px 24px;
                    border-radius: 8px;
                    font-size: 14px;
                    font-weight: 600;
                    cursor: pointer;
                    margin-bottom: 20px;
                }
                .refresh-button:hover {
                    background: linear-gradient(45deg, #3182ce, #2c5aa0);
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>📊 Execution Statistics</h1>
                    <button class="refresh-button" onclick="location.reload()">🔄 Refresh</button>
                </div>
                
                <div class="summary-grid">
                    <div class="summary-card">
                        <div class="summary-label">Total Executions</div>
                        <div class="summary-value">${summary.totalExecutions}</div>
                    </div>
                    <div class="summary-card success">
                        <div class="summary-label">Successful</div>
                        <div class="summary-value">${summary.successfulExecutions}</div>
                    </div>
                    <div class="summary-card error">
                        <div class="summary-label">Failed</div>
                        <div class="summary-value">${summary.failedExecutions}</div>
                    </div>
                    <div class="summary-card">
                        <div class="summary-label">Avg Duration</div>
                        <div class="summary-value">${formatDuration(summary.averageDurationMs)}</div>
                    </div>
                    <div class="summary-card">
                        <div class="summary-label">Files Converted</div>
                        <div class="summary-value">${summary.totalFilesConverted}</div>
                    </div>
                    <div class="summary-card">
                        <div class="summary-label">Files Copied</div>
                        <div class="summary-value">${summary.totalFilesCopied}</div>
                    </div>
                </div>
                
                <div class="history-section">
                    <div class="history-title">Recent Executions</div>
                    ${if (history.isEmpty()) """
                        <div style="text-align: center; color: #718096; padding: 40px;">
                            No executions recorded yet
                        </div>
                    """ else history.joinToString("") { execution ->
            val statusClass = if (execution.success) "success" else "failed"
            val badge = if (execution.success)
                """<span class="badge success">✅ Success</span>"""
            else
                """<span class="badge failed">❌ Failed</span>"""

            """
                        <div class="execution-item $statusClass">
                            <div class="execution-header">
                                <div>
                                    $badge
                                    <span class="execution-id">${execution.id}</span>
                                </div>
                                <div class="execution-time">${execution.timestamp.format(formatter)}</div>
                            </div>
                            <div class="execution-stats">
                                <div class="stat-item">
                                    <span>Duration:</span>
                                    <span>${formatDuration(execution.durationMs)}</span>
                                </div>
                                <div class="stat-item">
                                    <span>Converted:</span>
                                    <span>${execution.stats.filesConverted}</span>
                                </div>
                                <div class="stat-item">
                                    <span>Copied:</span>
                                    <span>${execution.stats.filesCopied}</span>
                                </div>
                                <div class="stat-item">
                                    <span>Failed:</span>
                                    <span>${execution.stats.filesFailed}</span>
                                </div>
                                ${if (!execution.success && execution.errorMessage != null) """
                                <div class="stat-item" style="grid-column: 1 / -1; color: #e53e3e;">
                                    <span>Error:</span>
                                    <span>${execution.errorMessage}</span>
                                </div>
                                """ else ""}
                            </div>
                        </div>
                        """
        }}
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun buildExecutionDetailsResponseMessage(execution: ExecutionRecord): String {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTimestamp = execution.timestamp.format(formatter)
        val durationFormatted = formatDuration(execution.durationMs)

        return buildResponseMessage(execution.stats, execution.timestamp, execution.durationMs)
    }


}
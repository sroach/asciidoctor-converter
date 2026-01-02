package gy.roach.asciidoctor.web

import gy.roach.asciidoctor.config.AllowedPathsConfig
import gy.roach.asciidoctor.config.ConverterSettings
import gy.roach.asciidoctor.config.ExecutionHistoryConfig
import gy.roach.asciidoctor.service.AsciiDoctorConverter
import gy.roach.asciidoctor.service.ConversionContext
import gy.roach.asciidoctor.service.ConversionJobService
import gy.roach.asciidoctor.service.ConversionStats
import gy.roach.asciidoctor.service.SitemapService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

@Controller
@RequestMapping("/api")
class MainController(private val convert: AsciiDoctorConverter,
                     private val historyConfig: ExecutionHistoryConfig,
                     private val allowedPathsConfig: AllowedPathsConfig,
                     private val htmlTemplateService: gy.roach.asciidoctor.service.HtmlTemplateService,
                     private val conversionJobService: ConversionJobService,
                     private val sitemapService: SitemapService,
                     private val converterSettings: ConverterSettings,
                     private val conversionContext: ConversionContext
) {
    @Value("\${sitemap.directory-depth:2}")
    private val defaultDirectoryDepth: Int = 2

    private val logger = LoggerFactory.getLogger(MainController::class.java)

    // Thread-safe deque to store execution history
    //private val executionHistory = ConcurrentLinkedDeque<ExecutionRecord>()

    // Thread-safe map to track active conversions by normalized source directory path
    //private val activeConversions = ConcurrentHashMap<String, ActiveConversion>()

    // Counter for total executions (including those removed from history)
    //private var totalExecutionCount = 0



    @GetMapping("/test", produces = ["text/html"])
    fun localConversion(
        @RequestParam("sourceDir") sourceDirectory: String,
        @RequestParam("outputDir") outputDirectory: String,
        @RequestParam("cssTheme", defaultValue = "github-markdown-css.css") cssTheme: String
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
        val existingConversion = conversionContext.activeConversions[normalizedSourcePath]

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

        conversionContext.activeConversions[normalizedSourcePath] = activeConversion
        logger.info("Started conversion for source directory: $normalizedSourcePath (execution ID: $executionId)")

        try {
            // Pre-generate sitemap.adoc in the source directory BEFORE conversion

            try {
                val sitemapPath = sitemapService.generateSitemapAdocInSourceDirectory(localDirectory.absolutePath, defaultDirectoryDepth, validatedOutputDir.toString())
                if (sitemapPath != null) {
                    // Copy sitemap icon to target directory so it's available during conversion
                    sitemapService.copySitemapIconToTarget(validatedOutputDir.toString())

                    logger.info("Pre-generated sitemap.adoc for conversion: $sitemapPath")
                } else {
                    logger.warn("Failed to pre-generate sitemap.adoc in source directory: $validatedSourceDir")
                }
            } catch (e: Exception) {
                logger.error("Error pre-generating sitemap.adoc in source directory: $validatedSourceDir", e)
                // Don't fail the entire conversion if sitemap generation fails
            }

            // Convert files and get statistics
            val stats = convert.convert(localDirectory, validatedOutputDir.toString(), cssTheme = cssTheme)

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



        } finally {
        // Clean up: delete sitemap.adoc from source directory after conversion
        try {
            sitemapService.deleteSitemapAdocFromSource(validatedSourceDir.toString())
            logger.debug("Cleaned up sitemap.adoc from source directory: {}", validatedSourceDir)
        } catch (e: Exception) {
            logger.warn("Failed to clean up sitemap.adoc from source directory: {}", validatedSourceDir, e)
        }

        // Always remove the active conversion when done (success or failure)
            conversionContext.activeConversions.remove(normalizedSourcePath)
        logger.debug("Removed active conversion for source directory: $normalizedSourcePath (execution ID: $executionId)")
    }


}

    fun validateAndSanitizePath(inputPath: String): Path? {
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
            val isAllowed = allowedPathsConfig.allowedBasePaths.any { basePath ->
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
        val active = conversionContext.activeConversions.values.toList()
        return ResponseEntity.ok(mapOf(
            "activeConversions" to active,
            "count" to active.size
        ))
    }

    @GetMapping("/active-conversions/html", produces = ["text/html"])
    fun getActiveConversionsHtml(): ResponseEntity<String> {
        val active = conversionContext.activeConversions.values.toList()
        return ResponseEntity.ok()
            .header("Content-Type", "text/html; charset=UTF-8")
            .body(htmlTemplateService.buildActiveConversionsResponseMessage(active))
    }

    @GetMapping("/stats", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getStatsJson(): ResponseEntity<Map<String, Any>> {
        val history = conversionContext.executionHistory.toList()
        val summary = calculateSummary(history)

        return ResponseEntity.ok(mapOf(
            "summary" to summary,
            "executions" to history,
            "maxHistorySize" to historyConfig.maxSize
        ))
    }

    @GetMapping("/stats/html", produces = ["text/html"])
    fun getStatsHtml(): ResponseEntity<String> {
        val history = conversionContext.executionHistory.toList()
        val summary = calculateSummary(history)

        return ResponseEntity.ok()
            .header("Content-Type", "text/html; charset=UTF-8")
            .body(htmlTemplateService.buildStatsResponseMessage(summary, history))
    }

    @GetMapping("/stats/{executionId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getExecutionDetails(@PathVariable executionId: String): ResponseEntity<ExecutionRecord> {
        val execution = conversionContext.executionHistory.find { it.id == executionId }
        return if (execution != null) {
            ResponseEntity.ok(execution)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/stats/{executionId}/html", produces = ["text/html"])
    fun getExecutionDetailsHtml(@PathVariable executionId: String): ResponseEntity<String> {
        val execution = conversionContext.executionHistory.find { it.id == executionId }
        return if (execution != null) {
            ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(htmlTemplateService.buildExecutionDetailsResponseMessage(execution))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    fun recordExecution(
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
        conversionContext.executionHistory.addFirst(record)

        // Increment total execution count
        conversionContext.totalExecutionCount.getAndIncrement()

        // Remove oldest entries if we exceed max size
        while (conversionContext.executionHistory.size > historyConfig.maxSize) {
            conversionContext.executionHistory.removeLast()
        }

        logger.debug("Recorded execution: ${record.id} - Success: $success")
    }

    fun calculateSummary(history: List<ExecutionRecord>): ExecutionSummary {
        if (history.isEmpty()) {
            return ExecutionSummary(conversionContext.totalExecutionCount, 0, 0, 0, 0, 0, 0, null)
        }

        val successfulExecutions = history.count { it.success }
        val failedExecutions = history.count { !it.success }
        val averageDuration = if (history.isNotEmpty()) history.map { it.durationMs }.average().toLong() else 0L
        val totalFilesConverted = history.sumOf { it.stats.filesConverted }
        val totalFilesCopied = history.sumOf { it.stats.filesCopied }
        val totalFilesDeleted = history.sumOf { it.stats.filesDeleted }

        return ExecutionSummary(
            totalExecutions = conversionContext.totalExecutionCount,
            successfulExecutions = successfulExecutions,
            failedExecutions = failedExecutions,
            averageDurationMs = averageDuration,
            totalFilesConverted = totalFilesConverted,
            totalFilesCopied = totalFilesCopied,
            totalFilesDeleted = totalFilesDeleted,
            lastExecution = history.firstOrNull()
        )
    }

    /**
     * Start a PDF conversion job
     *
     * @param sourceDirectory Source directory containing AsciiDoc files
     * @param outputDirectory Output directory for PDF files
     * @return Job ID
     */
    @PostMapping("/pdf/convert", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun convertToPdf(
        @RequestParam("sourceDir") sourceDirectory: String,
        @RequestParam("outputDir") outputDirectory: String
    ): ResponseEntity<Map<String, Any>> {
        // Validate and sanitize the source directory
        val validatedSourceDir = validateAndSanitizePath(sourceDirectory)
        if (validatedSourceDir == null) {
            logger.warn("Path traversal attempt detected for source directory: $sourceDirectory")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Invalid or unauthorized source directory path"
            ))
        }

        // Validate and sanitize the output directory
        val validatedOutputDir = validateAndSanitizePath(outputDirectory)
        if (validatedOutputDir == null) {
            logger.warn("Path traversal attempt detected for output directory: $outputDirectory")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Invalid or unauthorized output directory path"
            ))
        }

        val localDirectory = validatedSourceDir.toFile()

        // Validate that the source directory exists and is a directory
        if (!localDirectory.exists() || !localDirectory.isDirectory) {
            logger.error("Source directory validation failed: $validatedSourceDir")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Source directory does not exist or is not a directory"
            ))
        }

        // Get all AsciiDoc files from the source directory
        val adocFiles = localDirectory.walkTopDown()
            .filter { it.isFile && it.extension == "adoc" }
            .toList()

        if (adocFiles.isEmpty()) {
            logger.warn("No AsciiDoc files found in source directory: $validatedSourceDir")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "No AsciiDoc files found in source directory"
            ))
        }

        // Start the conversion job
        val jobId = conversionJobService.startPdfConversion(adocFiles, validatedOutputDir.toString())
        logger.info("Started PDF conversion job: $jobId for source directory: $validatedSourceDir")

        return ResponseEntity.ok(mapOf(
            "jobId" to jobId,
            "status" to "QUEUED",
            "message" to "PDF conversion job started",
            "files" to adocFiles.size
        ))
    }

    /**
     * Get the status of a PDF conversion job
     *
     * @param jobId Job ID
     * @return Job status
     */
    @GetMapping("/pdf/status/{jobId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getPdfConversionStatus(@PathVariable jobId: String): ResponseEntity<Map<String, Any>> {
        val job = conversionJobService.getJobStatus(jobId)

        if (job == null) {
            logger.warn("Job not found: $jobId")
            return ResponseEntity.notFound().build()
        }

        val response = mutableMapOf<String, Any>(
            "jobId" to job.id,
            "status" to job.status.toString(),
            "progress" to job.progress
        )

        // Add stats if available
        job.stats?.let { stats ->
            response["stats"] = mapOf(
                "filesConverted" to stats.filesConverted,
                "filesFailed" to stats.filesFailed,
                "failedFiles" to stats.failedFiles
            )
        }

        // Add error message if available
        job.errorMessage?.let { errorMessage ->
            response["error"] = errorMessage
        }

        return ResponseEntity.ok(response)
    }

    /**
     * Get all PDF conversion jobs
     *
     * @return List of jobs
     */
    @GetMapping("/pdf/jobs", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAllPdfConversionJobs(): ResponseEntity<Map<String, Any>> {
        val jobs = conversionJobService.getAllJobs()

        val jobsData = jobs.map { (jobId, job) ->
            val jobData = mutableMapOf<String, Any>(
                "jobId" to job.id,
                "status" to job.status.toString(),
                "progress" to job.progress
            )

            // Add stats if available
            job.stats?.let { stats ->
                jobData["stats"] = mapOf(
                    "filesConverted" to stats.filesConverted,
                    "filesFailed" to stats.filesFailed
                )
            }

            // Add error message if available
            job.errorMessage?.let { errorMessage ->
                jobData["error"] = errorMessage
            }

            jobId to jobData
        }.toMap()

        return ResponseEntity.ok(mapOf(
            "jobs" to jobsData
        ))
    }

    @PostMapping("/convert", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun convertToMultipleFormats(
        @RequestParam("sourceDir") sourceDirectory: String,
        @RequestParam("outputDir") outputDirectory: String,
        @RequestParam("formats", defaultValue = "") formats: String,
        @RequestParam("cssTheme", defaultValue = "github-markdown-css.css") cssTheme: String
    ): ResponseEntity<Map<String, Any>> {

        // Validate paths
        val validatedSourceDir = validateAndSanitizePath(sourceDirectory)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Invalid source directory"))
        val validatedOutputDir = validateAndSanitizePath(outputDirectory)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Invalid output directory"))

        // Parse and validate formats
        val requestedFormats = if (formats.isBlank()) {
            converterSettings.defaultFormats
        } else {
            formats.split(",").map { it.trim().lowercase() }
        }

        val supportedFormats = listOf("html", "pdf", "epub")
        val invalidFormats = requestedFormats - supportedFormats
        if (invalidFormats.isNotEmpty()) {
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Unsupported formats: $invalidFormats. Allowed: $supportedFormats"
            ))
        }

        val localDirectory = validatedSourceDir.toFile()
        val adocFiles = localDirectory.walkTopDown()
            .filter { it.isFile && it.extension == "adoc" }
            .toList()

        if (adocFiles.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "No AsciiDoc files found in source directory"
            ))
        }

        val conversionResults = mutableMapOf<String, Any>()
        val jobIds = mutableMapOf<String, String>()

        // Process each format independently
        requestedFormats.forEach { format ->
            try {
                when (format) {
                    "html" -> {
                        val stats = convert.convert(localDirectory, validatedOutputDir.toString(), cssTheme)
                        conversionResults[format] = mapOf(
                            "status" to "completed",
                            "stats" to stats
                        )
                    }
                    "pdf" -> {
                        val jobId = conversionJobService.startPdfConversion(adocFiles, validatedOutputDir.toString())
                        jobIds[format] = jobId
                        conversionResults[format] = mapOf(
                            "status" to "queued",
                            "jobId" to jobId
                        )
                    }
                    "epub" -> {
                        val jobId = conversionJobService.startEpubConversion(adocFiles, validatedOutputDir.toString())
                        jobIds[format] = jobId
                        conversionResults[format] = mapOf(
                            "status" to "queued",
                            "jobId" to jobId
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to convert to $format", e)
                conversionResults[format] = mapOf(
                    "status" to "failed",
                    "error" to e.message
                )
            }
        }

        return ResponseEntity.ok(mapOf(
            "results" to conversionResults,
            "jobIds" to jobIds,
            "message" to "Conversion requests processed"
        ))
    }

    /**
     * Convert a single AsciiDoc file to EPUB
     *
     * @param sourceFile Path to the source AsciiDoc file
     * @param outputDir Output directory for the EPUB file
     * @return Job ID and status
     */
    @PostMapping("/epub/convert-file", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun convertSingleFileToEpub(
        @RequestParam("sourceFile") sourceFile: String,
        @RequestParam("outputDir") outputDir: String
    ): ResponseEntity<Map<String, Any>> {

        // Validate and sanitize the source file path
        val validatedSourcePath = validateAndSanitizePath(sourceFile)
        if (validatedSourcePath == null) {
            logger.warn("Path traversal attempt detected for source file: $sourceFile")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Invalid or unauthorized source file path"
            ))
        }

        // Validate and sanitize the output directory
        val validatedOutputDir = validateAndSanitizePath(outputDir)
        if (validatedOutputDir == null) {
            logger.warn("Path traversal attempt detected for output directory: $outputDir")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Invalid or unauthorized output directory path"
            ))
        }

        val sourceFileObj = validatedSourcePath.toFile()

        // Validate that the source file exists and is an AsciiDoc file
        if (!sourceFileObj.exists() || !sourceFileObj.isFile) {
            logger.error("Source file validation failed: $validatedSourcePath")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Source file does not exist or is not a file"
            ))
        }

        if (sourceFileObj.extension != "adoc") {
            logger.error("Source file is not an AsciiDoc file: $validatedSourcePath")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Source file must be an AsciiDoc file (.adoc extension)"
            ))
        }

        // Start the conversion job
        val jobId = conversionJobService.startSingleFileEpubConversion(sourceFileObj, validatedOutputDir.toString())
        logger.info("Started single file EPUB conversion job: $jobId for file: $validatedSourcePath")

        return ResponseEntity.ok(mapOf(
            "jobId" to jobId,
            "status" to "QUEUED",
            "message" to "Single file EPUB conversion job started",
            "sourceFile" to sourceFileObj.name,
            "outputDir" to validatedOutputDir.toString()
        ))
    }

    /**
     * Convert a single AsciiDoc file to EPUB synchronously
     *
     * @param sourceFile Path to the source AsciiDoc file
     * @param outputDir Output directory for the EPUB file
     * @return Conversion result with statistics
     */
    @PostMapping("/epub/convert-file-sync", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun convertSingleFileToEpubSync(
        @RequestParam("sourceFile") sourceFile: String,
        @RequestParam("outputDir") outputDir: String
    ): ResponseEntity<Map<String, Any>> {

        val startTime = System.currentTimeMillis()

        // Validate and sanitize the source file path
        val validatedSourcePath = validateAndSanitizePath(sourceFile)
        if (validatedSourcePath == null) {
            logger.warn("Path traversal attempt detected for source file: $sourceFile")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Invalid or unauthorized source file path"
            ))
        }

        // Validate and sanitize the output directory
        val validatedOutputDir = validateAndSanitizePath(outputDir)
        if (validatedOutputDir == null) {
            logger.warn("Path traversal attempt detected for output directory: $outputDir")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Invalid or unauthorized output directory path"
            ))
        }

        val sourceFileObj = validatedSourcePath.toFile()

        // Validate that the source file exists and is an AsciiDoc file
        if (!sourceFileObj.exists() || !sourceFileObj.isFile) {
            logger.error("Source file validation failed: $validatedSourcePath")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Source file does not exist or is not a file"
            ))
        }

        if (sourceFileObj.extension != "adoc") {
            logger.error("Source file is not an AsciiDoc file: $validatedSourcePath")
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Source file must be an AsciiDoc file (.adoc extension)"
            ))
        }

        try {
            // Convert the file synchronously
            val stats = convert.convertSingleFileToEpub(sourceFileObj, validatedOutputDir.toString())
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            logger.info("Single file EPUB conversion completed in ${duration}ms for file: ${sourceFileObj.name}")

            return ResponseEntity.ok(mapOf(
                "status" to if (stats.filesConverted > 0) "success" else "failed",
                "message" to "Single file EPUB conversion completed",
                "sourceFile" to sourceFileObj.name,
                "outputDir" to validatedOutputDir.toString(),
                "stats" to mapOf(
                    "filesConverted" to stats.filesConverted,
                    "filesFailed" to stats.filesFailed,
                    "failedFiles" to stats.failedFiles
                ),
                "duration" to "${duration}ms"
            ))

        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            logger.error("Single file EPUB conversion failed after ${duration}ms", e)
            return ResponseEntity.internalServerError().body(mapOf(
                "status" to "failed",
                "error" to "Conversion failed: ${e.message}",
                "sourceFile" to sourceFileObj.name,
                "duration" to "${duration}ms"
            ))
        }
    }

    /**
     * Enhanced unified conversion that supports single files and multiple formats
     */
    @PostMapping("/convert-file", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun convertSingleFileToMultipleFormats(
        @RequestParam("sourceFile") sourceFile: String,
        @RequestParam("outputDir") outputDir: String,
        @RequestParam("formats", defaultValue = "") formats: String,
        @RequestParam("cssTheme", defaultValue = "github-markdown-css.css") cssTheme: String
    ): ResponseEntity<Map<String, Any>> {

        // Validate paths
        val validatedSourcePath = validateAndSanitizePath(sourceFile)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Invalid source file path"))
        val validatedOutputDir = validateAndSanitizePath(outputDir)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Invalid output directory"))

        val sourceFileObj = validatedSourcePath.toFile()

        // Validate file
        if (!sourceFileObj.exists() || !sourceFileObj.isFile || sourceFileObj.extension != "adoc") {
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Source must be an existing AsciiDoc file (.adoc extension)"
            ))
        }

        // Parse and validate formats
        val requestedFormats = if (formats.isBlank()) {
            converterSettings.defaultFormats
        } else {
            formats.split(",").map { it.trim().lowercase() }
        }

        val supportedFormats = listOf("html", "pdf", "epub")
        val invalidFormats = requestedFormats - supportedFormats
        if (invalidFormats.isNotEmpty()) {
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Unsupported formats: $invalidFormats. Allowed: $supportedFormats"
            ))
        }

        val conversionResults = mutableMapOf<String, Any>()
        val jobIds = mutableMapOf<String, String>()

        // Process each format independently
        requestedFormats.forEach { format ->
            try {
                when (format) {
                    "html" -> {
                        // For HTML, we can convert directly (assuming you have a single file HTML method)
                        // Or you could create a temporary directory with just this file
                        val parentDir = sourceFileObj.parentFile
                        val stats = convert.convert(parentDir, validatedOutputDir.toString(), cssTheme = cssTheme)
                        conversionResults[format] = mapOf(
                            "status" to "completed",
                            "stats" to stats
                        )
                    }
                    "pdf" -> {
                        val jobId = conversionJobService.startPdfConversion(listOf(sourceFileObj), validatedOutputDir.toString())
                        jobIds[format] = jobId
                        conversionResults[format] = mapOf(
                            "status" to "queued",
                            "jobId" to jobId
                        )
                    }
                    "epub" -> {
                        val jobId = conversionJobService.startSingleFileEpubConversion(sourceFileObj, validatedOutputDir.toString())
                        jobIds[format] = jobId
                        conversionResults[format] = mapOf(
                            "status" to "queued",
                            "jobId" to jobId
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to convert single file to $format", e)
                conversionResults[format] = mapOf(
                    "status" to "failed",
                    "error" to e.message
                )
            }
        }

        return ResponseEntity.ok(mapOf(
            "results" to conversionResults,
            "jobIds" to jobIds,
            "sourceFile" to sourceFileObj.name,
            "message" to "Single file conversion requests processed"
        ))
    }
}
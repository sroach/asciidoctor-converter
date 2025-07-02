package gy.roach.asciidoctor.web

import gy.roach.asciidoctor.repo.GithubClient
import gy.roach.asciidoctor.service.AsciiDoctorConverter
import gy.roach.asciidoctor.service.ConversionStats
import gy.roach.asciidoctor.service.HtmlTemplateService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Controller for handling GitHub repository operations.
 * Provides endpoints for processing GitHub repositories and converting their content.
 */
@RestController
@RequestMapping("/github")
class GithubController(
    private val githubClient: GithubClient,
    private val mainController: MainController,
    private val convert: AsciiDoctorConverter,
    private val htmlTemplateService: HtmlTemplateService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Thread-safe map to track active conversions by repository name
    private val activeConversions = ConcurrentHashMap<String, ActiveConversion>()

    /**
     * Data class representing a GitHub repository request.
     */
    data class GithubRepositoryRequest(
        val repoName: String,
        val pat: String,
        val name: String,
        val gitUrl: String,
        val branch: String = "main"
    )

    /**
     * Process a GitHub repository and convert its content.
     * 
     * @param request The GitHub repository request
     * @return ResponseEntity with the conversion result
     */
    @PostMapping("/process")
    fun processRepository(@RequestBody request: GithubRepositoryRequest): ResponseEntity<String> {
        logger.info("Received request to process GitHub repository: ${request.repoName}")

        // Capture start time
        val startTime = System.currentTimeMillis()
        val startTimestamp = LocalDateTime.now()

        // Check if conversion is already in progress for this repository
        val existingConversion = activeConversions[request.repoName]

        if (existingConversion != null) {
            val conflictMessage = "Conversion already in progress for repository: ${request.repoName} (started at ${existingConversion.startTime})"
            logger.warn(conflictMessage)
            val duration = System.currentTimeMillis() - startTime

            // Record the failed execution
            mainController.recordExecution(
                startTimestamp, 
                "Repository: ${request.repoName}", 
                "N/A", 
                duration, 
                ConversionStats(), 
                false, 
                conflictMessage
            )

            return ResponseEntity.status(409) // HTTP 409 Conflict
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(htmlTemplateService.buildConflictResponseMessage(
                    conflictMessage, 
                    existingConversion, 
                    startTimestamp, 
                    duration
                ))
        }

        try {
            // Process the repository using GithubClient
            val result = githubClient.processRepository(
                repoName = request.repoName,
                pat = request.pat,
                name = request.name,
                gitUrl = request.gitUrl,
                branch = request.branch
            )

            logger.info("Repository processed successfully. Source directory: ${result.sourceDirectory}, Target directory: ${result.targetDirectory}")

            // Validate and sanitize the source directory
            val validatedSourceDir = mainController.validateAndSanitizePath(result.sourceDirectory)
            if (validatedSourceDir == null) {
                val errorMessage = "Invalid or unauthorized source directory path: ${result.sourceDirectory}"
                logger.warn(errorMessage)
                return ResponseEntity.badRequest().body(errorMessage)
            }

            // Validate and sanitize the output directory
            val validatedOutputDir = mainController.validateAndSanitizePath(result.targetDirectory)
            if (validatedOutputDir == null) {
                val errorMessage = "Invalid or unauthorized output directory path: ${result.targetDirectory}"
                logger.warn(errorMessage)
                return ResponseEntity.badRequest().body(errorMessage)
            }

            val localDirectory = validatedSourceDir.toFile()

            // Validate that the source directory exists and is a directory
            if (!localDirectory.exists() || !localDirectory.isDirectory) {
                val errorMessage = "Source directory does not exist or is not a directory: ${result.sourceDirectory}"
                logger.error(errorMessage)
                return ResponseEntity.badRequest().body(errorMessage)
            }

            // Generate execution ID for this conversion
            val executionId = UUID.randomUUID().toString()

            // Register this conversion as active
            val activeConversion = ActiveConversion(
                sourceDirectory = result.sourceDirectory,
                outputDirectory = result.targetDirectory,
                startTime = startTimestamp,
                executionId = executionId
            )

            activeConversions[request.repoName] = activeConversion
            logger.info("Started conversion for repository: ${request.repoName} (execution ID: $executionId)")

            try {
                // Convert files and get statistics
                val stats = convert.convert(localDirectory, validatedOutputDir.toString())

                // Calculate execution duration
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                // Record successful execution in MainController
                mainController.recordExecution(startTimestamp, result.sourceDirectory, result.targetDirectory, duration, stats, true)

                // Create a response message with the statistics including timing info
                val responseMessage = htmlTemplateService.buildResponseMessage(stats, startTimestamp, duration)

                logger.info("Conversion completed successfully for source: ${result.sourceDirectory} in ${duration}ms (execution ID: $executionId)")

                return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(responseMessage)

            } catch (e: Exception) {
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                // Record failed execution in MainController
                mainController.recordExecution(startTimestamp, result.sourceDirectory, result.targetDirectory, duration, ConversionStats(), false, e.message)

                val errorMessage = htmlTemplateService.buildErrorResponseMessage("Conversion failed: ${e.message}", startTimestamp, duration)
                logger.error("Conversion error after ${duration}ms (execution ID: $executionId)", e)
                return ResponseEntity.internalServerError()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(errorMessage)
            } finally {
                // Always remove the active conversion when done (success or failure)
                activeConversions.remove(request.repoName)
                logger.debug("Removed active conversion for repository: ${request.repoName} (execution ID: $executionId)")
            }

        } catch (e: Exception) {
            logger.error("Failed to process GitHub repository", e)
            return ResponseEntity.internalServerError()
                .body("Failed to process GitHub repository: ${e.message}")
        }
    }
}

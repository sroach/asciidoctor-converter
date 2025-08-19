package gy.roach.asciidoctor.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class ConversionJob(
    val id: String,
    var status: ConversionStatus,
    var progress: Int,
    var stats: ConversionStats? = null,
    var errorMessage: String? = null
)

enum class ConversionStatus {
    QUEUED, IN_PROGRESS, COMPLETED, FAILED
}

@Service
class ConversionJobService(private val asciiDoctorConverter: AsciiDoctorConverter) {
    private val logger = LoggerFactory.getLogger(ConversionJobService::class.java)
    private val jobs = ConcurrentHashMap<String, ConversionJob>()

    /**
     * Starts a PDF conversion job asynchronously
     * 
     * @param files List of files to convert
     * @param toDir Output directory
     * @return Job ID
     */
    fun startPdfConversion(files: List<File>, toDir: String): String {
        val jobId = UUID.randomUUID().toString()
        jobs[jobId] = ConversionJob(jobId, ConversionStatus.QUEUED, 0)

        // Update job status
        jobs[jobId]?.status = ConversionStatus.IN_PROGRESS

        // Start conversion
        val future = asciiDoctorConverter.convertToPdfAsync(files, toDir)

        // Create a monitoring thread
        val monitorThread = Thread {
            try {
                // Update progress periodically
                val totalFiles = files.size
                var lastProgress = 0

                while (!future.isDone) {
                    val currentStats = jobs[jobId]?.stats ?: ConversionStats()
                    val convertedFiles = currentStats.filesConverted + currentStats.filesFailed
                    val progress = if (totalFiles > 0) (convertedFiles * 100) / totalFiles else 0

                    if (progress > lastProgress) {
                        jobs[jobId]?.progress = progress
                        lastProgress = progress
                    }

                    Thread.sleep(500)
                }
            } catch (e: Exception) {
                logger.error("Error monitoring PDF conversion progress", e)
            }
        }
        monitorThread.isDaemon = true
        monitorThread.start()

        // Handle completion
        future.whenComplete { stats, throwable ->
            if (throwable != null) {
                logger.error("PDF conversion job failed: $jobId", throwable)
                jobs[jobId]?.apply {
                    this.status = ConversionStatus.FAILED
                    this.errorMessage = throwable.message
                }
            } else {
                // Update job with final status
                jobs[jobId]?.apply {
                    this.stats = stats
                    this.progress = 100
                    this.status = ConversionStatus.COMPLETED
                }
                logger.info("PDF conversion job completed: $jobId")
            }
        }

        return jobId
    }

    /**
     * Gets the status of a conversion job
     * 
     * @param jobId Job ID
     * @return ConversionJob or null if not found
     */
    fun getJobStatus(jobId: String): ConversionJob? = jobs[jobId]

    /**
     * Gets all conversion jobs
     * 
     * @return Map of job IDs to ConversionJobs
     */
    fun getAllJobs(): Map<String, ConversionJob> = jobs.toMap()

    fun startEpubConversion(files: List<File>, toDir: String): String {
        val jobId = UUID.randomUUID().toString()
        jobs[jobId] = ConversionJob(jobId, ConversionStatus.QUEUED, 0)

        jobs[jobId]?.status = ConversionStatus.IN_PROGRESS

        val future = asciiDoctorConverter.convertToEpubAsync(files, toDir)

        // Monitor and handle completion similar to PDF conversion
        future.whenComplete { stats, throwable ->
            if (throwable != null) {
                logger.error("EPUB conversion job failed: $jobId", throwable)
                jobs[jobId]?.apply {
                    this.status = ConversionStatus.FAILED
                    this.errorMessage = throwable.message
                }
            } else {
                jobs[jobId]?.apply {
                    this.stats = stats
                    this.progress = 100
                    this.status = ConversionStatus.COMPLETED
                }
                logger.info("EPUB conversion job completed: $jobId")
            }
        }

        return jobId
    }

    /**
     * Starts an EPUB conversion job for a single file asynchronously
     *
     * @param sourceFile Single file to convert
     * @param toDir Output directory
     * @return Job ID
     */
    fun startSingleFileEpubConversion(sourceFile: File, toDir: String): String {
        val jobId = UUID.randomUUID().toString()
        jobs[jobId] = ConversionJob(jobId, ConversionStatus.QUEUED, 0)

        jobs[jobId]?.status = ConversionStatus.IN_PROGRESS

        val future = asciiDoctorConverter.convertSingleFileToEpubAsync(sourceFile, toDir)

        // Handle completion
        future.whenComplete { stats, throwable ->
            if (throwable != null) {
                logger.error("Single file EPUB conversion job failed: $jobId", throwable)
                jobs[jobId]?.apply {
                    this.status = ConversionStatus.FAILED
                    this.errorMessage = throwable.message
                }
            } else {
                jobs[jobId]?.apply {
                    this.stats = stats
                    this.progress = 100
                    this.status = ConversionStatus.COMPLETED
                }
                logger.info("Single file EPUB conversion job completed: $jobId")
            }
        }

        return jobId
    }
}

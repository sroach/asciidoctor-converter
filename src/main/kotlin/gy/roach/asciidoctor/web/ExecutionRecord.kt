package gy.roach.asciidoctor.web

import gy.roach.asciidoctor.service.ConversionStats
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

data class ExecutionRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: LocalDateTime,
    val sourceDirectory: String,
    val outputDirectory: String,
    val durationMs: Long,
    val stats: ConversionStats,
    val success: Boolean,
    val errorMessage: String? = null
)

data class ExecutionSummary(
    val totalExecutions: AtomicInteger,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    val averageDurationMs: Long,
    val totalFilesConverted: Int,
    val totalFilesCopied: Int,
    val totalFilesDeleted: Int,
    val lastExecution: ExecutionRecord?
)

data class ActiveConversion(
    val sourceDirectory: String,
    val outputDirectory: String,
    val startTime: LocalDateTime,
    val executionId: String
)

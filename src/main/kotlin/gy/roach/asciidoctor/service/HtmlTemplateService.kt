package gy.roach.asciidoctor.service

import gy.roach.asciidoctor.web.ActiveConversion
import gy.roach.asciidoctor.web.ExecutionRecord
import gy.roach.asciidoctor.web.ExecutionSummary
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class HtmlTemplateService {

    fun buildResponseMessage(stats: ConversionStats, startTimestamp: LocalDateTime, durationMs: Long): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
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

    fun buildErrorResponseMessage(errorMessage: String, startTimestamp: LocalDateTime, durationMs: Long): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
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

    fun buildStatsResponseMessage(summary: ExecutionSummary, history: List<ExecutionRecord>): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

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
                .source-directory {
                    grid-column: 1 / -1;
                    background: #edf2f7;
                    border-radius: 6px;
                    padding: 10px;
                    margin-top: 10px;
                    font-family: monospace;
                    font-size: 12px;
                    color: #4a5568;
                    word-break: break-all;
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
                                    <span class="execution-id"><a href="${execution.id}/html">${execution.id}</a></span>
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
                                <div class="source-directory">
                                    📁 Source: ${execution.sourceDirectory}
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
    fun buildConflictResponseMessage(
        conflictMessage: String,
        existingConversion: ActiveConversion,
        startTimestamp: LocalDateTime,
        durationMs: Long
    ): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formattedTimestamp = startTimestamp.format(formatter)
        val formattedExistingStart = existingConversion.startTime.format(formatter)
        val durationFormatted = formatDuration(durationMs)

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Conversion Conflict</title>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
                    background: linear-gradient(135deg, #ed8936 0%, #dd6b20 100%);
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
                .conflict-icon {
                    font-size: 48px;
                    margin-bottom: 20px;
                }
                .conflict-title {
                    color: #c05621;
                    font-size: 24px;
                    font-weight: 600;
                    margin-bottom: 15px;
                }
                .conflict-message {
                    color: #4a5568;
                    font-size: 16px;
                    line-height: 1.5;
                    margin-bottom: 20px;
                }
                .existing-conversion-info {
                    background: #fef5e7;
                    border-radius: 12px;
                    padding: 20px;
                    margin: 20px 0;
                    border-left: 4px solid #ed8936;
                }
                .info-item {
                    display: flex;
                    justify-content: space-between;
                    margin-bottom: 10px;
                    font-size: 14px;
                }
                .info-label {
                    color: #718096;
                    font-weight: 500;
                }
                .info-value {
                    color: #2d3748;
                    font-weight: 600;
                    font-family: monospace;
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
                .retry-info {
                    background: #edf2f7;
                    border-radius: 8px;
                    padding: 15px;
                    margin-top: 20px;
                    font-size: 14px;
                    color: #4a5568;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="conflict-icon">⚠️</div>
                <div class="conflict-title">Conversion Already In Progress</div>
                <div class="conflict-message">
                    A conversion is currently running for the requested source directory. 
                    Please wait for it to complete before starting a new conversion.
                </div>
                
                <div class="existing-conversion-info">
                    <div class="info-item">
                        <span class="info-label">Execution ID:</span>
                        <span class="info-value">${existingConversion.executionId}</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">Started At:</span>
                        <span class="info-value">$formattedExistingStart</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">Source Directory:</span>
                        <span class="info-value">${existingConversion.sourceDirectory}</span>
                    </div>
                    <div class="info-item">
                        <span class="info-label">Output Directory:</span>
                        <span class="info-value">${existingConversion.outputDirectory}</span>
                    </div>
                </div>
                
                <div class="timing-info">
                    <div class="timing-item">
                        <div class="timing-label">🕐 Your Request Time</div>
                        <div class="timing-value">$formattedTimestamp</div>
                    </div>
                    <div class="timing-item">
                        <div class="timing-label">⏱️ Response Time</div>
                        <div class="timing-value">$durationFormatted</div>
                    </div>
                </div>
                
                <div class="retry-info">
                    💡 <strong>Tip:</strong> You can check active conversions at 
                    <code>/api/active-conversions</code> or retry your request once the current conversion completes.
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    fun buildActiveConversionsResponseMessage(activeConversions: List<ActiveConversion>): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Active Conversions</title>
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
                    max-width: 1000px;
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
                .count-badge {
                    background: linear-gradient(45deg, #48bb78, #38a169);
                    color: white;
                    padding: 8px 16px;
                    border-radius: 20px;
                    font-size: 14px;
                    font-weight: 500;
                    display: inline-block;
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
                    margin-left: 10px;
                }
                .refresh-button:hover {
                    background: linear-gradient(45deg, #3182ce, #2c5aa0);
                }
                .conversion-item {
                    background: #f7fafc;
                    border-radius: 12px;
                    padding: 20px;
                    margin-bottom: 15px;
                    border-left: 4px solid #ed8936;
                }
                .conversion-header {
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
                .start-time {
                    color: #4a5568;
                    font-size: 14px;
                }
                .conversion-details {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 15px;
                    font-size: 14px;
                    color: #4a5568;
                }
                .detail-item {
                    display: flex;
                    flex-direction: column;
                }
                .detail-label {
                    color: #718096;
                    font-size: 12px;
                    font-weight: 500;
                    margin-bottom: 4px;
                }
                .detail-value {
                    color: #2d3748;
                    font-family: monospace;
                    font-size: 13px;
                    word-break: break-all;
                }
                .no-active {
                    text-align: center;
                    color: #718096;
                    padding: 40px;
                    font-size: 16px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>🔄 Active Conversions</h1>
                    <span class="count-badge">${activeConversions.size} Active</span>
                    <button class="refresh-button" onclick="location.reload()">🔄 Refresh</button>
                </div>
                
                ${if (activeConversions.isEmpty()) """
                    <div class="no-active">
                        No conversions currently in progress
                    </div>
                """ else activeConversions.joinToString("") { conversion ->
            """
                    <div class="conversion-item">
                        <div class="conversion-header">
                            <div class="execution-id">ID: ${conversion.executionId}</div>
                            <div class="start-time">Started: ${conversion.startTime.format(formatter)}</div>
                        </div>
                        <div class="conversion-details">
                            <div class="detail-item">
                                <div class="detail-label">Source Directory</div>
                                <div class="detail-value">${conversion.sourceDirectory}</div>
                            </div>
                            <div class="detail-item">
                                <div class="detail-label">Output Directory</div>
                                <div class="detail-value">${conversion.outputDirectory}</div>
                            </div>
                        </div>
                    </div>
                    """
        }}
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    fun buildExecutionDetailsResponseMessage(execution: ExecutionRecord): String {
        return buildResponseMessage(execution.stats, execution.timestamp, execution.durationMs)
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
}
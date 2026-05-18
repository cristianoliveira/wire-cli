package wirecli.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Formatter for sync status and diagnostics output.
 * Supports human-readable and JSON output modes.
 */
@Suppress("TooManyFunctions")
object SyncOutputFormatter {
    private const val HIGH_LAG_THRESHOLD_MS = 5000
    private const val LARGE_BACKLOG_THRESHOLD = 100
    private const val MLS_MIGRATION_THRESHOLD_PCT = 50

    private val json = Json { prettyPrint = true }

    fun formatStatusHuman(result: SyncStatusResult): String =
        when (result) {
            is SyncStatusResult.Success -> formatSuccessStatus(result.view)
            is SyncStatusResult.Failure -> result.message
        }

    fun formatStatusVerbose(result: SyncStatusResult): String =
        when (result) {
            is SyncStatusResult.Success -> formatVerboseStatus(result.view)
            is SyncStatusResult.Failure -> result.message
        }

    fun formatStatusJson(result: SyncStatusResult): String =
        when (result) {
            is SyncStatusResult.Success ->
                json.encodeToString(
                    StatusJsonOutput.Success(
                        status = result.view.status.value,
                        auth = result.view.metrics.authStatus,
                        encryption = result.view.metrics.encryptionStatus,
                        metrics =
                            MetricsJson(
                                lagMs = result.view.metrics.lagMs,
                                pendingMessages = result.view.metrics.pendingMessages,
                                mlsPct = result.view.metrics.mlsPct,
                                timestamp = result.view.metrics.timestamp,
                                lastMessageReceivedMs = result.view.metrics.lastMessageReceivedMs,
                            ),
                        uptimeMs = result.view.metrics.uptimeMs,
                    ),
                )
            is SyncStatusResult.Failure ->
                json.encodeToString(
                    StatusJsonOutput.Failure(
                        error = result.message,
                        exitCode = result.exitCode,
                    ),
                )
        }

    fun formatDiagnosticsHuman(result: DiagnosticsResult): String =
        when (result) {
            is DiagnosticsResult.Success -> formatSuccessDiagnostics(result.report)
            is DiagnosticsResult.Failure -> result.message
        }

    fun formatDiagnosticsJson(result: DiagnosticsResult): String =
        when (result) {
            is DiagnosticsResult.Success -> {
                val jsonOutput =
                    DiagnosticsJsonOutput.Success(
                        summary = result.report.summary,
                        checks =
                            result.report.checks.map { check ->
                                CheckJson(
                                    name = check.name,
                                    status = check.status,
                                    details = check.details,
                                )
                            },
                        recoveryHints =
                            result.report.recoveryHints.map { hint ->
                                RecoveryHintJson(
                                    description = hint.description,
                                    command = hint.command,
                                )
                            },
                    )
                json.encodeToString(jsonOutput)
            }
            is DiagnosticsResult.Failure ->
                json.encodeToString(
                    DiagnosticsJsonOutput.Failure(
                        error = result.message,
                        exitCode = result.exitCode,
                    ),
                )
        }

    private fun formatSuccessStatus(view: SyncStatusView): String =
        buildString {
            val statusIcon = getStatusIcon(view.status.value)
            appendLine("$statusIcon Account Health: ${view.status}")
            appendLine("")
            appendLine("  Auth: ${formatAuthStatus(view.metrics.authStatus)}")
            appendLine("  Encryption: ${formatEncryptionStatus(view.metrics.encryptionStatus, view.metrics.mlsPct)}")
            appendLine("")
            appendLine("  Lag: ${view.metrics.lagMs}ms")
            appendLine("  Pending: ${view.metrics.pendingMessages} messages")
            appendLine("  MLS: ${view.metrics.mlsPct}%")
            appendLine("  Last sync: ${view.metrics.timestamp}")
        }

    private fun formatVerboseStatus(view: SyncStatusView): String =
        buildString {
            val statusIcon = getStatusIcon(view.status.value)
            appendLine("$statusIcon Account Health: ${view.status}")
            appendLine("")
            appendHealthMetrics(view.metrics)
            appendMlsDetails(view.metrics.mls)
            appendRecoveryActions(view.diagnosticsReport)
            val interpretation = buildInterpretation(view.metrics)
            appendLine(interpretation)
        }

    private fun StringBuilder.appendHealthMetrics(metrics: HealthMetrics) {
        appendLine("Health Metrics:")
        appendLine("  • Event Queue Lag: ${metrics.lagMs}ms")
        if (metrics.lastMessageReceivedMs != null) {
            val secondsAgo = (System.currentTimeMillis() - metrics.lastMessageReceivedMs) / 1000
            appendLine("  • Messages: ${metrics.pendingMessages} pending (last received ${secondsAgo}s ago)")
        } else {
            appendLine("  • Pending Messages: ${metrics.pendingMessages}")
        }
        appendLine("  • MLS Migration: ${metrics.mlsPct}% complete")
        appendLine("  • Last Sync: ${metrics.timestamp}")
    }

    private fun StringBuilder.appendMlsDetails(mlsMetrics: MLSMetrics?) {
        if (mlsMetrics == null) return
        appendLine("")
        val keyPackageStatus =
            when {
                mlsMetrics.keyPackageExhausted -> "exhausted, refresh needed"
                mlsMetrics.keyPackageAvailable < 50 -> "low, refilling"
                else -> "${mlsMetrics.keyPackageAvailable} available"
            }
        if (mlsMetrics.deviceName != null && mlsMetrics.keyPackageTotal != null) {
            val keyPkgInfo =
                "  • ${mlsMetrics.deviceName}: ${mlsMetrics.keyPackageAvailable}/${mlsMetrics.keyPackageTotal} ($keyPackageStatus)"
            appendLine(keyPkgInfo)
        } else {
            appendLine("  • Key Packages: $keyPackageStatus")
        }
        if (mlsMetrics.estimatedRemainingMs != null) {
            val secondsRemaining = mlsMetrics.estimatedRemainingMs / 1000
            appendLine("  • estimated: ${secondsRemaining}s remaining")
        }
    }

    private fun StringBuilder.appendRecoveryActions(diagnosticsReport: DiagnosticsReport?) {
        if (diagnosticsReport?.recoveryHints?.isEmpty() != false) return
        appendLine("")
        appendLine("Recovery Actions:")
        diagnosticsReport.recoveryHints.forEach { hint ->
            appendLine("  • ${hint.description}")
            appendLine("    Command: ${hint.command}")
        }
        appendLine("")
    }

    private fun buildInterpretation(metrics: HealthMetrics): String =
        when {
            metrics.lagMs > HIGH_LAG_THRESHOLD_MS -> "⚠ High lag detected. Sync may be slow."
            metrics.pendingMessages > LARGE_BACKLOG_THRESHOLD -> "⚠ Large message backlog detected."
            metrics.mlsPct < MLS_MIGRATION_THRESHOLD_PCT -> "⚠ MLS migration is still in progress."
            else -> "✓ All metrics are healthy."
        }

    private fun formatSuccessDiagnostics(report: DiagnosticsReport): String =
        buildString {
            appendLine("📋 Diagnostics Report")
            appendLine("────────────────────────────────────")
            appendLine("")
            appendLine("Summary: ${report.summary}")
            appendLine("")
            appendLine("Health Checks:")
            report.checks.forEach { check ->
                val icon = getStatusIcon(check.status)
                appendLine("  $icon ${check.name}")
                appendLine("     Status: ${check.status}")
                appendLine("     ${check.details}")
            }

            if (report.recoveryHints.isNotEmpty()) {
                appendLine("")
                appendLine("Recovery Actions:")
                report.recoveryHints.forEach { hint ->
                    appendLine("  • ${hint.description}")
                    appendLine("    Command: ${hint.command}")
                }
            }

            // Add summary message
            appendLine("")
            val passedChecks = report.checks.count { it.status in listOf("ok", "healthy", "ready") }
            val totalChecks = report.checks.size
            val allPassed = report.checks.all { it.status !in listOf("error", "fail", "degraded") }
            val summaryIcon = if (allPassed) "✓" else "⚠"
            if (allPassed) {
                appendLine("Diagnosis complete: All $totalChecks checks passed $summaryIcon")
            } else {
                appendLine("Diagnosis complete: $passedChecks/$totalChecks checks passed, issues detected ⚠")
            }
        }

    private fun getStatusIcon(status: String): String =
        when (status) {
            "healthy", "ready", "ok" -> "✓"
            "initializing" -> "⟳"
            "degraded", "warning" -> "⚠"
            "error", "fail" -> "✗"
            else -> "?"
        }

    private fun formatAuthStatus(authStatus: String): String =
        when (authStatus.lowercase()) {
            "ok", "connected", "authenticated" -> "✓ Connected"
            "not_authenticated", "disconnected" -> "✗ Not authenticated"
            else -> "? Unknown ($authStatus)"
        }

    private fun formatEncryptionStatus(
        encryptionStatus: String,
        mlsPct: Int,
    ): String =
        when (encryptionStatus.lowercase()) {
            "ready" -> "✓ Ready"
            "pending" -> "⟳ Pending ($mlsPct% complete)"
            else -> "? Unknown ($encryptionStatus)"
        }
}

// JSON Output types
@Serializable
sealed class StatusJsonOutput {
    @Serializable
    data class Success(
        val status: String,
        val auth: String,
        val encryption: String,
        val metrics: MetricsJson,
        val uptimeMs: Long? = null,
    ) : StatusJsonOutput()

    @Serializable
    data class Failure(
        val error: String,
        val exitCode: Int,
    ) : StatusJsonOutput()
}

@Serializable
data class MetricsJson(
    val lagMs: Long,
    val pendingMessages: Int,
    val mlsPct: Int,
    val timestamp: String,
    val lastMessageReceivedMs: Long? = null,
)

@Serializable
sealed class DiagnosticsJsonOutput {
    @Serializable
    data class Success(
        val summary: String,
        val checks: List<CheckJson>,
        val recoveryHints: List<RecoveryHintJson>,
    ) : DiagnosticsJsonOutput()

    @Serializable
    data class Failure(
        val error: String,
        val exitCode: Int,
    ) : DiagnosticsJsonOutput()
}

@Serializable
data class CheckJson(
    val name: String,
    val status: String,
    val details: String,
)

@Serializable
data class RecoveryHintJson(
    val description: String,
    val command: String,
)

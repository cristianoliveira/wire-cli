package wirecli.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Formatter for sync status and diagnostics output.
 * Supports human-readable and JSON output modes.
 */
object SyncOutputFormatter {
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
                        auth = result.view.metrics.auth_status,
                        encryption = result.view.metrics.encryption_status,
                        metrics =
                            MetricsJson(
                                lag_ms = result.view.metrics.lag_ms,
                                pending_messages = result.view.metrics.pending_messages,
                                mls_pct = result.view.metrics.mls_pct,
                                timestamp = result.view.metrics.timestamp,
                                last_message_received_ms = result.view.metrics.last_message_received_ms,
                            ),
                        uptime_ms = result.view.metrics.uptime_ms,
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
            appendLine("  Auth: ${formatAuthStatus(view.metrics.auth_status)}")
            appendLine("  Encryption: ${formatEncryptionStatus(view.metrics.encryption_status, view.metrics.mls_pct)}")
            appendLine("")
            appendLine("  Lag: ${view.metrics.lag_ms}ms")
            appendLine("  Pending: ${view.metrics.pending_messages} messages")
            appendLine("  MLS: ${view.metrics.mls_pct}%")
            appendLine("  Last sync: ${view.metrics.timestamp}")
        }

    private fun formatVerboseStatus(view: SyncStatusView): String =
        buildString {
            val statusIcon = getStatusIcon(view.status.value)
            appendLine("$statusIcon Account Health: ${view.status}")
            appendLine("")
            appendLine("Health Metrics:")
            appendLine("  • Event Queue Lag: ${view.metrics.lag_ms}ms")
            if (view.metrics.last_message_received_ms != null) {
                val secondsAgo = (System.currentTimeMillis() - view.metrics.last_message_received_ms) / 1000
                appendLine("  • Messages: ${view.metrics.pending_messages} pending (last received ${secondsAgo}s ago)")
            } else {
                appendLine("  • Pending Messages: ${view.metrics.pending_messages}")
            }
            appendLine("  • MLS Migration: ${view.metrics.mls_pct}% complete")
            appendLine("  • Last Sync: ${view.metrics.timestamp}")

            // Add MLS details if available
            if (view.metrics.mls != null) {
                appendLine("")
                val mlsMetrics = view.metrics.mls
                val keyPackageStatus =
                    when {
                        mlsMetrics.key_package_exhausted -> "exhausted, refresh needed"
                        mlsMetrics.key_package_available < 50 -> "low, refilling"
                        else -> "${mlsMetrics.key_package_available} available"
                    }
                if (mlsMetrics.device_name != null && mlsMetrics.key_package_total != null) {
                    val keyPackageInfo =
                        "  • ${mlsMetrics.device_name}: ${mlsMetrics.key_package_available}/${mlsMetrics.key_package_total} ($keyPackageStatus)"
                    appendLine(keyPackageInfo)
                } else {
                    appendLine("  • Key Packages: $keyPackageStatus")
                }
                if (mlsMetrics.estimated_remaining_ms != null) {
                    val secondsRemaining = mlsMetrics.estimated_remaining_ms / 1000
                    appendLine("  • estimated: ${secondsRemaining}s remaining")
                }
            }
            appendLine("")

            // Add recovery hints if available
            if (view.diagnosticsReport != null && view.diagnosticsReport.recoveryHints.isNotEmpty()) {
                appendLine("Recovery Actions:")
                view.diagnosticsReport.recoveryHints.forEach { hint ->
                    appendLine("  • ${hint.description}")
                    appendLine("    Command: ${hint.command}")
                }
                appendLine("")
            }

            // Add interpretation
            val interpretation =
                when {
                    view.metrics.lag_ms > 5000 -> "⚠ High lag detected. Sync may be slow."
                    view.metrics.pending_messages > 100 -> "⚠ Large message backlog detected."
                    view.metrics.mls_pct < 50 -> "⚠ MLS migration is still in progress."
                    else -> "✓ All metrics are healthy."
                }
            appendLine(interpretation)
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
        val uptime_ms: Long? = null,
    ) : StatusJsonOutput()

    @Serializable
    data class Failure(
        val error: String,
        val exitCode: Int,
    ) : StatusJsonOutput()
}

@Serializable
data class MetricsJson(
    val lag_ms: Long,
    val pending_messages: Int,
    val mls_pct: Int,
    val timestamp: String,
    val last_message_received_ms: Long? = null,
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

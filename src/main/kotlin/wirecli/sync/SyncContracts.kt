package wirecli.sync

import wirecli.auth.AuthSession

enum class SyncStatus(val value: String) {
    READY("ready"),
    INITIALIZING("initializing"),
    DEGRADED("degraded"),
    ERROR("error"),
    ;

    override fun toString(): String = value
}

data class HealthMetrics(
    val lag_ms: Long,
    val pending_messages: Int,
    val mls_pct: Int,
    val timestamp: String,
)

data class Check(
    val name: String,
    val status: String,
    val details: String,
)

data class RecoveryHint(
    val description: String,
    val command: String,
)

data class DiagnosticsReport(
    val checks: List<Check>,
    val summary: String,
    val recoveryHints: List<RecoveryHint> = emptyList(),
)

data class SyncStatusView(
    val status: SyncStatus,
    val metrics: HealthMetrics,
)

sealed interface SyncStatusResult {
    data class Success(val view: SyncStatusView) : SyncStatusResult

    data class Failure(val message: String, val exitCode: Int) : SyncStatusResult
}

sealed interface DiagnosticsResult {
    data class Success(val report: DiagnosticsReport) : DiagnosticsResult

    data class Failure(val message: String, val exitCode: Int) : DiagnosticsResult
}

interface SyncApiClient {
    fun getSyncStatus(session: AuthSession): SyncStatusResult

    fun getDiagnostics(session: AuthSession): DiagnosticsResult
}

interface SyncService {
    fun getCurrentSyncStatus(): SyncStatusResult

    fun getDiagnosticsReport(): DiagnosticsResult
}

object SyncExitCodes {
    const val OK = 0
    const val DEGRADED = 1
    const val UNAUTHORIZED = 11
    const val SERVER_ERROR = 13
}

internal object SyncMessages {
    const val NETWORK_FAILURE = "Sync status fetch failed: network is unreachable. Check your connection and retry."
    const val SERVER_FAILURE = "Sync service is unavailable. Retry later or check server settings."
    const val UNKNOWN_FAILURE = "Sync status fetch failed unexpectedly. Retry and check your setup."
    const val UNAUTHORIZED_FAILURE = "Your session is invalid or expired. Please log in again."

    const val DIAGNOSTICS_NETWORK_FAILURE = "Diagnostics fetch failed: network is unreachable. Check your connection and retry."
    const val DIAGNOSTICS_SERVER_FAILURE = "Diagnostics service is unavailable. Retry later or check server settings."
    const val DIAGNOSTICS_UNKNOWN_FAILURE = "Diagnostics fetch failed unexpectedly. Retry and check your setup."
}

package wirecli.sync

import wirecli.auth.AuthSession

// ==================== NETWORK METRICS ====================

/**
 * Represents the type of network connection detected.
 */
enum class NetworkType(val value: String) {
    WIFI("wifi"),
    CELLULAR("cellular"),
    WIRED("wired"),
    UNKNOWN("unknown"),
    DISCONNECTED("disconnected"),
    ;

    override fun toString(): String = value
}

/**
 * Network connectivity and quality metrics.
 *
 * Tracks:
 * - Connection status and type
 * - Network latency measurements
 * - Connection stability (error rates, recovery time)
 * - Reachability checks
 */
data class NetworkMetrics(
    val connected: Boolean,
    val network_type: NetworkType,
    val estimated_latency_ms: Long,
    val error_rate: Double,
    val last_recovery_time_ms: Long?,
    val reachability_check_timestamp: String,
)

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
    val network: NetworkMetrics? = null,
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

    /**
     * Retrieves sync status for a specific conversation.
     *
     * @param session The authenticated session
     * @param conversationId The ID of the conversation to query
     * @return Sync status and metrics for the conversation
     */
    fun getConversationSyncStatus(session: AuthSession, conversationId: String): ConversationSyncStatusResult

    /**
     * Retrieves detailed diagnostics for a conversation's sync status.
     *
     * @param session The authenticated session
     * @param conversationId The ID of the conversation to diagnose
     * @return Detailed diagnostics report with checks and recovery hints
     */
    fun getPerConversationDiagnostics(session: AuthSession, conversationId: String): PerConversationDiagnosticsResult
}

// ==================== PER-CONVERSATION SYNC DIAGNOSTICS ====================

data class ConversationMetrics(
    val conversation_id: String,
    val lag_ms: Long,
    val pending_messages: Int,
    val sync_completeness_pct: Int,
    val timestamp: String,
    val network: NetworkMetrics? = null,
)

data class ConversationSyncStatus(
    val conversation_id: String,
    val status: SyncStatus,
    val metrics: ConversationMetrics,
    val last_sync_timestamp: String,
)

data class PerConversationDiagnosticsReport(
    val conversation_id: String,
    val checks: List<Check>,
    val summary: String,
    val recoveryHints: List<RecoveryHint> = emptyList(),
)

sealed interface ConversationSyncStatusResult {
    data class Success(val status: ConversationSyncStatus) : ConversationSyncStatusResult

    data class Failure(val message: String, val exitCode: Int) : ConversationSyncStatusResult
}

sealed interface PerConversationDiagnosticsResult {
    data class Success(val report: PerConversationDiagnosticsReport) : PerConversationDiagnosticsResult

    data class Failure(val message: String, val exitCode: Int) : PerConversationDiagnosticsResult
}

interface SyncService {
    fun getCurrentSyncStatus(): SyncStatusResult

    fun getDiagnosticsReport(): DiagnosticsResult

    /**
     * Retrieves sync status for a specific conversation.
     *
     * @param conversationId The ID of the conversation to query
     * @return Sync status and metrics for the conversation, or failure if not found
     */
    fun getConversationSyncStatus(conversationId: String): ConversationSyncStatusResult

    /**
     * Retrieves detailed diagnostics for a specific conversation's sync status.
     *
     * @param conversationId The ID of the conversation to diagnose
     * @return Detailed diagnostics report with checks and recovery hints
     */
    fun getPerConversationDiagnostics(conversationId: String): PerConversationDiagnosticsResult
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

    // Per-conversation sync diagnostics messages
    const val CONVERSATION_NOT_FOUND = "Conversation not found. Verify the conversation ID and retry."
    const val CONVERSATION_SYNC_NETWORK_FAILURE = "Failed to fetch conversation sync status: network is unreachable."
    const val CONVERSATION_SYNC_SERVER_FAILURE = "Failed to fetch conversation sync status: server error occurred."
    const val CONVERSATION_SYNC_UNKNOWN_FAILURE = "Failed to fetch conversation sync status unexpectedly."
}

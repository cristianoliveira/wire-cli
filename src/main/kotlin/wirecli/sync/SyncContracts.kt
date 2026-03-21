package wirecli.sync

import wirecli.auth.AuthSession
import wirecli.shared.SyncResult

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

// ==================== MLS METRICS ====================

/**
 * Represents the status of MLS key package availability.
 */
enum class KeyPackageAvailabilityStatus(val value: String) {
    AVAILABLE("available"),
    LOW("low"),
    EXHAUSTED("exhausted"),
    UNAVAILABLE("unavailable"),
    ;

    override fun toString(): String = value
}

/**
 * Detailed MLS (Message Layer Security) metrics and status.
 *
 * Tracks:
 * - MLS enrollment status and percentage
 * - Key package availability
 * - Key package generation status
 * - MLS group update status
 * - Error rates specific to MLS operations
 */
data class MLSMetrics(
    val enrollment_pct: Int,
    val key_package_available: Int,
    val key_package_exhausted: Boolean,
    val key_package_generation_enabled: Boolean,
    val key_package_refresh_required: Boolean,
    val mls_group_updates_failed_count: Int,
    val mls_enrollment_failures_count: Int,
    val mls_error_rate: Double,
    val last_key_package_refresh_timestamp: String?,
    val timestamp: String,
    val estimated_remaining_ms: Long? = null,
    val device_name: String? = null,
    val key_package_total: Int? = null,
)

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
    val mls: MLSMetrics? = null,
    val last_message_received_ms: Long? = null,
    val auth_status: String = "ok",
    val encryption_status: String = "ready",
    val uptime_ms: Long? = null,
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
    val diagnosticsReport: DiagnosticsReport? = null,
)

typealias SyncStatusResult = SyncResult<SyncStatusView>
typealias DiagnosticsResult = SyncResult<DiagnosticsReport>
typealias ResetResult = SyncResult<String>

interface SyncStatusApiClient {
    fun getSyncStatus(session: AuthSession): SyncStatusResult

    /**
     * Retrieves sync status for a specific conversation.
     *
     * @param session The authenticated session
     * @param conversationId The ID of the conversation to query
     * @return Sync status and metrics for the conversation
     */
    fun getConversationSyncStatus(
        session: AuthSession,
        conversationId: String,
    ): ConversationSyncStatusResult
}

interface SyncDiagnosticsApiClient {
    fun getDiagnostics(session: AuthSession): DiagnosticsResult

    /**
     * Retrieves detailed diagnostics for a conversation's sync status.
     *
     * @param session The authenticated session
     * @param conversationId The ID of the conversation to diagnose
     * @return Detailed diagnostics report with checks and recovery hints
     */
    fun getPerConversationDiagnostics(
        session: AuthSession,
        conversationId: String,
    ): PerConversationDiagnosticsResult
}

interface SyncControlApiClient {
    fun forceSyncAndWait(session: AuthSession): SyncStatusResult

    fun resetSync(
        session: AuthSession,
        force: Boolean = false,
    ): ResetResult
}

interface SyncApiClient : SyncStatusApiClient, SyncDiagnosticsApiClient, SyncControlApiClient

// ==================== PER-CONVERSATION SYNC DIAGNOSTICS ====================

data class ConversationMetrics(
    val conversation_id: String,
    val lag_ms: Long,
    val pending_messages: Int,
    val sync_completeness_pct: Int,
    val timestamp: String,
    val network: NetworkMetrics? = null,
    val mls: MLSMetrics? = null,
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

typealias ConversationSyncStatusResult = SyncResult<ConversationSyncStatus>
typealias PerConversationDiagnosticsResult = SyncResult<PerConversationDiagnosticsReport>

interface SyncService {
    fun getCurrentSyncStatus(): SyncStatusResult

    fun getDiagnosticsReport(): DiagnosticsResult

    fun resetSync(force: Boolean = false): ResetResult

    fun forceSyncAndWait(): SyncStatusResult

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
    const val PERMISSION_ERROR = 15
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

    // MLS-specific messages for recovery hints
    const val MLS_LOW_KEY_PACKAGES = "Low on key packages. Refresh key packages to maintain MLS availability."
    const val MLS_EXHAUSTED_KEY_PACKAGES = "Key packages exhausted. Immediate key package refresh required."
    const val MLS_ENROLLMENT_FAILURES = "MLS enrollment failures detected. Check network and retry."
    const val MLS_GROUP_UPDATE_FAILURES = "MLS group update failures detected. May impact group conversations."
}

package wirecli.sync

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.shared.Result.Success
import wirecli.shared.Result.Failure

class StubSyncApiClient(
    private val environment: Map<String, String>,
) : SyncApiClient {
    override fun forceSyncAndWait(session: AuthSession): SyncStatusResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "sync_wait_timeout" ->
                Failure(
                    message = "Timed out waiting for sync to reach live state after force sync.",
                    exitCode = SyncExitCodes.DEGRADED,
                )

            "sync_wait_error", "network_error" ->
                Failure(
                    message = SyncMessages.NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "sync_wait_unauthorized", "unauthorized" ->
                Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else ->
                Success(
                    view =
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = defaultHealthMetrics,
                        ),
                )
        }
    }

    private val defaultHealthMetrics =
        HealthMetrics(
            lag_ms = 100L,
            pending_messages = 5,
            mls_pct = 85,
            timestamp = "2025-03-13T10:30:00Z",
        )

    private val defaultDegradedMetrics =
        HealthMetrics(
            lag_ms = 5000L,
            pending_messages = 250,
            mls_pct = 45,
            timestamp = "2025-03-13T10:35:00Z",
        )

    private val defaultErrorMetrics =
        HealthMetrics(
            lag_ms = 30000L,
            pending_messages = 1000,
            mls_pct = 10,
            timestamp = "2025-03-13T10:40:00Z",
        )

    override fun getSyncStatus(session: AuthSession): SyncStatusResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "status_ready" ->
                Success(
                    view =
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = defaultHealthMetrics,
                        ),
                )

            "status_initializing" ->
                Success(
                    view =
                        SyncStatusView(
                            status = SyncStatus.INITIALIZING,
                            metrics =
                                HealthMetrics(
                                    lag_ms = 2000L,
                                    pending_messages = 100,
                                    mls_pct = 20,
                                    timestamp = "2025-03-13T10:32:00Z",
                                ),
                        ),
                )

            "status_degraded" ->
                Success(
                    view =
                        SyncStatusView(
                            status = SyncStatus.DEGRADED,
                            metrics = defaultDegradedMetrics,
                        ),
                )

            "status_error" ->
                Success(
                    view =
                        SyncStatusView(
                            status = SyncStatus.ERROR,
                            metrics = defaultErrorMetrics,
                        ),
                )

            "network_error" ->
                Failure(
                    message = SyncMessages.NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "server_error" ->
                Failure(
                    message = SyncMessages.SERVER_FAILURE,
                    exitCode = SyncExitCodes.SERVER_ERROR,
                )

            "unauthorized" ->
                Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else ->
                Success(
                    view =
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = defaultHealthMetrics,
                        ),
                )
        }
    }

    override fun getDiagnostics(session: AuthSession): DiagnosticsResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "status_ready", "diagnostics_healthy" ->
                Success(
                    report =
                        DiagnosticsReport(
                            checks =
                                listOf(
                                    Check(
                                        name = "Authentication",
                                        status = "healthy",
                                        details = "Session is valid and authenticated",
                                    ),
                                    Check(
                                        name = "Sync Engine",
                                        status = "healthy",
                                        details = "Sync engine is running and responsive",
                                    ),
                                    Check(
                                        name = "Event Queue",
                                        status = "healthy",
                                        details = "Event queue is processing normally (lag: 100ms)",
                                    ),
                                    Check(
                                        name = "Key Packages",
                                        status = "healthy",
                                        details = "Key packages available: 42 remaining",
                                    ),
                                    Check(
                                        name = "Network Connectivity",
                                        status = "healthy",
                                        details = "Network connection is stable and responsive",
                                    ),
                                ),
                            summary = "All systems operational",
                            recoveryHints = emptyList(),
                        ),
                )

            "status_initializing", "diagnostics_initializing" ->
                Success(
                    report =
                        DiagnosticsReport(
                            checks =
                                listOf(
                                    Check(
                                        name = "Authentication",
                                        status = "healthy",
                                        details = "Session is valid and authenticated",
                                    ),
                                    Check(
                                        name = "Sync Engine",
                                        status = "initializing",
                                        details = "Sync engine is initializing (2/5 steps complete)",
                                    ),
                                    Check(
                                        name = "Event Queue",
                                        status = "initializing",
                                        details = "Event queue is being initialized",
                                    ),
                                    Check(
                                        name = "Key Packages",
                                        status = "initializing",
                                        details = "Key packages are being loaded",
                                    ),
                                    Check(
                                        name = "Network Connectivity",
                                        status = "healthy",
                                        details = "Network connection is stable",
                                    ),
                                ),
                            summary = "System is initializing",
                            recoveryHints =
                                listOf(
                                    RecoveryHint(
                                        description = "Wait for system initialization to complete",
                                        command = "wire sync status --verbose",
                                    ),
                                ),
                        ),
                )

            "status_degraded", "diagnostics_degraded" ->
                Success(
                    report =
                        DiagnosticsReport(
                            checks =
                                listOf(
                                    Check(
                                        name = "Authentication",
                                        status = "healthy",
                                        details = "Session is valid and authenticated",
                                    ),
                                    Check(
                                        name = "Sync Engine",
                                        status = "degraded",
                                        details = "Sync engine is running but experiencing delays",
                                    ),
                                    Check(
                                        name = "Event Queue",
                                        status = "degraded",
                                        details = "Event queue is backlogged (lag: 5000ms, pending: 250)",
                                    ),
                                    Check(
                                        name = "Key Packages",
                                        status = "degraded",
                                        details = "Key packages running low: 3 remaining",
                                    ),
                                    Check(
                                        name = "Network Connectivity",
                                        status = "degraded",
                                        details = "Network latency detected (200ms avg)",
                                    ),
                                ),
                            summary = "System is degraded, operation continues but performance is reduced",
                            recoveryHints =
                                listOf(
                                    RecoveryHint(
                                        description = "Clear event backlog",
                                        command = "wire sync reset --mode=queue",
                                    ),
                                    RecoveryHint(
                                        description = "Refresh key packages",
                                        command = "wire sync reset --mode=keys",
                                    ),
                                ),
                        ),
                )

            "status_error", "diagnostics_error" ->
                Success(
                    report =
                        DiagnosticsReport(
                            checks =
                                listOf(
                                    Check(
                                        name = "Authentication",
                                        status = "healthy",
                                        details = "Session is valid and authenticated",
                                    ),
                                    Check(
                                        name = "Sync Engine",
                                        status = "error",
                                        details = "Sync engine encountered a critical error and stopped",
                                    ),
                                    Check(
                                        name = "Event Queue",
                                        status = "error",
                                        details = "Event queue has stopped processing (lag: 30000ms, pending: 1000)",
                                    ),
                                    Check(
                                        name = "Key Packages",
                                        status = "error",
                                        details = "Key package retrieval failed",
                                    ),
                                    Check(
                                        name = "Network Connectivity",
                                        status = "error",
                                        details = "Network connection is unstable or unavailable",
                                    ),
                                ),
                            summary = "System encountered critical errors, immediate action required",
                            recoveryHints =
                                listOf(
                                    RecoveryHint(
                                        description = "Check your network connection",
                                        command = "ping api.wire.com",
                                    ),
                                    RecoveryHint(
                                        description = "Reset sync engine to recover",
                                        command = "wire sync reset",
                                    ),
                                    RecoveryHint(
                                        description = "Check logs for more details",
                                        command = "wire debug logs --tail=100",
                                    ),
                                ),
                        ),
                )

            "diagnostics_network_error" ->
                Failure(
                    message = SyncMessages.DIAGNOSTICS_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "diagnostics_server_error" ->
                Failure(
                    message = SyncMessages.DIAGNOSTICS_SERVER_FAILURE,
                    exitCode = SyncExitCodes.SERVER_ERROR,
                )

            "diagnostics_unauthorized" ->
                Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else ->
                Success(
                    report =
                        DiagnosticsReport(
                            checks =
                                listOf(
                                    Check(
                                        name = "Authentication",
                                        status = "healthy",
                                        details = "Session is valid and authenticated",
                                    ),
                                    Check(
                                        name = "Sync Engine",
                                        status = "healthy",
                                        details = "Sync engine is running and responsive",
                                    ),
                                    Check(
                                        name = "Event Queue",
                                        status = "healthy",
                                        details = "Event queue is processing normally (lag: 100ms)",
                                    ),
                                    Check(
                                        name = "Key Packages",
                                        status = "healthy",
                                        details = "Key packages available: 42 remaining",
                                    ),
                                    Check(
                                        name = "Network Connectivity",
                                        status = "healthy",
                                        details = "Network connection is stable and responsive",
                                    ),
                                ),
                            summary = "All systems operational",
                            recoveryHints = emptyList(),
                        ),
                )
        }
    }

    override fun getConversationSyncStatus(
        session: AuthSession,
        conversationId: String,
    ): ConversationSyncStatusResult {
        val mode = environment["WIRE_STUB_MODE"]

        if (conversationId.isBlank()) {
            return ConversationFailure(
                message = SyncMessages.CONVERSATION_NOT_FOUND,
                exitCode = SyncExitCodes.DEGRADED,
            )
        }

        return when (mode) {
            "conversation_ready" ->
                ConversationSuccess(
                    status =
                        ConversationSyncStatus(
                            conversation_id = conversationId,
                            status = SyncStatus.READY,
                            metrics =
                                ConversationMetrics(
                                    conversation_id = conversationId,
                                    lag_ms = 50L,
                                    pending_messages = 0,
                                    sync_completeness_pct = 100,
                                    timestamp = "2025-03-13T10:30:00Z",
                                ),
                            last_sync_timestamp = "2025-03-13T10:30:00Z",
                        ),
                )

            "conversation_initializing" ->
                ConversationSuccess(
                    status =
                        ConversationSyncStatus(
                            conversation_id = conversationId,
                            status = SyncStatus.INITIALIZING,
                            metrics =
                                ConversationMetrics(
                                    conversation_id = conversationId,
                                    lag_ms = 2000L,
                                    pending_messages = 15,
                                    sync_completeness_pct = 65,
                                    timestamp = "2025-03-13T10:32:00Z",
                                ),
                            last_sync_timestamp = "2025-03-13T10:32:00Z",
                        ),
                )

            "conversation_degraded" ->
                ConversationSuccess(
                    status =
                        ConversationSyncStatus(
                            conversation_id = conversationId,
                            status = SyncStatus.DEGRADED,
                            metrics =
                                ConversationMetrics(
                                    conversation_id = conversationId,
                                    lag_ms = 5000L,
                                    pending_messages = 50,
                                    sync_completeness_pct = 40,
                                    timestamp = "2025-03-13T10:35:00Z",
                                ),
                            last_sync_timestamp = "2025-03-13T10:35:00Z",
                        ),
                )

            "conversation_network_error" ->
                ConversationFailure(
                    message = SyncMessages.CONVERSATION_SYNC_NETWORK_FAILURE,
                    exitCode = SyncExitCodes.DEGRADED,
                )

            "conversation_not_found" ->
                ConversationFailure(
                    message = SyncMessages.CONVERSATION_NOT_FOUND,
                    exitCode = SyncExitCodes.DEGRADED,
                )

            else ->
                ConversationSuccess(
                    status =
                        ConversationSyncStatus(
                            conversation_id = conversationId,
                            status = SyncStatus.READY,
                            metrics =
                                ConversationMetrics(
                                    conversation_id = conversationId,
                                    lag_ms = 50L,
                                    pending_messages = 0,
                                    sync_completeness_pct = 100,
                                    timestamp = "2025-03-13T10:30:00Z",
                                ),
                            last_sync_timestamp = "2025-03-13T10:30:00Z",
                        ),
                )
        }
    }

    override fun getPerConversationDiagnostics(
        session: AuthSession,
        conversationId: String,
    ): PerConversationDiagnosticsResult {
        val mode = environment["WIRE_STUB_MODE"]

        if (conversationId.isBlank()) {
            return PerConversationFailure(
                message = SyncMessages.CONVERSATION_NOT_FOUND,
                exitCode = SyncExitCodes.DEGRADED,
            )
        }

        return when (mode) {
            "conversation_ready", "conversation_diagnostics_healthy" ->
                PerConversationSuccess(
                    report =
                        PerConversationDiagnosticsReport(
                            conversation_id = conversationId,
                            checks =
                                listOf(
                                    Check(
                                        name = "Conversation State",
                                        status = "Pass",
                                        details = "Conversation ID: $conversationId",
                                    ),
                                    Check(
                                        name = "Message Sync",
                                        status = "Pass",
                                        details = "All messages synced",
                                    ),
                                    Check(
                                        name = "Sync Completeness",
                                        status = "Pass",
                                        details = "Sync completeness: 100%",
                                    ),
                                    Check(
                                        name = "Conversation Connectivity",
                                        status = "Pass",
                                        details = "Conversation is reachable",
                                    ),
                                ),
                            summary = "Conversation is fully synced and healthy.",
                            recoveryHints = emptyList(),
                        ),
                )

            "conversation_initializing", "conversation_diagnostics_initializing" ->
                PerConversationSuccess(
                    report =
                        PerConversationDiagnosticsReport(
                            conversation_id = conversationId,
                            checks =
                                listOf(
                                    Check(
                                        name = "Conversation State",
                                        status = "Pass",
                                        details = "Conversation ID: $conversationId",
                                    ),
                                    Check(
                                        name = "Message Sync",
                                        status = "Warn",
                                        details = "Message sync in progress",
                                    ),
                                    Check(
                                        name = "Sync Completeness",
                                        status = "Warn",
                                        details = "Sync completeness: 65%",
                                    ),
                                    Check(
                                        name = "Conversation Connectivity",
                                        status = "Pass",
                                        details = "Conversation is reachable",
                                    ),
                                ),
                            summary = "Conversation sync is in progress. Check back soon.",
                            recoveryHints =
                                listOf(
                                    RecoveryHint(
                                        description = "Conversation is syncing messages",
                                        command = "Monitor sync progress with: wire-cli sync status --conversation $conversationId",
                                    ),
                                ),
                        ),
                )

            "conversation_degraded", "conversation_diagnostics_degraded" ->
                PerConversationSuccess(
                    report =
                        PerConversationDiagnosticsReport(
                            conversation_id = conversationId,
                            checks =
                                listOf(
                                    Check(
                                        name = "Conversation State",
                                        status = "Pass",
                                        details = "Conversation ID: $conversationId",
                                    ),
                                    Check(
                                        name = "Message Sync",
                                        status = "Fail",
                                        details = "Message sync degraded",
                                    ),
                                    Check(
                                        name = "Sync Completeness",
                                        status = "Warn",
                                        details = "Sync completeness: 40%",
                                    ),
                                    Check(
                                        name = "Conversation Connectivity",
                                        status = "Fail",
                                        details = "Conversation connectivity degraded",
                                    ),
                                ),
                            summary = "Conversation sync has failed. Recovery actions may help.",
                            recoveryHints =
                                listOf(
                                    RecoveryHint(
                                        description = "Message sync failed for conversation",
                                        command = "wire-cli sync status --conversation $conversationId --retry",
                                    ),
                                ),
                        ),
                )

            "conversation_network_error" ->
                PerConversationFailure(
                    message = SyncMessages.CONVERSATION_SYNC_NETWORK_FAILURE,
                    exitCode = SyncExitCodes.DEGRADED,
                )

            "conversation_not_found" ->
                PerConversationFailure(
                    message = SyncMessages.CONVERSATION_NOT_FOUND,
                    exitCode = SyncExitCodes.DEGRADED,
                )

            else ->
                PerConversationSuccess(
                    report =
                        PerConversationDiagnosticsReport(
                            conversation_id = conversationId,
                            checks =
                                listOf(
                                    Check(
                                        name = "Conversation State",
                                        status = "Pass",
                                        details = "Conversation ID: $conversationId",
                                    ),
                                    Check(
                                        name = "Message Sync",
                                        status = "Pass",
                                        details = "All messages synced",
                                    ),
                                    Check(
                                        name = "Sync Completeness",
                                        status = "Pass",
                                        details = "Sync completeness: 100%",
                                    ),
                                    Check(
                                        name = "Conversation Connectivity",
                                        status = "Pass",
                                        details = "Conversation is reachable",
                                    ),
                                ),
                            summary = "Conversation is fully synced and healthy.",
                            recoveryHints = emptyList(),
                        ),
                )
        }
    }

    override fun resetSync(
        session: AuthSession,
        force: Boolean,
    ): ResetResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "reset_success" ->
                Success(
                    message = "Sync reset completed successfully",
                )
            "reset_forced" ->
                Success(
                    message = "Sync reset completed successfully (forced)",
                )
            "reset_error", "network_error" ->
                Failure(
                    message = SyncMessages.NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )
            "reset_server_error", "server_error" ->
                Failure(
                    message = SyncMessages.SERVER_FAILURE,
                    exitCode = SyncExitCodes.SERVER_ERROR,
                )
            "reset_unauthorized", "unauthorized" ->
                Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )
            else ->
                Success(
                    message = "Sync reset completed successfully",
                )
        }
    }
}

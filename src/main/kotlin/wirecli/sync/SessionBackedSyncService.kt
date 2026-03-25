package wirecli.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider

private val logger = KotlinLogging.logger {}

class SessionBackedSyncService(
    private val sessionStore: SessionProvider,
    private val apiClient: SyncApiClient,
) : SyncService {
    override fun forceSyncAndWait(): SyncStatusResult {
        logger.debug { "Forcing sync and waiting for live state" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot force sync" }
                    return SyncStatusResult.Failure(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    )
                }

        logger.debug { "Active session found for user: ${session.userId} - forcing sync and waiting" }
        return apiClient.forceSyncAndWait(session).also { result ->
            when (result) {
                is SyncStatusResult.Success ->
                    logger.info {
                        "Force sync completed: status=${result.view.status}, lag=${result.view.metrics.lagMs}ms"
                    }
                is SyncStatusResult.Failure ->
                    logger.warn { "Force sync failed: ${result.message} (exit code: ${result.exitCode})" }
            }
        }
    }

    override fun getCurrentSyncStatus(): SyncStatusResult {
        logger.debug { "Fetching current sync status" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot fetch sync status" }
                    return SyncStatusResult.Failure(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    )
                }

        logger.debug { "Active session found for user: ${session.userId} - calling sync API" }
        return apiClient.getSyncStatus(session).also { result ->
            when (result) {
                is SyncStatusResult.Success ->
                    logger.info {
                        "Sync status fetched successfully: status=${result.view.status}, lag=${result.view.metrics.lagMs}ms"
                    }
                is SyncStatusResult.Failure ->
                    logger.warn { "Failed to fetch sync status: ${result.message} (exit code: ${result.exitCode})" }
            }
        }
    }

    override fun getDiagnosticsReport(): DiagnosticsResult {
        logger.debug { "Fetching diagnostics report" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot fetch diagnostics" }
                    return DiagnosticsResult.Failure(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    )
                }

        logger.debug { "Active session found for user: ${session.userId} - calling diagnostics API" }
        return apiClient.getDiagnostics(session).also { result ->
            when (result) {
                is DiagnosticsResult.Success -> {
                    val checks = result.report.checks
                    val passed = checks.count { it.status == "Pass" }
                    val failed = checks.count { it.status == "Fail" }
                    val warned = checks.count { it.status == "Warn" }
                    logger.info {
                        "Diagnostics fetched successfully: $passed passed, $failed failed, $warned warned checks"
                    }
                }
                is DiagnosticsResult.Failure -> {
                    logger.warn { "Failed to fetch diagnostics: ${result.message} (exit code: ${result.exitCode})" }
                }
            }
        }
    }

    override fun getConversationSyncStatus(conversationId: String): ConversationSyncStatusResult {
        logger.debug { "Fetching sync status for conversation: $conversationId" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot fetch conversation sync status" }
                    return ConversationSyncStatusResult.Failure(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    )
                }

        logger.debug {
            "Active session found for user: ${session.userId} - fetching sync status for conversation $conversationId"
        }
        return apiClient.getConversationSyncStatus(session, conversationId).also { result ->
            when (result) {
                is ConversationSyncStatusResult.Success ->
                    logger.info {
                        "Conversation sync status fetched: conversationId=$conversationId, " +
                            "status=${result.status.status}, lag=${result.status.metrics.lagMs}ms"
                    }
                is ConversationSyncStatusResult.Failure ->
                    logger.warn {
                        "Failed to fetch conversation sync status for $conversationId: " +
                            "${result.message} (exit code: ${result.exitCode})"
                    }
            }
        }
    }

    override fun getPerConversationDiagnostics(conversationId: String): PerConversationDiagnosticsResult {
        logger.debug { "Fetching per-conversation diagnostics for: $conversationId" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot fetch conversation diagnostics" }
                    return PerConversationDiagnosticsResult.Failure(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    )
                }

        logger.debug {
            "Active session found for user: ${session.userId} - fetching diagnostics for conversation $conversationId"
        }
        return apiClient.getPerConversationDiagnostics(session, conversationId).also { result ->
            when (result) {
                is PerConversationDiagnosticsResult.Success -> {
                    val checks = result.report.checks
                    val passed = checks.count { it.status == "Pass" }
                    val failed = checks.count { it.status == "Fail" }
                    val warned = checks.count { it.status == "Warn" }
                    logger.info {
                        "Conversation diagnostics fetched: conversationId=$conversationId, " +
                            "$passed passed, $failed failed, $warned warned checks"
                    }
                }
                is PerConversationDiagnosticsResult.Failure ->
                    logger.warn {
                        "Failed to fetch conversation diagnostics for $conversationId: " +
                            "${result.message} (exit code: ${result.exitCode})"
                    }
            }
        }
    }

    override fun resetSync(force: Boolean): ResetResult {
        logger.debug { "Resetting sync (force=$force)" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot reset sync" }
                    return ResetResult.Failure(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    )
                }

        logger.debug { "Active session found for user: ${session.userId} - resetting sync (force=$force)" }
        return apiClient.resetSync(session, force).also { result ->
            when (result) {
                is ResetResult.Success -> {
                    logger.info {
                        "Sync reset completed successfully for user: ${session.userId} (force=$force)"
                    }
                }
                is ResetResult.Failure -> {
                    logger.error {
                        "Failed to reset sync for user ${session.userId}: " +
                            "${result.message} (exit code: ${result.exitCode})"
                    }
                }
            }
        }
    }
}

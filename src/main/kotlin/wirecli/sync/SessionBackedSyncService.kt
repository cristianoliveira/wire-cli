package wirecli.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import wirecli.shared.Result
import wirecli.shared.SyncError

private val logger = KotlinLogging.logger {}

class SessionBackedSyncService(
    private val sessionStore: SessionProvider,
    private val apiClient: SyncApiClient,
) : SyncService {
    override fun forceSyncAndWait(): SyncResult<SyncStatusView> {
        logger.debug { "Forcing sync and waiting for live state" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot force sync" }
                    return Result.Failure(
                        error = SyncError(
                            message = AuthMessages.noActiveSession(),
                            exitCode = ExitCodes.UNAUTHORIZED,
                        ),
                    )
                }

        logger.debug { "Active session found for user: ${session.userId} - forcing sync and waiting" }
        return apiClient.forceSyncAndWait(session).also { result ->
            when (result) {
                is Result.Success ->
                    logger.info { "Force sync completed: status=${result.value.status}, lag=${result.value.metrics.lag_ms}ms" }
                is Result.Failure ->
                    logger.warn { "Force sync failed: ${result.error.message} (exit code: ${result.error.exitCode})" }
            }
        }
    }

    override fun getCurrentSyncStatus(): SyncResult<SyncStatusView> {
        logger.debug { "Fetching current sync status" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot fetch sync status" }
                    return Result.Failure(
                        error = SyncError(
                            message = AuthMessages.noActiveSession(),
                            exitCode = ExitCodes.UNAUTHORIZED,
                        ),
                    )
                }

        logger.debug { "Active session found for user: ${session.userId} - calling sync API" }
        return apiClient.getSyncStatus(session).also { result ->
            when (result) {
                is Result.Success ->
                    logger.info { "Sync status fetched successfully: status=${result.value.status}, lag=${result.value.metrics.lag_ms}ms" }
                is Result.Failure ->
                    logger.warn { "Failed to fetch sync status: ${result.error.message} (exit code: ${result.error.exitCode})" }
            }
        }
    }

    override fun getDiagnosticsReport(): SyncResult<DiagnosticsReport> {
        logger.debug { "Fetching diagnostics report" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot fetch diagnostics" }
                    return Result.Failure(
                        error = SyncError(
                            message = AuthMessages.noActiveSession(),
                            exitCode = ExitCodes.UNAUTHORIZED,
                        ),
                    )
                }

        logger.debug { "Active session found for user: ${session.userId} - calling diagnostics API" }
        return apiClient.getDiagnostics(session).also { result ->
            when (result) {
                is Result.Success -> {
                    val checks = result.value.checks
                    val passed = checks.count { it.status == "Pass" }
                    val failed = checks.count { it.status == "Fail" }
                    val warned = checks.count { it.status == "Warn" }
                    logger.info { "Diagnostics fetched successfully: $passed passed, $failed failed, $warned warned checks" }
                }
                is Result.Failure -> {
                    logger.warn { "Failed to fetch diagnostics: ${result.error.message} (exit code: ${result.error.exitCode})" }
                }
            }
        }
    }

    override fun getConversationSyncStatus(conversationId: String): SyncResult<ConversationSyncStatus> {
        logger.debug { "Fetching sync status for conversation: $conversationId" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot fetch conversation sync status" }
                    return Result.Failure(
                        error = SyncError(
                            message = AuthMessages.noActiveSession(),
                            exitCode = ExitCodes.UNAUTHORIZED,
                        ),
                    )
                }

        logger.debug { "Active session found for user: ${session.userId} - fetching sync status for conversation $conversationId" }
        return apiClient.getConversationSyncStatus(session, conversationId).also { result ->
            when (result) {
                is Result.Success ->
                    logger.info {
                        "Conversation sync status fetched: conversationId=$conversationId, " +
                            "status=${result.value.status}, lag=${result.value.metrics.lag_ms}ms"
                    }
                is Result.Failure ->
                    logger.warn {
                        "Failed to fetch conversation sync status for $conversationId: " +
                            "${result.error.message} (exit code: ${result.error.exitCode})"
                    }
            }
        }
    }

    override fun getPerConversationDiagnostics(conversationId: String): SyncResult<PerConversationDiagnosticsReport> {
        logger.debug { "Fetching per-conversation diagnostics for: $conversationId" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot fetch conversation diagnostics" }
                    return Result.Failure(
                        error = SyncError(
                            message = AuthMessages.noActiveSession(),
                            exitCode = ExitCodes.UNAUTHORIZED,
                        ),
                    )
                }

        logger.debug { "Active session found for user: ${session.userId} - fetching diagnostics for conversation $conversationId" }
        return apiClient.getPerConversationDiagnostics(session, conversationId).also { result ->
            when (result) {
                is Result.Success -> {
                    val checks = result.value.checks
                    val passed = checks.count { it.status == "Pass" }
                    val failed = checks.count { it.status == "Fail" }
                    val warned = checks.count { it.status == "Warn" }
                    logger.info {
                        "Conversation diagnostics fetched: conversationId=$conversationId, " +
                            "$passed passed, $failed failed, $warned warned checks"
                    }
                }
                is Result.Failure ->
                    logger.warn {
                        "Failed to fetch conversation diagnostics for $conversationId: " +
                            "${result.error.message} (exit code: ${result.error.exitCode})"
                    }
            }
        }
    }

    override fun resetSync(force: Boolean): SyncResult<String> {
        logger.debug { "Resetting sync (force=$force)" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found - cannot reset sync" }
                    return Result.Failure(
                        error = SyncError(
                            message = AuthMessages.noActiveSession(),
                            exitCode = ExitCodes.UNAUTHORIZED,
                        ),
                    )
                }

        logger.debug { "Active session found for user: ${session.userId} - resetting sync (force=$force)" }
        return apiClient.resetSync(session, force).also { result ->
            when (result) {
                is Result.Success -> {
                    logger.info { "Sync reset completed successfully for user: ${session.userId} (force=$force)" }
                }
                is Result.Failure -> {
                    logger.error { "Failed to reset sync for user ${session.userId}: ${result.error.message} (exit code: ${result.error.exitCode})" }
                }
            }
        }
    }
}

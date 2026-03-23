package wirecli.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.shared.AuthError
import wirecli.shared.Result
import wirecli.shared.SyncError

private val logger = KotlinLogging.logger {}

class AuthGuardedSyncService(
    private val authSessionService: AuthSessionService,
    private val delegate: SyncService,
) : SyncService {
    override fun forceSyncAndWait(): SyncResult<SyncStatusView> {
        logger.debug { "AuthGuardedSyncService: Checking authentication for forceSyncAndWait" }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.forceSyncAndWait()
            }
            is Result.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.error.message} (exit code: ${authResult.error.exitCode})" }
                Result.Failure(
                    error = SyncError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
            }
        }
    }

    override fun getCurrentSyncStatus(): SyncResult<SyncStatusView> {
        logger.debug { "AuthGuardedSyncService: Checking authentication for getCurrentSyncStatus" }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.getCurrentSyncStatus()
            }
            is Result.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.error.message} (exit code: ${authResult.error.exitCode})" }
                Result.Failure(
                    error = SyncError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
            }
        }
    }

    override fun getDiagnosticsReport(): SyncResult<DiagnosticsReport> {
        logger.debug { "AuthGuardedSyncService: Checking authentication for getDiagnosticsReport" }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.getDiagnosticsReport()
            }
            is Result.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.error.message} (exit code: ${authResult.error.exitCode})" }
                Result.Failure(
                    error = SyncError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
            }
        }
    }

    override fun getConversationSyncStatus(conversationId: String): SyncResult<ConversationSyncStatus> {
        logger.debug { "AuthGuardedSyncService: Checking authentication for getConversationSyncStatus (conversationId: $conversationId)" }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.getConversationSyncStatus(conversationId)
            }
            is Result.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.error.message} (exit code: ${authResult.error.exitCode})" }
                Result.Failure(
                    error = SyncError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
            }
        }
    }

    override fun getPerConversationDiagnostics(conversationId: String): SyncResult<PerConversationDiagnosticsReport> {
        logger.debug {
            "AuthGuardedSyncService: Checking authentication for " +
                "getPerConversationDiagnostics (conversationId: $conversationId)"
        }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.getPerConversationDiagnostics(conversationId)
            }
            is Result.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.error.message} (exit code: ${authResult.error.exitCode})" }
                Result.Failure(
                    error = SyncError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
            }
        }
    }

    override fun resetSync(force: Boolean): SyncResult<String> {
        logger.debug { "AuthGuardedSyncService: Checking authentication for resetSync (force=$force)" }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.resetSync(force)
            }
            is Result.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.error.message} (exit code: ${authResult.error.exitCode})" }
                Result.Failure(
                    error = SyncError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
            }
        }
    }
}

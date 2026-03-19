package wirecli.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

private val logger = KotlinLogging.logger {}

class AuthGuardedSyncService(
    private val authSessionService: AuthSessionService,
    private val delegate: SyncService,
) : SyncService {
    override fun forceSyncAndWait(): SyncStatusResult {
        logger.debug { "AuthGuardedSyncService: Checking authentication for forceSyncAndWait" }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.forceSyncAndWait()
            }
            is AuthResult.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.message} (exit code: ${authResult.exitCode})" }
                SyncStatusResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
            }
        }
    }

    override fun getCurrentSyncStatus(): SyncStatusResult {
        logger.debug { "AuthGuardedSyncService: Checking authentication for getCurrentSyncStatus" }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.getCurrentSyncStatus()
            }
            is AuthResult.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.message} (exit code: ${authResult.exitCode})" }
                SyncStatusResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
            }
        }
    }

    override fun getDiagnosticsReport(): DiagnosticsResult {
        logger.debug { "AuthGuardedSyncService: Checking authentication for getDiagnosticsReport" }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.getDiagnosticsReport()
            }
            is AuthResult.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.message} (exit code: ${authResult.exitCode})" }
                DiagnosticsResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
            }
        }
    }

    override fun getConversationSyncStatus(conversationId: String): ConversationSyncStatusResult {
        logger.debug { "AuthGuardedSyncService: Checking authentication for getConversationSyncStatus (conversationId: $conversationId)" }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.getConversationSyncStatus(conversationId)
            }
            is AuthResult.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.message} (exit code: ${authResult.exitCode})" }
                ConversationSyncStatusResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
            }
        }
    }

    override fun getPerConversationDiagnostics(conversationId: String): PerConversationDiagnosticsResult {
        logger.debug {
            "AuthGuardedSyncService: Checking authentication for " +
                "getPerConversationDiagnostics (conversationId: $conversationId)"
        }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.getPerConversationDiagnostics(conversationId)
            }
            is AuthResult.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.message} (exit code: ${authResult.exitCode})" }
                PerConversationDiagnosticsResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
            }
        }
    }

    override fun resetSync(force: Boolean): ResetResult {
        logger.debug { "AuthGuardedSyncService: Checking authentication for resetSync (force=$force)" }
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> {
                logger.debug { "Authentication check passed - delegating to sync service" }
                delegate.resetSync(force)
            }
            is AuthResult.Failure -> {
                logger.warn { "Authentication check failed: ${authResult.message} (exit code: ${authResult.exitCode})" }
                ResetResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
            }
        }
    }
}

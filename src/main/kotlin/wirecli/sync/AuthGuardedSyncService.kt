package wirecli.sync

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class AuthGuardedSyncService(
    private val authSessionService: AuthSessionService,
    private val delegate: SyncService,
) : SyncService {
    override fun getCurrentSyncStatus(): SyncStatusResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getCurrentSyncStatus()
            is AuthResult.Failure ->
                SyncStatusResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun getDiagnosticsReport(): DiagnosticsResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getDiagnosticsReport()
            is AuthResult.Failure ->
                DiagnosticsResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun getConversationSyncStatus(conversationId: String): ConversationSyncStatusResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getConversationSyncStatus(conversationId)
            is AuthResult.Failure ->
                ConversationSyncStatusResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun getPerConversationDiagnostics(conversationId: String): PerConversationDiagnosticsResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getPerConversationDiagnostics(conversationId)
            is AuthResult.Failure ->
                PerConversationDiagnosticsResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

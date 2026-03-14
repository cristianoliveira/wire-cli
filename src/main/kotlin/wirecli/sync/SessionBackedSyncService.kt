package wirecli.sync

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes

class SessionBackedSyncService(
    private val sessionStore: AuthSessionStore,
    private val apiClient: SyncApiClient,
) : SyncService {
    override fun getCurrentSyncStatus(): SyncStatusResult {
        val session =
            sessionStore.readActiveSession()
                ?: return SyncStatusResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.getSyncStatus(session)
    }

    override fun getDiagnosticsReport(): DiagnosticsResult {
        val session =
            sessionStore.readActiveSession()
                ?: return DiagnosticsResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.getDiagnostics(session)
    }

    override fun getConversationSyncStatus(conversationId: String): ConversationSyncStatusResult {
        val session =
            sessionStore.readActiveSession()
                ?: return ConversationSyncStatusResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.getConversationSyncStatus(session, conversationId)
    }

    override fun getPerConversationDiagnostics(conversationId: String): PerConversationDiagnosticsResult {
        val session =
            sessionStore.readActiveSession()
                ?: return PerConversationDiagnosticsResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.getPerConversationDiagnostics(session, conversationId)
    }

    override fun resetSync(force: Boolean): ResetResult {
        val session =
            sessionStore.readActiveSession()
                ?: return ResetResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.resetSync(session, force)
    }
}

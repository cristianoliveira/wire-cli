package wirecli.presence

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes

class SessionBackedPresenceService(
    private val sessionStore: AuthSessionStore,
    private val apiClient: PresenceApiClient,
) : PresenceService {
    // TODO: Consider using SessionInventory diagnostics for richer direct errors when not guarded.
    override fun getCurrentPresence(): PresenceResult {
        val session =
            sessionStore.readActiveSession()
                ?: return PresenceResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.fetchPresence(session)
    }
}

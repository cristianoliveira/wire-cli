package wirecli.profile

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes
import wirecli.presence.PresenceApiClient
import wirecli.presence.PresenceResult
import wirecli.presence.PresenceState

class SessionBackedProfileService(
    private val sessionStore: AuthSessionStore,
    private val apiClient: ProfileApiClient,
    private val presenceApiClient: PresenceApiClient,
) : ProfileService {
    // TODO: Consider using SessionInventory for more detailed error messages when not guarded by AuthGuardedProfileService.
    override fun getCurrentProfile(): ProfileResult {
        val session =
            sessionStore.readActiveSession()
                ?: return ProfileResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return when (val profileResult = apiClient.fetchProfile(session)) {
            is ProfileResult.Success ->
                ProfileResult.Success(
                    profile =
                        profileResult.profile.copy(
                            presence = resolvePresence(session),
                        ),
                )

            is ProfileResult.Failure -> profileResult
        }
    }

    private fun resolvePresence(session: AuthSession): PresenceState {
        return when (val presenceResult = presenceApiClient.fetchPresence(session)) {
            is PresenceResult.Success -> presenceResult.presence.state
            is PresenceResult.Failure -> PresenceState.UNKNOWN
        }
    }
}

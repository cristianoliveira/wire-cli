package wirecli.profile

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes
import wirecli.presence.PresenceApiClient
import wirecli.presence.PresenceResult
import wirecli.presence.PresenceState

private val logger = KotlinLogging.logger {}

class SessionBackedProfileService(
    private val sessionStore: AuthSessionStore,
    private val apiClient: ProfileApiClient,
    private val presenceApiClient: PresenceApiClient,
) : ProfileService {
    // TODO: Consider using SessionInventory for more detailed error messages when not guarded by AuthGuardedProfileService.
    override fun getCurrentProfile(): ProfileResult {
        logger.debug { "SessionBackedProfileService: Retrieving current profile" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found for profile retrieval" }
                    return ProfileResult.Failure(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    )
                }

        return when (val profileResult = apiClient.fetchProfile(session)) {
            is ProfileResult.Success -> {
                val presence = resolvePresence(session)
                logger.debug { "Profile retrieved successfully with presence: $presence" }
                ProfileResult.Success(
                    profile =
                        profileResult.profile.copy(
                            presence = presence,
                        ),
                )

            }

            is ProfileResult.Failure -> profileResult
        }
    }

    private fun resolvePresence(session: AuthSession): PresenceState {
        return when (val presenceResult = presenceApiClient.fetchPresence(session)) {
            is PresenceResult.Success -> presenceResult.presence.state
            is PresenceResult.Failure -> {
                logger.warn { "Failed to resolve presence for profile, using UNKNOWN" }
                PresenceState.UNKNOWN
            }
        }
    }
}

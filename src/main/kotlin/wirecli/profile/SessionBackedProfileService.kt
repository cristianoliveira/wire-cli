package wirecli.profile

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import wirecli.presence.PresenceApiClient
import wirecli.presence.PresenceState
import wirecli.shared.ProfileError
import wirecli.shared.Result

private val logger = KotlinLogging.logger {}

class SessionBackedProfileService(
    private val sessionStore: SessionProvider,
    private val apiClient: ProfileApiClient,
    private val presenceApiClient: PresenceApiClient,
) : ProfileService {
    // TODO: Consider using SessionInventory for more detailed error messages when not guarded by AuthGuardedProfileService.
    override fun getCurrentProfile(): ProfileResult<ProfileView> {
        logger.debug { "SessionBackedProfileService: Retrieving current profile" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found for profile retrieval" }
                    return Result.Failure(
                        error = ProfileError(
                            message = AuthMessages.noActiveSession(),
                            exitCode = ExitCodes.UNAUTHORIZED,
                        ),
                    )
                }

        return when (val profileResult = apiClient.fetchProfile(session)) {
            is Result.Success -> {
                val presence = resolvePresence(session)
                logger.debug { "Profile retrieved successfully with presence: $presence" }
                Result.Success(
                    value =
                        profileResult.value.copy(
                            presence = presence,
                        ),
                )
            }

            is Result.Failure -> profileResult
        }
    }

    private fun resolvePresence(session: AuthSession): PresenceState {
        return when (val presenceResult = presenceApiClient.fetchPresence(session)) {
            is Result.Success -> presenceResult.value.state
            is Result.Failure -> {
                logger.warn { "Failed to resolve presence for profile, using UNKNOWN" }
                PresenceState.UNKNOWN
            }
        }
    }
}

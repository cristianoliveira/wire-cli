package wirecli.presence

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import wirecli.shared.PresenceError
import wirecli.shared.Result

private val logger = KotlinLogging.logger {}

class SessionBackedPresenceService(
    private val sessionStore: SessionProvider,
    private val apiClient: PresenceApiClient,
) : PresenceService {
    // TODO: Consider using SessionInventory diagnostics for richer direct errors when not guarded.
    override fun getCurrentPresence(): PresenceResult<PresenceView> {
        logger.debug { "SessionBackedPresenceService: Retrieving current presence" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found for presence retrieval" }
                    return Result.Failure(
                        error = PresenceError(
                            message = AuthMessages.noActiveSession(),
                            exitCode = ExitCodes.UNAUTHORIZED,
                        ),
                    )
                }

        return apiClient.fetchPresence(session)
    }

    override fun setCurrentPresence(state: WritablePresenceState): PresenceResult<PresenceView> {
        logger.debug { "SessionBackedPresenceService: Setting presence to: $state" }
        val session =
            sessionStore.readActiveSession()
                ?: run {
                    logger.warn { "No active session found for setting presence" }
                    return Result.Failure(
                        error = PresenceError(
                            message = AuthMessages.noActiveSession(),
                            exitCode = ExitCodes.UNAUTHORIZED,
                        ),
                    )
                }

        return apiClient.updatePresence(session, state)
    }
}

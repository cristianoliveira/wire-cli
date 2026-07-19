package wirecli.team

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider

private val logger = KotlinLogging.logger {}

class SessionBackedTeamService(
    private val sessionStore: SessionProvider,
    private val apiClient: TeamApiClient,
) : TeamService {
    override fun read(): TeamReadResult {
        logger.debug { "Service operation: read() started" }

        val session =
            sessionStore.readActiveSession()
                ?: return TeamReadResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for read()" } }

        logger.debug { "Active session found, calling API client" }
        return apiClient.readTeam(session).also { result ->
            when (result) {
                is TeamReadResult.Success ->
                    logger.info { "Service: Successfully read team ${result.view.id}" }
                is TeamReadResult.Failure -> logger.warn { "Service: Failed to read team - ${result.message}" }
            }
        }
    }
}

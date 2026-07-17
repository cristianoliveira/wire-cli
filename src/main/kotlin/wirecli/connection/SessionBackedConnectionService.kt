package wirecli.connection

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider

private val logger = KotlinLogging.logger {}

class SessionBackedConnectionService(
    private val sessionStore: SessionProvider,
    private val apiClient: ConnectionApiClient,
) : ConnectionService {
    override fun sendRequest(userId: String): ConnectionActionResult {
        logger.debug { "Service operation: sendRequest($userId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return ConnectionActionResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for sendRequest($userId)" } }

        return apiClient.sendRequest(session, userId).also { result ->
            when (result) {
                is ConnectionActionResult.Success -> logger.info { "Service: connection request sent to $userId" }
                is ConnectionActionResult.Failure -> logger.warn { "Service: connection request failed for $userId" }
            }
        }
    }

    override fun acceptRequest(userId: String): ConnectionActionResult {
        logger.debug { "Service operation: acceptRequest($userId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return ConnectionActionResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for acceptRequest($userId)" } }

        return apiClient.acceptRequest(session, userId).also { result ->
            when (result) {
                is ConnectionActionResult.Success -> logger.info { "Service: accepted connection request from $userId" }
                is ConnectionActionResult.Failure -> logger.warn { "Service: failed to accept request from $userId" }
            }
        }
    }

    override fun blockUser(userId: String): ConnectionActionResult {
        logger.debug { "Service operation: blockUser($userId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return ConnectionActionResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for blockUser($userId)" } }

        return apiClient.blockUser(session, userId).also { result ->
            when (result) {
                is ConnectionActionResult.Success -> logger.info { "Service: blocked user $userId" }
                is ConnectionActionResult.Failure -> logger.warn { "Service: failed to block user $userId" }
            }
        }
    }

    override fun unblockUser(userId: String): ConnectionActionResult {
        logger.debug { "Service operation: unblockUser($userId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return ConnectionActionResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for unblockUser($userId)" } }

        return apiClient.unblockUser(session, userId).also { result ->
            when (result) {
                is ConnectionActionResult.Success -> logger.info { "Service: unblocked user $userId" }
                is ConnectionActionResult.Failure -> logger.warn { "Service: failed to unblock user $userId" }
            }
        }
    }

    override fun listConnections(): ConnectionListResult {
        logger.debug { "Service operation: listConnections() started" }

        val session =
            sessionStore.readActiveSession()
                ?: return ConnectionListResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for listConnections()" } }

        return apiClient.listConnections(session).also { result ->
            when (result) {
                is ConnectionListResult.Success -> logger.info { "Service: listed ${result.view.connections.size} connection(s)" }
                is ConnectionListResult.Failure -> logger.warn { "Service: failed to list connections" }
            }
        }
    }
}

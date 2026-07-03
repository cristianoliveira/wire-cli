package wirecli.user

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider

private val logger = KotlinLogging.logger {}

class SessionBackedUserService(
    private val sessionStore: SessionProvider,
    private val apiClient: UserApiClient,
) : UserService {
    override fun search(query: UserSearchQuery): UserSearchResult {
        logger.debug { "Service operation: search(${query.query}) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return UserSearchResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for search()" } }

        return apiClient.searchUsers(session, query).also { result ->
            when (result) {
                is UserSearchResult.Success ->
                    logger.info { "Service: search returned ${result.view.users.size} user(s)" }
                is UserSearchResult.Failure -> logger.warn { "Service: search failed - ${result.message}" }
            }
        }
    }

    override fun get(userId: String): UserGetResult {
        logger.debug { "Service operation: get($userId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return UserGetResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for get($userId)" } }

        return apiClient.getUser(session, userId).also { result ->
            when (result) {
                is UserGetResult.Success -> logger.info { "Service: retrieved user ${result.view.id}" }
                is UserGetResult.Failure -> logger.warn { "Service: failed to get user $userId" }
            }
        }
    }
}

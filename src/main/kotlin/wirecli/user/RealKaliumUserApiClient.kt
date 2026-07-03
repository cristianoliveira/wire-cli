package wirecli.user

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.search.SearchUserResult
import com.wire.kalium.logic.feature.user.GetUserInfoResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.config.KaliumCliMode
import wirecli.config.kaliumCliConfigs

private val logger = KotlinLogging.logger {}

internal class RealKaliumUserApiClient(
    private val runtime: UserRuntime,
) : UserApiClient {
    override fun searchUsers(
        session: AuthSession,
        query: UserSearchQuery,
    ): UserSearchResult {
        logger.debug { "RealKaliumUserApiClient: searching users for query='${query.query}'" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is UserStepResult.Success -> scope.value
                is UserStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for search: ${scope.category}" }
                    return scope.toSearchFailure()
                }
            }

        return when (val result = runtime.searchUsers(sessionScope, query.query, query.limit)) {
            is UserStepResult.Success -> {
                logger.info { "User search returned ${result.value.users.size} user(s)" }
                val view = UserListView(users = result.value.users.toUserViews())
                UserSearchResult.Success(view = view)
            }

            is UserStepResult.Failure -> {
                logger.warn { "Failed to search users: ${result.category}" }
                result.toSearchFailure()
            }
        }
    }

    override fun getUser(
        session: AuthSession,
        userId: String,
    ): UserGetResult {
        logger.debug { "RealKaliumUserApiClient: fetching user $userId" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is UserStepResult.Success -> scope.value
                is UserStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for get: ${scope.category}" }
                    return scope.toGetFailure()
                }
            }

        return when (val result = runtime.getUser(sessionScope, userId)) {
            is UserStepResult.Success -> {
                logger.info { "Successfully fetched user ${result.value.id}" }
                UserGetResult.Success(view = result.value.toUserView())
            }

            is UserStepResult.Failure -> {
                logger.warn { "Failed to fetch user $userId: ${result.category}" }
                result.toGetFailure()
            }
        }
    }
}

internal typealias RealKaliumUserRuntime = UserRuntime

internal class SdkKaliumUserRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : UserRuntime {
    private val activeSessionUserIds = mutableSetOf<UserId>()

    private val coreLogicLazy =
        lazy {
            CoreLogic(
                rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium",
                kaliumConfigs = kaliumCliConfigs(cliMode),
                userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
            )
        }
    private val coreLogic: CoreLogic by coreLogicLazy

    override fun resolveSessionScope(session: AuthSession): UserStepResult<KaliumUserSessionScope> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return UserStepResult.Failure(UserFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                if (!cliMode.disableSessionSyncWait) {
                    coreLogic.sessionScope(qualifiedId) {
                        syncExecutor.request { waitUntilLiveOrFailure() }
                    }
                }
                UserStepResult.Success(
                    KaliumUserSessionScope(
                        userId = session.userId,
                        server = session.server,
                    ),
                )
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to resolve user session scope for ${session.userId}" }
                UserStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun searchUsers(
        sessionScope: KaliumUserSessionScope,
        query: String,
        limit: Int,
    ): UserStepResult<KaliumUserSearchResult> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return UserStepResult.Failure(UserFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                val searchResult: SearchUserResult =
                    coreLogic.sessionScope(qualifiedId) {
                        search.searchUsersByName(
                            searchQuery = query,
                            excludingMembersOfConversation = null,
                            skipRemoteSearch = false,
                            customDomain = null,
                        )
                    }

                val merged =
                    buildList {
                        addAll(searchResult.connected)
                        addAll(searchResult.notConnected)
                    }.distinctBy { it.id }
                        .take(limit)
                        .map { details ->
                            KaliumUser(
                                id = details.id.toString(),
                                name = details.name,
                                handle = details.handle,
                                email = null,
                                team = null,
                                connection = details.connectionStatus.toUserConnectionState(),
                            )
                        }

                UserStepResult.Success(KaliumUserSearchResult(users = merged))
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to search users for query '$query'" }
                UserStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getUser(
        sessionScope: KaliumUserSessionScope,
        userId: String,
    ): UserStepResult<KaliumUser> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return UserStepResult.Failure(UserFailureCategory.UNAUTHORIZED)
        val targetId =
            userId.toQualifiedIdOrNull()
                ?: return UserStepResult.Failure(UserFailureCategory.USER_NOT_FOUND)

        return runBlocking {
            try {
                val result =
                    coreLogic.sessionScope(qualifiedId) {
                        users.getUserInfo(targetId)
                    }

                when (result) {
                    is GetUserInfoResult.Success ->
                        UserStepResult.Success(
                            KaliumUser(
                                id = result.otherUser.id.toString(),
                                name = result.otherUser.name,
                                handle = result.otherUser.handle,
                                email = result.otherUser.email,
                                team = result.team?.name,
                                connection = result.otherUser.connectionStatus.toUserConnectionState(),
                            ),
                        )

                    GetUserInfoResult.Failure ->
                        UserStepResult.Failure(UserFailureCategory.USER_NOT_FOUND)
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to fetch user $userId" }
                UserStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun shutdown() {
        if (!coreLogicLazy.isInitialized()) return

        runBlocking {
            activeSessionUserIds.forEach { userId ->
                coreLogic.sessionScope(userId) { cancel() }
            }
        }
        activeSessionUserIds.clear()
        coreLogic.getGlobalScope().cancel()
        logger.debug { "SdkKaliumUserRuntime: shutdown complete" }
    }

    private fun categoryFromCoreFailure(failure: CoreFailure): UserFailureCategory =
        when (failure) {
            is NetworkFailure.NoNetworkConnection,
            is NetworkFailure.ProxyError,
            -> UserFailureCategory.NETWORK

            is NetworkFailure.ServerMiscommunication -> UserFailureCategory.SERVER

            is NetworkFailure.FederatedBackendFailure,
            NetworkFailure.FeatureNotSupported,
            is NetworkFailure.MlsMessageRejectedFailure,
            -> UserFailureCategory.SERVER

            else -> UserFailureCategory.UNKNOWN
        }

    @Suppress("TooGenericExceptionCaught")
    private fun categoryFromThrowable(error: Throwable): UserFailureCategory {
        val message = error.message.orEmpty()
        return when {
            message.contains("network", ignoreCase = true) -> UserFailureCategory.NETWORK
            message.contains("unauthorized", ignoreCase = true) -> UserFailureCategory.UNAUTHORIZED
            message.contains("auth", ignoreCase = true) -> UserFailureCategory.UNAUTHORIZED
            message.contains("not found", ignoreCase = true) -> UserFailureCategory.USER_NOT_FOUND
            message.isNotEmpty() -> UserFailureCategory.SERVER
            else -> UserFailureCategory.UNKNOWN
        }
    }

    private fun resolveHomeDirectory(env: Map<String, String>): String {
        val home = env["HOME"]?.trim()
        if (!home.isNullOrEmpty()) return home
        return System.getProperty("user.home")
    }
}

private fun ConnectionState.toUserConnectionState(): UserConnectionState =
    when (this) {
        ConnectionState.NOT_CONNECTED -> UserConnectionState.NOT_CONNECTED
        ConnectionState.PENDING -> UserConnectionState.PENDING
        ConnectionState.SENT -> UserConnectionState.SENT
        ConnectionState.ACCEPTED -> UserConnectionState.ACCEPTED
        ConnectionState.BLOCKED -> UserConnectionState.BLOCKED
        ConnectionState.IGNORED -> UserConnectionState.IGNORED
        ConnectionState.CANCELLED -> UserConnectionState.CANCELLED
        ConnectionState.MISSING_LEGALHOLD_CONSENT -> UserConnectionState.UNKNOWN
    }

private fun KaliumUser.toUserView(): UserView =
    UserView(
        id = id,
        name = name,
        handle = handle,
        email = email,
        team = team,
        connection = connection,
    )

private fun List<KaliumUser>.toUserViews(): List<UserView> = map { it.toUserView() }

private fun String.toQualifiedIdOrNull(): UserId? {
    val trimmed = trim()
    val atIndex = trimmed.lastIndexOf('@')
    val isValidFormat = atIndex > 0 && atIndex < trimmed.lastIndex

    return if (isValidFormat) {
        val value = trimmed.substring(0, atIndex)
        val domain = trimmed.substring(atIndex + 1)
        if (value.isNotBlank() && domain.isNotBlank()) {
            QualifiedID(value = value, domain = domain)
        } else {
            null
        }
    } else {
        null
    }
}

private fun UserStepResult.Failure.toSearchFailure(): UserSearchResult.Failure {
    val (message, exitCode) = UserFailureMapper.toSearchFailureInfo(category)
    return UserSearchResult.Failure(message = message, exitCode = exitCode)
}

private fun UserStepResult.Failure.toGetFailure(): UserGetResult.Failure {
    val (message, exitCode) = UserFailureMapper.toGetFailureInfo(category)
    return UserGetResult.Failure(message = message, exitCode = exitCode)
}

internal object UserFailureMapper {
    data class FailureInfo(val message: String, val exitCode: Int)

    fun toSearchFailureInfo(category: UserFailureCategory): FailureInfo =
        FailureInfo(
            message =
                when (category) {
                    UserFailureCategory.NETWORK -> UserMessages.SEARCH_NETWORK_FAILURE
                    UserFailureCategory.SERVER -> UserMessages.SEARCH_SERVER_FAILURE
                    UserFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
                    UserFailureCategory.USER_NOT_FOUND -> UserMessages.NO_RESULTS
                    UserFailureCategory.UNKNOWN -> UserMessages.SEARCH_UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    fun toGetFailureInfo(category: UserFailureCategory): FailureInfo =
        FailureInfo(
            message =
                when (category) {
                    UserFailureCategory.NETWORK -> UserMessages.GET_NETWORK_FAILURE
                    UserFailureCategory.SERVER -> UserMessages.GET_SERVER_FAILURE
                    UserFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
                    UserFailureCategory.USER_NOT_FOUND -> UserMessages.USER_NOT_FOUND
                    UserFailureCategory.UNKNOWN -> UserMessages.GET_UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    private fun categoryToExitCode(category: UserFailureCategory): Int =
        when (category) {
            UserFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            UserFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            UserFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            UserFailureCategory.USER_NOT_FOUND -> UserExitCodes.NOT_FOUND
            UserFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }
}

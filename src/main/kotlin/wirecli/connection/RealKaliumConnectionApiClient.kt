package wirecli.connection

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationFilter
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.connection.BlockUserResult
import com.wire.kalium.logic.feature.connection.SendConnectionRequestResult
import com.wire.kalium.logic.feature.connection.UnblockUserResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.config.KaliumCliMode
import wirecli.config.kaliumCliConfigs
import wirecli.user.UserConnectionState

private val logger = KotlinLogging.logger {}

internal class RealKaliumConnectionApiClient(
    private val runtime: ConnectionRuntime,
) : ConnectionApiClient {
    override fun sendRequest(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        logger.debug { "RealKaliumConnectionApiClient: sending connection request to $userId" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is ConnectionStepResult.Success -> scope.value
                is ConnectionStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for request: ${scope.category}" }
                    return scope.toActionFailure(ActionKind.REQUEST)
                }
            }

        return when (val result = runtime.sendConnectionRequest(sessionScope, userId)) {
            is ConnectionStepResult.Success -> {
                logger.info { "Connection request handled with outcome ${result.value}" }
                result.value.toRequestActionResult()
            }

            is ConnectionStepResult.Failure -> {
                logger.warn { "Failed to send connection request: ${result.category}" }
                result.toActionFailure(ActionKind.REQUEST)
            }
        }
    }

    override fun blockUser(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        logger.debug { "RealKaliumConnectionApiClient: blocking user $userId" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is ConnectionStepResult.Success -> scope.value
                is ConnectionStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for block: ${scope.category}" }
                    return scope.toActionFailure(ActionKind.BLOCK)
                }
            }

        return when (val result = runtime.blockUser(sessionScope, userId)) {
            is ConnectionStepResult.Success -> {
                logger.info { "Block handled with outcome ${result.value}" }
                result.value.toBlockActionResult()
            }

            is ConnectionStepResult.Failure -> {
                logger.warn { "Failed to block user: ${result.category}" }
                result.toActionFailure(ActionKind.BLOCK)
            }
        }
    }

    override fun unblockUser(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        logger.debug { "RealKaliumConnectionApiClient: unblocking user $userId" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is ConnectionStepResult.Success -> scope.value
                is ConnectionStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for unblock: ${scope.category}" }
                    return scope.toActionFailure(ActionKind.UNBLOCK)
                }
            }

        return when (val result = runtime.unblockUser(sessionScope, userId)) {
            is ConnectionStepResult.Success -> {
                logger.info { "Unblock handled with outcome ${result.value}" }
                result.value.toUnblockActionResult()
            }

            is ConnectionStepResult.Failure -> {
                logger.warn { "Failed to unblock user: ${result.category}" }
                result.toActionFailure(ActionKind.UNBLOCK)
            }
        }
    }

    override fun listConnections(session: AuthSession): ConnectionListResult {
        logger.debug { "RealKaliumConnectionApiClient: listing connections" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is ConnectionStepResult.Success -> scope.value
                is ConnectionStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for list: ${scope.category}" }
                    return scope.toListFailure()
                }
            }

        return when (val result = runtime.listConnections(sessionScope)) {
            is ConnectionStepResult.Success -> {
                logger.info { "Connection list returned ${result.value.size} connection(s)" }
                val view =
                    ConnectionListView(
                        connections =
                            result.value.map { entry ->
                                ConnectionView(
                                    userId = entry.userId,
                                    userName = entry.userName,
                                    handle = entry.handle,
                                    status = entry.status,
                                    lastUpdate = entry.lastUpdate,
                                )
                            },
                    )
                ConnectionListResult.Success(view = view)
            }

            is ConnectionStepResult.Failure -> {
                logger.warn { "Failed to list connections: ${result.category}" }
                result.toListFailure()
            }
        }
    }
}

private enum class ActionKind { REQUEST, BLOCK, UNBLOCK, LIST }

private fun ConnectionOutcome.toRequestActionResult(): ConnectionActionResult =
    when (this) {
        ConnectionOutcome.SUCCESS -> ConnectionActionResult.Success(ConnectionMessages.REQUEST_SUCCESS)
        ConnectionOutcome.FEDERATION_DENIED ->
            ConnectionActionResult.Failure(ConnectionMessages.REQUEST_FEDERATION_DENIED, ConnectionExitCodes.CONFLICT)
        ConnectionOutcome.LEGAL_HOLD ->
            ConnectionActionResult.Failure(ConnectionMessages.REQUEST_LEGAL_HOLD, ConnectionExitCodes.CONFLICT)
        ConnectionOutcome.FAILURE ->
            ConnectionActionResult.Failure(
                ConnectionMessages.REQUEST_UNKNOWN_FAILURE,
                ExitCodes.UNKNOWN_ERROR,
            )
    }

private fun ConnectionOutcome.toBlockActionResult(): ConnectionActionResult =
    when (this) {
        ConnectionOutcome.SUCCESS -> ConnectionActionResult.Success(ConnectionMessages.BLOCK_SUCCESS)
        else ->
            ConnectionActionResult.Failure(
                ConnectionMessages.BLOCK_UNKNOWN_FAILURE,
                ExitCodes.UNKNOWN_ERROR,
            )
    }

private fun ConnectionOutcome.toUnblockActionResult(): ConnectionActionResult =
    when (this) {
        ConnectionOutcome.SUCCESS -> ConnectionActionResult.Success(ConnectionMessages.UNBLOCK_SUCCESS)
        else ->
            ConnectionActionResult.Failure(
                ConnectionMessages.UNBLOCK_UNKNOWN_FAILURE,
                ExitCodes.UNKNOWN_ERROR,
            )
    }

private fun ConnectionStepResult.Failure.toActionFailure(kind: ActionKind): ConnectionActionResult.Failure {
    val (message, exitCode) =
        when (kind) {
            ActionKind.REQUEST -> ConnectionFailureMapper.toRequestFailureInfo(category)
            ActionKind.BLOCK -> ConnectionFailureMapper.toBlockFailureInfo(category)
            ActionKind.UNBLOCK -> ConnectionFailureMapper.toUnblockFailureInfo(category)
            ActionKind.LIST -> ConnectionFailureMapper.toListFailureInfo(category)
        }
    return ConnectionActionResult.Failure(message = message, exitCode = exitCode)
}

private fun ConnectionStepResult.Failure.toListFailure(): ConnectionListResult.Failure {
    val (message, exitCode) = ConnectionFailureMapper.toListFailureInfo(category)
    return ConnectionListResult.Failure(message = message, exitCode = exitCode)
}

internal object ConnectionFailureMapper {
    data class FailureInfo(val message: String, val exitCode: Int)

    fun toRequestFailureInfo(category: ConnectionFailureCategory): FailureInfo =
        FailureInfo(
            message =
                when (category) {
                    ConnectionFailureCategory.NETWORK -> ConnectionMessages.REQUEST_NETWORK_FAILURE
                    ConnectionFailureCategory.SERVER -> ConnectionMessages.REQUEST_SERVER_FAILURE
                    ConnectionFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
                    ConnectionFailureCategory.USER_NOT_FOUND -> ConnectionMessages.USER_NOT_FOUND
                    ConnectionFailureCategory.FEDERATION_DENIED -> ConnectionMessages.REQUEST_FEDERATION_DENIED
                    ConnectionFailureCategory.LEGAL_HOLD -> ConnectionMessages.REQUEST_LEGAL_HOLD
                    ConnectionFailureCategory.UNKNOWN,
                    ConnectionFailureCategory.LIST_NETWORK,
                    ConnectionFailureCategory.LIST_SERVER,
                    ConnectionFailureCategory.LIST_UNKNOWN,
                    -> ConnectionMessages.REQUEST_UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    fun toBlockFailureInfo(category: ConnectionFailureCategory): FailureInfo =
        FailureInfo(
            message =
                when (category) {
                    ConnectionFailureCategory.NETWORK -> ConnectionMessages.BLOCK_NETWORK_FAILURE
                    ConnectionFailureCategory.SERVER -> ConnectionMessages.BLOCK_SERVER_FAILURE
                    ConnectionFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
                    ConnectionFailureCategory.USER_NOT_FOUND -> ConnectionMessages.USER_NOT_FOUND
                    ConnectionFailureCategory.FEDERATION_DENIED,
                    ConnectionFailureCategory.LEGAL_HOLD,
                    ConnectionFailureCategory.UNKNOWN,
                    ConnectionFailureCategory.LIST_NETWORK,
                    ConnectionFailureCategory.LIST_SERVER,
                    ConnectionFailureCategory.LIST_UNKNOWN,
                    -> ConnectionMessages.BLOCK_UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    fun toUnblockFailureInfo(category: ConnectionFailureCategory): FailureInfo =
        FailureInfo(
            message =
                when (category) {
                    ConnectionFailureCategory.NETWORK -> ConnectionMessages.UNBLOCK_NETWORK_FAILURE
                    ConnectionFailureCategory.SERVER -> ConnectionMessages.UNBLOCK_SERVER_FAILURE
                    ConnectionFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
                    ConnectionFailureCategory.USER_NOT_FOUND -> ConnectionMessages.USER_NOT_FOUND
                    ConnectionFailureCategory.FEDERATION_DENIED,
                    ConnectionFailureCategory.LEGAL_HOLD,
                    ConnectionFailureCategory.UNKNOWN,
                    ConnectionFailureCategory.LIST_NETWORK,
                    ConnectionFailureCategory.LIST_SERVER,
                    ConnectionFailureCategory.LIST_UNKNOWN,
                    -> ConnectionMessages.UNBLOCK_UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    fun toListFailureInfo(category: ConnectionFailureCategory): FailureInfo =
        FailureInfo(
            message =
                when (category) {
                    ConnectionFailureCategory.NETWORK,
                    ConnectionFailureCategory.LIST_NETWORK,
                    -> ConnectionMessages.LIST_NETWORK_FAILURE

                    ConnectionFailureCategory.SERVER,
                    ConnectionFailureCategory.LIST_SERVER,
                    -> ConnectionMessages.LIST_SERVER_FAILURE

                    ConnectionFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
                    ConnectionFailureCategory.USER_NOT_FOUND -> ConnectionMessages.USER_NOT_FOUND
                    ConnectionFailureCategory.FEDERATION_DENIED,
                    ConnectionFailureCategory.LEGAL_HOLD,
                    ConnectionFailureCategory.LIST_UNKNOWN,
                    ConnectionFailureCategory.UNKNOWN,
                    -> ConnectionMessages.LIST_UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    private fun categoryToExitCode(category: ConnectionFailureCategory): Int =
        when (category) {
            ConnectionFailureCategory.NETWORK,
            ConnectionFailureCategory.LIST_NETWORK,
            -> ExitCodes.NETWORK_ERROR

            ConnectionFailureCategory.SERVER,
            ConnectionFailureCategory.LIST_SERVER,
            -> ExitCodes.SERVER_ERROR

            ConnectionFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            ConnectionFailureCategory.USER_NOT_FOUND -> ConnectionExitCodes.NOT_FOUND
            ConnectionFailureCategory.FEDERATION_DENIED,
            ConnectionFailureCategory.LEGAL_HOLD,
            ConnectionFailureCategory.LIST_UNKNOWN,
            -> ConnectionExitCodes.CONFLICT
            ConnectionFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }
}

internal typealias RealKaliumConnectionRuntime = ConnectionRuntime

internal class SdkKaliumConnectionRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : ConnectionRuntime {
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

    override fun resolveSessionScope(session: AuthSession): ConnectionStepResult<KaliumConnectionSessionScope> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return ConnectionStepResult.Failure(ConnectionFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                if (!cliMode.disableSessionSyncWait) {
                    coreLogic.sessionScope(qualifiedId) {
                        syncExecutor.request { waitUntilLiveOrFailure() }
                    }
                }
                ConnectionStepResult.Success(
                    KaliumConnectionSessionScope(
                        userId = session.userId,
                        server = session.server,
                    ),
                )
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to resolve connection session scope for ${session.userId}" }
                ConnectionStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun sendConnectionRequest(
        sessionScope: KaliumConnectionSessionScope,
        userId: String,
    ): ConnectionStepResult<ConnectionOutcome> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return ConnectionStepResult.Failure(ConnectionFailureCategory.UNAUTHORIZED)
        val targetId =
            userId.toQualifiedIdOrNull()
                ?: return ConnectionStepResult.Failure(ConnectionFailureCategory.USER_NOT_FOUND)

        return runBlocking {
            try {
                val result =
                    coreLogic.sessionScope(qualifiedId) {
                        connection.sendConnectionRequest(targetId)
                    }

                when (result) {
                    is SendConnectionRequestResult.Success ->
                        ConnectionStepResult.Success(ConnectionOutcome.SUCCESS)

                    is SendConnectionRequestResult.Failure.FederationDenied ->
                        ConnectionStepResult.Success(ConnectionOutcome.FEDERATION_DENIED)

                    is SendConnectionRequestResult.Failure.MissingLegalHoldConsent ->
                        ConnectionStepResult.Success(ConnectionOutcome.LEGAL_HOLD)

                    is SendConnectionRequestResult.Failure.GenericFailure ->
                        ConnectionStepResult.Failure(categoryFromCoreFailure(result.coreFailure))
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to send connection request to $userId" }
                ConnectionStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun blockUser(
        sessionScope: KaliumConnectionSessionScope,
        userId: String,
    ): ConnectionStepResult<ConnectionOutcome> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return ConnectionStepResult.Failure(ConnectionFailureCategory.UNAUTHORIZED)
        val targetId =
            userId.toQualifiedIdOrNull()
                ?: return ConnectionStepResult.Failure(ConnectionFailureCategory.USER_NOT_FOUND)

        return runBlocking {
            try {
                val result =
                    coreLogic.sessionScope(qualifiedId) {
                        connection.blockUser(targetId)
                    }

                when (result) {
                    is BlockUserResult.Success ->
                        ConnectionStepResult.Success(ConnectionOutcome.SUCCESS)

                    is BlockUserResult.Failure ->
                        ConnectionStepResult.Failure(categoryFromCoreFailure(result.coreFailure))
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to block user $userId" }
                ConnectionStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun unblockUser(
        sessionScope: KaliumConnectionSessionScope,
        userId: String,
    ): ConnectionStepResult<ConnectionOutcome> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return ConnectionStepResult.Failure(ConnectionFailureCategory.UNAUTHORIZED)
        val targetId =
            userId.toQualifiedIdOrNull()
                ?: return ConnectionStepResult.Failure(ConnectionFailureCategory.USER_NOT_FOUND)

        return runBlocking {
            try {
                val result =
                    coreLogic.sessionScope(qualifiedId) {
                        connection.unblockUser(targetId)
                    }

                when (result) {
                    is UnblockUserResult.Success ->
                        ConnectionStepResult.Success(ConnectionOutcome.SUCCESS)

                    is UnblockUserResult.Failure ->
                        ConnectionStepResult.Failure(categoryFromCoreFailure(result.coreFailure))
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to unblock user $userId" }
                ConnectionStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun listConnections(sessionScope: KaliumConnectionSessionScope): ConnectionStepResult<List<KaliumConnectionEntry>> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return ConnectionStepResult.Failure(ConnectionFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                val conversationDetails =
                    coreLogic.sessionScope(qualifiedId) {
                        conversations.observeConversationListDetailsWithEvents(
                            fromArchive = false,
                            conversationFilter = ConversationFilter.All,
                        )
                            .firstOrNull()
                            ?.map { it.conversationDetails }
                            ?: emptyList()
                    }

                val entries =
                    conversationDetails
                        .flatMap { details ->
                            when (details) {
                                is ConversationDetails.Connection ->
                                    listOf(
                                        KaliumConnectionEntry(
                                            userId = details.connection.qualifiedToId.toString(),
                                            userName = details.otherUser?.name,
                                            handle = details.otherUser?.handle,
                                            status = details.connection.status.toUserConnectionState(),
                                            lastUpdate = details.connection.lastUpdate.toString(),
                                        ),
                                    )

                                is ConversationDetails.OneOne ->
                                    listOf(
                                        KaliumConnectionEntry(
                                            userId = details.otherUser.id.toString(),
                                            userName = details.otherUser.name,
                                            handle = details.otherUser.handle,
                                            status = details.otherUser.connectionStatus.toUserConnectionState(),
                                            lastUpdate = details.conversation.lastModifiedDate?.toString(),
                                        ),
                                    )

                                else -> emptyList()
                            }
                        }

                ConnectionStepResult.Success(entries)
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to list connections" }
                ConnectionStepResult.Failure(categoryFromThrowable(error))
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
        logger.debug { "SdkKaliumConnectionRuntime: shutdown complete" }
    }

    private fun categoryFromCoreFailure(failure: CoreFailure): ConnectionFailureCategory =
        when (failure) {
            is NetworkFailure.NoNetworkConnection,
            is NetworkFailure.ProxyError,
            -> ConnectionFailureCategory.NETWORK

            is NetworkFailure.ServerMiscommunication -> ConnectionFailureCategory.SERVER

            is NetworkFailure.FederatedBackendFailure,
            NetworkFailure.FeatureNotSupported,
            is NetworkFailure.MlsMessageRejectedFailure,
            -> ConnectionFailureCategory.SERVER

            else -> ConnectionFailureCategory.UNKNOWN
        }

    @Suppress("TooGenericExceptionCaught")
    private fun categoryFromThrowable(error: Throwable): ConnectionFailureCategory {
        val message = error.message.orEmpty()
        return when {
            message.contains("network", ignoreCase = true) -> ConnectionFailureCategory.NETWORK
            message.contains("unauthorized", ignoreCase = true) -> ConnectionFailureCategory.UNAUTHORIZED
            message.contains("auth", ignoreCase = true) -> ConnectionFailureCategory.UNAUTHORIZED
            message.contains("not found", ignoreCase = true) -> ConnectionFailureCategory.USER_NOT_FOUND
            message.isNotEmpty() -> ConnectionFailureCategory.SERVER
            else -> ConnectionFailureCategory.UNKNOWN
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

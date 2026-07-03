package wirecli.connection

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.connection.BlockUserResult
import com.wire.kalium.logic.feature.connection.SendConnectionRequestResult
import com.wire.kalium.logic.feature.connection.UnblockUserResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.config.KaliumCliMode
import wirecli.config.kaliumCliConfigs

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
}

private enum class ActionKind { REQUEST, BLOCK, UNBLOCK }

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
        }
    return ConnectionActionResult.Failure(message = message, exitCode = exitCode)
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
                    ConnectionFailureCategory.UNKNOWN -> ConnectionMessages.REQUEST_UNKNOWN_FAILURE
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
                    -> ConnectionMessages.UNBLOCK_UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    private fun categoryToExitCode(category: ConnectionFailureCategory): Int =
        when (category) {
            ConnectionFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            ConnectionFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            ConnectionFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            ConnectionFailureCategory.USER_NOT_FOUND -> ConnectionExitCodes.NOT_FOUND
            ConnectionFailureCategory.FEDERATION_DENIED,
            ConnectionFailureCategory.LEGAL_HOLD,
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

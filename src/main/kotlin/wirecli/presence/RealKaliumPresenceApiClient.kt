package wirecli.presence

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs

private val logger = KotlinLogging.logger {}

internal class RealKaliumPresenceApiClient(
    private val runtime: RealKaliumPresenceRuntime,
) : PresenceApiClient {
    override fun fetchPresence(session: AuthSession): PresenceResult {
        logger.debug { "RealKaliumPresenceApiClient: Fetching presence for user: ${session.userId}" }
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is PresenceStepResult.Success -> scope.value
                is PresenceStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for presence fetch: ${scope.category}" }
                    return scope.toPresenceFailure()
                }
            }

        return when (val status = runtime.getSelfAvailabilityStatus(sessionScope)) {
            is PresenceStepResult.Success -> {
                val normalizedState = PresenceNormalizer.normalize(status.value.toWirePresenceRawValue())
                logger.info { "Successfully retrieved presence: $normalizedState" }
                PresenceResult.Success(
                    presence =
                        PresenceView(
                            state = normalizedState,
                        ),
                )
            }

            is PresenceStepResult.Failure -> {
                logger.warn { "Failed to get self availability status: ${status.category}" }
                status.toPresenceFailure()
            }
        }
    }

    override fun updatePresence(
        session: AuthSession,
        state: WritablePresenceState,
    ): PresenceResult {
        logger.info { "Updating presence to: $state for user: ${session.userId}" }
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is PresenceStepResult.Success -> scope.value
                is PresenceStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for presence update: ${scope.category}" }
                    return scope.toSetPresenceFailure()
                }
            }

        return when (val result = runtime.setSelfAvailabilityStatus(sessionScope, state.toKaliumAvailabilityStatus())) {
            is PresenceStepResult.Success -> {
                val normalizedState = PresenceNormalizer.normalize(state.value)
                logger.info { "Presence updated successfully: $normalizedState" }
                PresenceResult.Success(
                    presence = PresenceView(state = normalizedState),
                )
            }

            is PresenceStepResult.Failure -> {
                logger.warn { "Failed to set self availability status: ${result.category}" }
                result.toSetPresenceFailure()
            }
        }
    }
}

internal interface RealKaliumPresenceRuntime {
    fun resolveSessionScope(session: AuthSession): PresenceStepResult<KaliumPresenceSessionScope>

    fun getSelfAvailabilityStatus(sessionScope: KaliumPresenceSessionScope): PresenceStepResult<UserAvailabilityStatus>

    fun setSelfAvailabilityStatus(
        sessionScope: KaliumPresenceSessionScope,
        status: UserAvailabilityStatus,
    ): PresenceStepResult<Unit>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

internal data class KaliumPresenceSessionScope(
    val userId: String,
    val server: String?,
)

internal sealed interface PresenceStepResult<out T> {
    data class Success<T>(val value: T) : PresenceStepResult<T>

    data class Failure(val category: PresenceFailureCategory) : PresenceStepResult<Nothing>
}

internal enum class PresenceFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    UNKNOWN,
}

internal class SdkKaliumPresenceRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : RealKaliumPresenceRuntime {
    private val activeSessionUserIds = mutableSetOf<UserId>()

    private val coreLogicLazy =
        lazy {
            logger.debug { "SdkKaliumPresenceRuntime: Initializing Kalium CoreLogic for presence runtime" }
            val rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium"
            logger.debug { "SdkKaliumPresenceRuntime: Kalium data path: $rootPath" }
            val configs = kaliumCliConfigs(cliMode)
            logger.debug { "SdkKaliumPresenceRuntime: Kalium configs loaded for mode: $cliMode" }
            CoreLogic(
                rootPath = rootPath,
                kaliumConfigs = configs,
                userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
            )
        }
    private val coreLogic: CoreLogic by coreLogicLazy

    override fun resolveSessionScope(session: AuthSession): PresenceStepResult<KaliumPresenceSessionScope> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "Invalid user ID format: ${session.userId}" }
                    return PresenceStepResult.Failure(PresenceFailureCategory.UNAUTHORIZED)
                }

        return runBlocking {
            try {
                if (!cliMode.disableSessionSyncWait) {
                    coreLogic.sessionScope(qualifiedId) {
                        syncExecutor.request { waitUntilLiveOrFailure() }
                    }
                }
                activeSessionUserIds += qualifiedId
                logger.debug { "Presence session scope resolved successfully for user: ${session.userId}" }
                PresenceStepResult.Success(
                    KaliumPresenceSessionScope(
                        userId = session.userId,
                        server = session.server,
                    ),
                )
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to resolve presence session scope for user: ${session.userId}" }
                PresenceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getSelfAvailabilityStatus(sessionScope: KaliumPresenceSessionScope): PresenceStepResult<UserAvailabilityStatus> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "Invalid user ID format for presence: ${sessionScope.userId}" }
                    return PresenceStepResult.Failure(PresenceFailureCategory.UNAUTHORIZED)
                }

        return runBlocking {
            try {
                logger.debug { "Fetching self availability status for: $qualifiedId" }
                val selfUser =
                    coreLogic.sessionScope(qualifiedId) {
                        users.getSelfUser()
                    }
                        ?: error(
                            "Self user data is unavailable - this indicates a failure to fetch presence information.",
                        )
                // Explicitly fail if availability status is null instead of returning it
                val status =
                    checkNotNull(selfUser.availabilityStatus) {
                        "Availability status is null - cannot determine presence state. " +
                            "This indicates a failure to fetch user availability data."
                    }
                logger.debug { "Self availability status retrieved successfully: $status" }
                PresenceStepResult.Success(status)
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to get self availability status for: $qualifiedId" }
                PresenceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun setSelfAvailabilityStatus(
        sessionScope: KaliumPresenceSessionScope,
        status: UserAvailabilityStatus,
    ): PresenceStepResult<Unit> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "Invalid user ID format for setting presence: ${sessionScope.userId}" }
                    return PresenceStepResult.Failure(PresenceFailureCategory.UNAUTHORIZED)
                }

        return runBlocking {
            try {
                logger.debug { "Setting self availability status to: $status for: $qualifiedId" }
                coreLogic.sessionScope(qualifiedId) {
                    users.updateSelfAvailabilityStatus(status)
                }
                logger.debug { "Self availability status updated successfully" }
                PresenceStepResult.Success(Unit)
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to set self availability status for: $qualifiedId" }
                PresenceStepResult.Failure(categoryFromThrowable(error))
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
        coreLogic.getGlobalScope().cancel()
        logger.debug { "SdkKaliumPresenceRuntime: Presence runtime shutdown complete" }
    }

    // Follow-up: consider more robust error categorization (e.g., using exception types)
    private fun categoryFromThrowable(error: Throwable): PresenceFailureCategory {
        val message = error.message.orEmpty()
        return when {
            message.contains("network", ignoreCase = true) -> PresenceFailureCategory.NETWORK
            message.contains("unauthorized", ignoreCase = true) -> PresenceFailureCategory.UNAUTHORIZED
            message.contains("auth", ignoreCase = true) -> PresenceFailureCategory.UNAUTHORIZED
            message.isNotEmpty() -> PresenceFailureCategory.SERVER
            else -> PresenceFailureCategory.UNKNOWN
        }
    }

    private fun resolveHomeDirectory(env: Map<String, String>): String {
        val home = env["HOME"]?.trim()
        if (!home.isNullOrEmpty()) return home
        return System.getProperty("user.home")
    }
}

private fun UserAvailabilityStatus.toWirePresenceRawValue(): String {
    return when (this) {
        UserAvailabilityStatus.AVAILABLE -> "online"
        UserAvailabilityStatus.BUSY -> "busy"
        UserAvailabilityStatus.AWAY -> "away"
        UserAvailabilityStatus.NONE -> "offline"
    }
}

private fun WritablePresenceState.toKaliumAvailabilityStatus(): UserAvailabilityStatus {
    return when (this) {
        WritablePresenceState.ONLINE -> UserAvailabilityStatus.AVAILABLE
        WritablePresenceState.BUSY -> UserAvailabilityStatus.BUSY
        WritablePresenceState.AWAY -> UserAvailabilityStatus.AWAY
        WritablePresenceState.OFFLINE -> UserAvailabilityStatus.NONE
    }
}

private fun String.toQualifiedIdOrNull(): UserId? {
    val trimmed = trim()
    val atIndex = trimmed.lastIndexOf('@')
    val isValidFormat = atIndex > 0 && atIndex < trimmed.lastIndex

    return if (isValidFormat) {
        val value = trimmed.substring(0, atIndex)
        val domain = trimmed.substring(atIndex + 1)
        if (value.isNotBlank() && domain.isNotBlank()) UserId(value = value, domain = domain) else null
    } else {
        null
    }
}

private fun PresenceStepResult.Failure.toPresenceFailure(): PresenceResult.Failure {
    val message =
        when (category) {
            PresenceFailureCategory.NETWORK -> PresenceMessages.NETWORK_FAILURE
            PresenceFailureCategory.SERVER -> PresenceMessages.SERVER_FAILURE
            PresenceFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
            PresenceFailureCategory.UNKNOWN -> PresenceMessages.UNKNOWN_FAILURE
        }

    val exitCode =
        when (category) {
            PresenceFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            PresenceFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            PresenceFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            PresenceFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }

    return PresenceResult.Failure(message = message, exitCode = exitCode)
}

private fun PresenceStepResult.Failure.toSetPresenceFailure(): PresenceResult.Failure {
    val message =
        when (category) {
            PresenceFailureCategory.NETWORK -> PresenceMessages.SET_NETWORK_FAILURE
            PresenceFailureCategory.SERVER -> PresenceMessages.SET_SERVER_FAILURE
            PresenceFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
            PresenceFailureCategory.UNKNOWN -> PresenceMessages.SET_UNKNOWN_FAILURE
        }

    val exitCode =
        when (category) {
            PresenceFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            PresenceFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            PresenceFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            PresenceFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }

    return PresenceResult.Failure(message = message, exitCode = exitCode)
}

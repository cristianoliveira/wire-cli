package wirecli.presence

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs

internal class RealKaliumPresenceApiClient(
    private val runtime: RealKaliumPresenceRuntime,
) : PresenceApiClient {
    override fun fetchPresence(session: AuthSession): PresenceResult {
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is PresenceStepResult.Success -> scope.value
                is PresenceStepResult.Failure -> return scope.toPresenceFailure()
            }

        return when (val status = runtime.getSelfAvailabilityStatus(sessionScope)) {
            is PresenceStepResult.Success ->
                PresenceResult.Success(
                    presence =
                        PresenceView(
                            state = PresenceNormalizer.normalize(status.value.toWirePresenceRawValue()),
                        ),
                )

            is PresenceStepResult.Failure -> status.toPresenceFailure()
        }
    }

    override fun updatePresence(
        session: AuthSession,
        state: WritablePresenceState,
    ): PresenceResult {
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is PresenceStepResult.Success -> scope.value
                is PresenceStepResult.Failure -> return scope.toSetPresenceFailure()
            }

        return when (val result = runtime.setSelfAvailabilityStatus(sessionScope, state.toKaliumAvailabilityStatus())) {
            is PresenceStepResult.Success ->
                PresenceResult.Success(
                    presence = PresenceView(state = PresenceNormalizer.normalize(state.value)),
                )

            is PresenceStepResult.Failure -> result.toSetPresenceFailure()
        }
    }
}

internal interface RealKaliumPresenceRuntime {
    fun resolveSessionScope(session: AuthSession): PresenceStepResult<KaliumPresenceSessionScope>

    fun getSelfAvailabilityStatus(sessionScope: KaliumPresenceSessionScope): PresenceStepResult<UserAvailabilityStatus?>

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
            CoreLogic(
                rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium",
                kaliumConfigs = kaliumCliConfigs(cliMode),
                userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
            )
        }
    private val coreLogic: CoreLogic by coreLogicLazy

    override fun resolveSessionScope(session: AuthSession): PresenceStepResult<KaliumPresenceSessionScope> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return PresenceStepResult.Failure(PresenceFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                if (!cliMode.disableSessionSyncWait) {
                    coreLogic.sessionScope(qualifiedId) {
                        syncExecutor.request { waitUntilLiveOrFailure() }
                    }
                }
                activeSessionUserIds += qualifiedId
                PresenceStepResult.Success(
                    KaliumPresenceSessionScope(
                        userId = session.userId,
                        server = session.server,
                    ),
                )
            } catch (error: Throwable) {
                PresenceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getSelfAvailabilityStatus(sessionScope: KaliumPresenceSessionScope): PresenceStepResult<UserAvailabilityStatus?> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return PresenceStepResult.Failure(PresenceFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                val selfUser =
                    coreLogic.sessionScope(qualifiedId) {
                        users.getSelfUser()
                    }

                if (selfUser == null) {
                    PresenceStepResult.Failure(PresenceFailureCategory.UNAUTHORIZED)
                } else {
                    PresenceStepResult.Success(selfUser.availabilityStatus)
                }
            } catch (error: Throwable) {
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
                ?: return PresenceStepResult.Failure(PresenceFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                coreLogic.sessionScope(qualifiedId) {
                    users.updateSelfAvailabilityStatus(status)
                }
                PresenceStepResult.Success(Unit)
            } catch (error: Throwable) {
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
    }

    // TODO: Consider more robust error categorization (e.g., using exception types)
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

private fun UserAvailabilityStatus?.toWirePresenceRawValue(): String? {
    return when (this) {
        UserAvailabilityStatus.AVAILABLE -> "online"
        UserAvailabilityStatus.BUSY -> "busy"
        UserAvailabilityStatus.AWAY -> "away"
        UserAvailabilityStatus.NONE -> "offline"
        null -> null
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
    if (atIndex <= 0 || atIndex == trimmed.lastIndex) return null
    val value = trimmed.substring(0, atIndex)
    val domain = trimmed.substring(atIndex + 1)
    if (value.isBlank() || domain.isBlank()) return null
    return UserId(value = value, domain = domain)
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

package wirecli.profile

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs
import wirecli.shared.ProfileError
import wirecli.shared.Result

private val logger = KotlinLogging.logger {}

internal class RealKaliumProfileApiClient(
    private val runtime: RealKaliumProfileRuntime,
) : ProfileApiClient {
    override fun fetchProfile(session: AuthSession): ProfileResult<ProfileView> {
        logger.debug { "RealKaliumProfileApiClient: Fetching profile for user: ${session.userId}" }
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is ProfileStepResult.Success -> scope.value
                is ProfileStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for profile: ${scope.category}" }
                    return scope.toProfileFailure()
                }
            }

        return when (val selfUser = runtime.getSelfUser(sessionScope)) {
            is ProfileStepResult.Success -> {
                logger.info {
                    "Successfully retrieved profile: name=${selfUser.value.name}, email=${selfUser.value.email}, handle=${selfUser.value.handle}"
                }
                Result.Success(
                    value =
                        ProfileView(
                            name = selfUser.value.name,
                            email = selfUser.value.email,
                            handle = selfUser.value.handle,
                        ),
                )
            }

            is ProfileStepResult.Failure -> {
                logger.warn { "Failed to retrieve self user: ${selfUser.category}" }
                selfUser.toProfileFailure()
            }
        }
    }
}

internal interface RealKaliumProfileRuntime {
    fun resolveSessionScope(session: AuthSession): ProfileStepResult<KaliumProfileSessionScope>

    fun getSelfUser(sessionScope: KaliumProfileSessionScope): ProfileStepResult<KaliumSelfUser>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

internal data class KaliumProfileSessionScope(
    val userId: String,
    val server: String?,
)

internal data class KaliumSelfUser(
    val name: String?,
    val email: String?,
    val handle: String?,
)

internal sealed interface ProfileStepResult<out T> {
    data class Success<T>(val value: T) : ProfileStepResult<T>

    data class Failure(val category: ProfileFailureCategory) : ProfileStepResult<Nothing>
}

internal enum class ProfileFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    UNKNOWN,
}

internal class SdkKaliumProfileRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : RealKaliumProfileRuntime {
    private val activeSessionUserIds = mutableSetOf<UserId>()

    private val coreLogicLazy =
        lazy {
            logger.debug { "SdkKaliumProfileRuntime: Initializing Kalium CoreLogic for profile runtime" }
            val rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium"
            logger.debug { "SdkKaliumProfileRuntime: Kalium data path: $rootPath" }
            val configs = kaliumCliConfigs(cliMode)
            logger.debug { "SdkKaliumProfileRuntime: Kalium configs loaded for mode: $cliMode" }
            CoreLogic(
                rootPath = rootPath,
                kaliumConfigs = configs,
                userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
            )
        }
    private val coreLogic: CoreLogic by coreLogicLazy

    override fun resolveSessionScope(session: AuthSession): ProfileStepResult<KaliumProfileSessionScope> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "Invalid user ID format: ${session.userId}" }
                    return ProfileStepResult.Failure(ProfileFailureCategory.UNAUTHORIZED)
                }
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                if (!cliMode.disableSessionSyncWait) {
                    coreLogic.sessionScope(qualifiedId) {
                        syncExecutor.request { waitUntilLiveOrFailure() }
                    }
                }
                logger.debug { "Profile session scope resolved successfully for user: ${session.userId}" }
                ProfileStepResult.Success(
                    KaliumProfileSessionScope(
                        userId = session.userId,
                        server = session.server,
                    ),
                )
            } catch (error: Throwable) {
                logger.error(error) { "Failed to resolve profile session scope for user: ${session.userId}" }
                ProfileStepResult.Failure(categoryFromThrowable(error))
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
        logger.debug { "SdkKaliumProfileRuntime: Profile runtime shutdown complete" }
    }

    override fun getSelfUser(sessionScope: KaliumProfileSessionScope): ProfileStepResult<KaliumSelfUser> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "Invalid user ID format for self user: ${sessionScope.userId}" }
                    return ProfileStepResult.Failure(ProfileFailureCategory.UNAUTHORIZED)
                }

        return runBlocking {
            try {
                logger.debug { "Fetching self user data for: $qualifiedId" }
                val selfUser: SelfUser =
                    coreLogic.sessionScope(qualifiedId) {
                        users.getSelfUser()
                    }
                        ?: throw IllegalStateException(
                            "Self user data is unavailable - this indicates a failure to fetch profile information.",
                        )
                logger.debug { "Self user data retrieved successfully: name=${selfUser.name}, handle=${selfUser.handle}" }
                ProfileStepResult.Success(
                    KaliumSelfUser(
                        name = selfUser.name,
                        email = selfUser.email,
                        handle = selfUser.handle,
                    ),
                )
            } catch (error: Throwable) {
                logger.error(error) { "Failed to fetch self user for: $qualifiedId" }
                ProfileStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    private fun categoryFromThrowable(error: Throwable): ProfileFailureCategory {
        val message = error.message.orEmpty()
        return when {
            message.contains("network", ignoreCase = true) -> ProfileFailureCategory.NETWORK
            message.contains("unauthorized", ignoreCase = true) -> ProfileFailureCategory.UNAUTHORIZED
            message.contains("auth", ignoreCase = true) -> ProfileFailureCategory.UNAUTHORIZED
            message.isNotEmpty() -> ProfileFailureCategory.SERVER
            else -> ProfileFailureCategory.UNKNOWN
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
    if (atIndex <= 0 || atIndex == trimmed.lastIndex) return null
    val value = trimmed.substring(0, atIndex)
    val domain = trimmed.substring(atIndex + 1)
    if (value.isBlank() || domain.isBlank()) return null
    return UserId(value = value, domain = domain)
}

private fun ProfileStepResult.Failure.toProfileFailure(): Result.Failure<ProfileError> {
    val message =
        when (category) {
            ProfileFailureCategory.NETWORK -> "Profile fetch failed: network is unreachable. Check your connection and retry."
            ProfileFailureCategory.SERVER -> "Profile service is unavailable. Retry later or check server settings."
            ProfileFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
            ProfileFailureCategory.UNKNOWN -> "Profile fetch failed unexpectedly. Retry and check your setup."
        }

    val exitCode =
        when (category) {
            ProfileFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            ProfileFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            ProfileFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            ProfileFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }

    return Result.Failure(error = ProfileError(message = message, exitCode = exitCode))
}

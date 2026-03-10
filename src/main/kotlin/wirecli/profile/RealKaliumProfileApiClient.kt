package wirecli.profile

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.runtime.kaliumCliConfigs

internal class RealKaliumProfileApiClient(
    private val runtime: RealKaliumProfileRuntime
) : ProfileApiClient {
    override fun fetchProfile(session: AuthSession): ProfileResult {
        val sessionScope = when (val scope = runtime.resolveSessionScope(session)) {
            is ProfileStepResult.Success -> scope.value
            is ProfileStepResult.Failure -> return scope.toProfileFailure()
        }

        return when (val selfUser = runtime.getSelfUser(sessionScope)) {
            is ProfileStepResult.Success -> ProfileResult.Success(
                profile = ProfileView(
                    name = selfUser.value.name,
                    email = selfUser.value.email,
                    handle = selfUser.value.handle
                )
            )

            is ProfileStepResult.Failure -> selfUser.toProfileFailure()
        }
    }
}

internal interface RealKaliumProfileRuntime {
    fun resolveSessionScope(session: AuthSession): ProfileStepResult<KaliumProfileSessionScope>
    fun getSelfUser(sessionScope: KaliumProfileSessionScope): ProfileStepResult<KaliumSelfUser>
}

internal data class KaliumProfileSessionScope(
    val userId: String,
    val server: String?
)

internal data class KaliumSelfUser(
    val name: String?,
    val email: String?,
    val handle: String?
)

internal sealed interface ProfileStepResult<out T> {
    data class Success<T>(val value: T) : ProfileStepResult<T>
    data class Failure(val category: ProfileFailureCategory) : ProfileStepResult<Nothing>
}

internal enum class ProfileFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    UNKNOWN
}

internal class SdkKaliumProfileRuntime(
    private val environment: Map<String, String>
) : RealKaliumProfileRuntime {
    private val coreLogic: CoreLogic by lazy {
        CoreLogic(
            rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium",
            kaliumConfigs = kaliumCliConfigs(),
            userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}"
        )
    }

    override fun resolveSessionScope(session: AuthSession): ProfileStepResult<KaliumProfileSessionScope> {
        val qualifiedId = session.userId.toQualifiedIdOrNull()
            ?: return ProfileStepResult.Failure(ProfileFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                coreLogic.sessionScope(qualifiedId) {
                    syncExecutor.request { waitUntilLiveOrFailure() }
                }
                ProfileStepResult.Success(
                    KaliumProfileSessionScope(
                        userId = session.userId,
                        server = session.server
                    )
                )
            } catch (error: Throwable) {
                ProfileStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getSelfUser(sessionScope: KaliumProfileSessionScope): ProfileStepResult<KaliumSelfUser> {
        val qualifiedId = sessionScope.userId.toQualifiedIdOrNull()
            ?: return ProfileStepResult.Failure(ProfileFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                val selfUser: SelfUser? = coreLogic.sessionScope(qualifiedId) {
                    users.getSelfUser()
                }
                if (selfUser == null) {
                    ProfileStepResult.Failure(ProfileFailureCategory.UNAUTHORIZED)
                } else {
                    ProfileStepResult.Success(
                        KaliumSelfUser(
                            name = selfUser.name,
                            email = selfUser.email,
                            handle = selfUser.handle
                        )
                    )
                }
            } catch (error: Throwable) {
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

private fun ProfileStepResult.Failure.toProfileFailure(): ProfileResult.Failure {
    val message = when (category) {
        ProfileFailureCategory.NETWORK -> "Profile fetch failed: network is unreachable. Check your connection and retry."
        ProfileFailureCategory.SERVER -> "Profile service is unavailable. Retry later or check server settings."
        ProfileFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
        ProfileFailureCategory.UNKNOWN -> "Profile fetch failed unexpectedly. Retry and check your setup."
    }

    val exitCode = when (category) {
        ProfileFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
        ProfileFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
        ProfileFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
        ProfileFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
    }

    return ProfileResult.Failure(message = message, exitCode = exitCode)
}

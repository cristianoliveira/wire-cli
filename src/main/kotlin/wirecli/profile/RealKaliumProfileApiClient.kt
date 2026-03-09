package wirecli.profile

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.EnvironmentKaliumAuthRuntime

internal class RealProfileApiClient(
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

internal class EnvironmentKaliumProfileRuntime(
    private val environment: Map<String, String>
) : RealKaliumProfileRuntime {
    private val mode = environment[EnvironmentKaliumAuthRuntime.ENV_REAL_MODE]?.trim().orEmpty()

    override fun resolveSessionScope(session: AuthSession): ProfileStepResult<KaliumProfileSessionScope> {
        val failure = failureForStep("session")
        if (failure != null) return failure

        return ProfileStepResult.Success(
            KaliumProfileSessionScope(
                userId = session.userId,
                server = session.server
            )
        )
    }

    override fun getSelfUser(sessionScope: KaliumProfileSessionScope): ProfileStepResult<KaliumSelfUser> {
        val failure = failureForStep("self_user")
        if (failure != null) return failure

        val profile = when (mode) {
            "profile_missing_optional" -> KaliumSelfUser(
                name = null,
                email = null,
                handle = null
            )

            else -> KaliumSelfUser(
                name = "Real ${sessionScope.userId}",
                email = "${sessionScope.userId}@wire.test",
                handle = sessionScope.userId
            )
        }

        return ProfileStepResult.Success(profile)
    }

    private fun failureForStep(step: String): ProfileStepResult.Failure? {
        val explicit = environment[EnvironmentKaliumAuthRuntime.ENV_REAL_FAIL_STEP]?.trim()?.lowercase()
        if (explicit == step) {
            return categoryForMode(mode)
        }

        if (mode == "profile_$step") {
            return ProfileStepResult.Failure(ProfileFailureCategory.SERVER)
        }

        return when (mode) {
            "network", "network_error" -> ProfileStepResult.Failure(ProfileFailureCategory.NETWORK)
            "server", "server_error" -> ProfileStepResult.Failure(ProfileFailureCategory.SERVER)
            "unauthorized" -> ProfileStepResult.Failure(ProfileFailureCategory.UNAUTHORIZED)
            "unknown" -> ProfileStepResult.Failure(ProfileFailureCategory.UNKNOWN)
            else -> null
        }
    }

    private fun categoryForMode(rawMode: String): ProfileStepResult.Failure {
        return when (rawMode) {
            "network", "network_error" -> ProfileStepResult.Failure(ProfileFailureCategory.NETWORK)
            "unauthorized" -> ProfileStepResult.Failure(ProfileFailureCategory.UNAUTHORIZED)
            "unknown" -> ProfileStepResult.Failure(ProfileFailureCategory.UNKNOWN)
            else -> ProfileStepResult.Failure(ProfileFailureCategory.SERVER)
        }
    }
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

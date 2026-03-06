package com.example.wirecli.runtime

import com.example.wirecli.auth.AuthResult
import com.example.wirecli.auth.AuthSessionService
import com.example.wirecli.auth.ExitCodes
import com.example.wirecli.auth.LoginInput
import com.example.wirecli.profile.ProfileResult
import com.example.wirecli.profile.ProfileService

interface KaliumRuntime {
    val authSessionService: AuthSessionService
    val profileService: ProfileService
}

object KaliumRuntimeBootstrap {
    // TODO: Replace placeholder services with real implementations.
    fun create(): KaliumRuntime = DefaultKaliumRuntime
}

private object DefaultKaliumRuntime : KaliumRuntime {
    override val authSessionService: AuthSessionService = PlaceholderAuthSessionService
    override val profileService: ProfileService = PlaceholderProfileService
}

private object PlaceholderAuthSessionService : AuthSessionService {
    override fun login(input: LoginInput): AuthResult =
        AuthResult.Failure(
            message = "Login is not implemented yet.",
            exitCode = ExitCodes.UNKNOWN_ERROR
        )

    override fun logout(): AuthResult =
        AuthResult.Failure(
            message = "Logout is not implemented yet.",
            exitCode = ExitCodes.UNKNOWN_ERROR
        )

    override fun requireActiveSession(): AuthResult =
        AuthResult.Failure(
            message = "No active session. Run wire login.",
            exitCode = ExitCodes.UNAUTHORIZED
        )
}

private object PlaceholderProfileService : ProfileService {
    override fun getCurrentProfile(): ProfileResult =
        ProfileResult.Failure(
            message = "Profile is not implemented yet.",
            exitCode = ExitCodes.UNKNOWN_ERROR
        )
}

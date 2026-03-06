package com.example.wirecli.runtime

import com.example.wirecli.auth.AuthSessionService
import com.example.wirecli.auth.AuthSessionServiceImpl
import com.example.wirecli.auth.ExitCodes
import com.example.wirecli.auth.FileAuthSessionStore
import com.example.wirecli.auth.StubAuthApiClient
import com.example.wirecli.profile.AuthGuardedProfileService
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
    override val authSessionService: AuthSessionService = AuthSessionServiceImpl(
        apiClient = StubAuthApiClient(System.getenv()),
        sessionStore = FileAuthSessionStore()
    )
    override val profileService: ProfileService = AuthGuardedProfileService(
        authSessionService = authSessionService,
        delegate = PlaceholderProfileService
    )
}

private object PlaceholderProfileService : ProfileService {
    override fun getCurrentProfile(): ProfileResult =
        ProfileResult.Failure(
            message = "Profile is not implemented yet.",
            exitCode = ExitCodes.UNKNOWN_ERROR
        )
}

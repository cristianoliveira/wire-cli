package com.example.wirecli.runtime

import com.example.wirecli.auth.AuthSessionService
import com.example.wirecli.auth.AuthSessionServiceImpl
import com.example.wirecli.auth.FileAuthSessionStore
import com.example.wirecli.auth.StubAuthApiClient
import com.example.wirecli.profile.AuthGuardedProfileService
import com.example.wirecli.profile.ProfileService
import com.example.wirecli.profile.SessionBackedProfileService
import com.example.wirecli.profile.StubProfileApiClient

interface KaliumRuntime {
    val authSessionService: AuthSessionService
    val profileService: ProfileService
}

object KaliumRuntimeBootstrap {
    // TODO: Replace placeholder services with real implementations.
    fun create(): KaliumRuntime = DefaultKaliumRuntime
}

private object DefaultKaliumRuntime : KaliumRuntime {
    private val sessionStore = FileAuthSessionStore()

    override val authSessionService: AuthSessionService = AuthSessionServiceImpl(
        apiClient = StubAuthApiClient(System.getenv()),
        sessionStore = sessionStore
    )

    override val profileService: ProfileService = AuthGuardedProfileService(
        authSessionService = authSessionService,
        delegate = SessionBackedProfileService(
            sessionStore = sessionStore,
            apiClient = StubProfileApiClient(System.getenv())
        )
    )
}

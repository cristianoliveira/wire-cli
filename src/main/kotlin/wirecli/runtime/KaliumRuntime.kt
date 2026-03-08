package wirecli.runtime

import wirecli.auth.AuthSessionService
import wirecli.auth.AuthSessionServiceImpl
import wirecli.auth.FileAuthSessionStore
import wirecli.auth.StubAuthApiClient
import wirecli.profile.AuthGuardedProfileService
import wirecli.profile.ProfileService
import wirecli.profile.SessionBackedProfileService
import wirecli.profile.StubProfileApiClient

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

package wirecli.runtime

import java.util.Locale
import wirecli.auth.AuthApiClient
import wirecli.auth.RealKaliumAuthClient
import wirecli.auth.SdkKaliumAuthRuntime
import wirecli.auth.AuthSessionService
import wirecli.auth.AuthSessionServiceImpl
import wirecli.auth.FileAuthSessionStore
import wirecli.auth.StubAuthApiClient
import wirecli.profile.AuthGuardedProfileService
import wirecli.profile.ProfileApiClient
import wirecli.profile.RealKaliumProfileApiClient
import wirecli.profile.SdkKaliumProfileRuntime
import wirecli.profile.ProfileService
import wirecli.profile.SessionBackedProfileService
import wirecli.profile.StubProfileApiClient
import wirecli.presence.PresenceApiClient
import wirecli.presence.PresenceService
import wirecli.presence.AuthGuardedPresenceService
import wirecli.presence.RealKaliumPresenceApiClient
import wirecli.presence.SessionBackedPresenceService
import wirecli.presence.SdkKaliumPresenceRuntime
import wirecli.presence.StubPresenceApiClient

interface KaliumRuntime {
    val authSessionService: AuthSessionService
    val profileService: ProfileService
    val presenceService: PresenceService
    fun shutdown()
}

object KaliumRuntimeBootstrap {
    // Supported selector values: stub | real
    const val ENV_BACKEND_SELECTOR = "WIRE_BACKEND"
    const val PROPERTY_BACKEND_SELECTOR = "wire.backend"

    fun create(): KaliumRuntime {
        val environment = System.getenv()
        val backend = RuntimeBackendSelector.resolve(
            configBackend = System.getProperty(PROPERTY_BACKEND_SELECTOR),
            environmentBackend = environment[ENV_BACKEND_SELECTOR]
        )
        return DefaultKaliumRuntime(
            environment = environment,
            backendFactory = backend.factory
        )
    }
}

private class DefaultKaliumRuntime(
    environment: Map<String, String>,
    backendFactory: RuntimeBackendFactory
) : KaliumRuntime {
    private val sessionStore = FileAuthSessionStore()
    private val backend = backendFactory.create(environment)

    override val authSessionService: AuthSessionService = AuthSessionServiceImpl(
        apiClient = backend.authApiClient,
        sessionStore = sessionStore
    )

    override val profileService: ProfileService = AuthGuardedProfileService(
        authSessionService = authSessionService,
        delegate = SessionBackedProfileService(
            sessionStore = sessionStore,
            apiClient = backend.profileApiClient,
            presenceApiClient = backend.presenceApiClient
        )
    )

    override val presenceService: PresenceService = AuthGuardedPresenceService(
        authSessionService = authSessionService,
        delegate = SessionBackedPresenceService(
            sessionStore = sessionStore,
            apiClient = backend.presenceApiClient
        )
    )

    override fun shutdown() {
        backend.shutdown()
    }
}

private enum class RuntimeBackendSelector(val factory: RuntimeBackendFactory) {
    STUB(StubRuntimeBackendFactory),
    REAL(RealRuntimeBackendFactory);

    companion object {
        // Deterministic default for local and test execution.
        private val default = STUB

        fun resolve(configBackend: String?, environmentBackend: String?): RuntimeBackendSelector {
            return parse(configBackend)
                ?: parse(environmentBackend)
                ?: default
        }

        private fun parse(rawValue: String?): RuntimeBackendSelector? {
            if (rawValue == null) return null

            return entries.firstOrNull { backend ->
                backend.name.equals(rawValue.trim().uppercase(Locale.US), ignoreCase = false)
            }
        }
    }
}

private interface RuntimeBackendFactory {
    fun create(environment: Map<String, String>): RuntimeBackend
}

private interface RuntimeBackend {
    val authApiClient: AuthApiClient
    val profileApiClient: ProfileApiClient
    val presenceApiClient: PresenceApiClient
    fun shutdown()
}

private object StubRuntimeBackendFactory : RuntimeBackendFactory {
    override fun create(environment: Map<String, String>): RuntimeBackend {
        return object : RuntimeBackend {
            override val authApiClient: AuthApiClient = StubAuthApiClient(environment)
            override val profileApiClient: ProfileApiClient = StubProfileApiClient(environment)
            override val presenceApiClient: PresenceApiClient = StubPresenceApiClient(environment)

            override fun shutdown() {
                // No background resources in stub backend.
            }
        }
    }
}

private object RealRuntimeBackendFactory : RuntimeBackendFactory {
    override fun create(environment: Map<String, String>): RuntimeBackend {
        return object : RuntimeBackend {
            private val authRuntime = SdkKaliumAuthRuntime(environment)
            private val profileRuntime = SdkKaliumProfileRuntime(environment)
            private val presenceRuntime = SdkKaliumPresenceRuntime(environment)

            override val authApiClient: AuthApiClient = RealKaliumAuthClient(authRuntime)
            override val profileApiClient: ProfileApiClient = RealKaliumProfileApiClient(profileRuntime)
            override val presenceApiClient: PresenceApiClient = RealKaliumPresenceApiClient(presenceRuntime)

            override fun shutdown() {
                authRuntime.shutdown()
                profileRuntime.shutdown()
                presenceRuntime.shutdown()
            }
        }
    }
}

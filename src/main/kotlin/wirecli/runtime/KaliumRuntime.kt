package wirecli.runtime

import wirecli.auth.AuthApiClient
import wirecli.auth.AuthSessionService
import wirecli.auth.AuthSessionServiceImpl
import wirecli.auth.FileAuthSessionStore
import wirecli.auth.RealKaliumAuthClient
import wirecli.auth.SdkKaliumAuthRuntime
import wirecli.auth.StubAuthApiClient
import wirecli.presence.AuthGuardedPresenceService
import wirecli.presence.PresenceApiClient
import wirecli.presence.PresenceService
import wirecli.presence.RealKaliumPresenceApiClient
import wirecli.presence.SdkKaliumPresenceRuntime
import wirecli.presence.SessionBackedPresenceService
import wirecli.presence.StubPresenceApiClient
import wirecli.profile.AuthGuardedProfileService
import wirecli.profile.ProfileApiClient
import wirecli.profile.ProfileService
import wirecli.profile.RealKaliumProfileApiClient
import wirecli.profile.SdkKaliumProfileRuntime
import wirecli.profile.SessionBackedProfileService
import wirecli.profile.StubProfileApiClient
import java.util.Locale

interface KaliumRuntime : AutoCloseable {
    val authSessionService: AuthSessionService
    val profileService: ProfileService
    val presenceService: PresenceService

    fun shutdown()

    override fun close() {
        shutdown()
    }
}

object KaliumRuntimeBootstrap {
    // Supported selector values: stub | real
    const val ENV_BACKEND_SELECTOR = "WIRE_BACKEND"

    fun create(): KaliumRuntime {
        val environment = System.getenv()
        val backend =
            RuntimeBackendSelector.resolve(
                environmentBackend = environment[ENV_BACKEND_SELECTOR],
            )
        return createWithBackend(environment, backend.factory)
    }

    internal fun createWithBackend(
        environment: Map<String, String>,
        backendFactory: RuntimeBackendFactory,
    ): KaliumRuntime {
        return DefaultKaliumRuntime(
            environment = environment,
            backendFactory = backendFactory,
        )
    }

    internal fun resolveBackendForTests(environmentBackend: String?): String {
        return RuntimeBackendSelector.resolve(environmentBackend).name
    }
}

private class DefaultKaliumRuntime(
    private val environment: Map<String, String>,
    backendFactory: RuntimeBackendFactory,
) : KaliumRuntime {
    private val sessionStore = FileAuthSessionStore()
    private val backendLazy = lazy { backendFactory.create(environment) }
    private val backend by backendLazy

    override val authSessionService: AuthSessionService by lazy {
        AuthSessionServiceImpl(
            apiClient = backend.authApiClient,
            sessionStore = sessionStore,
        )
    }

    override val profileService: ProfileService by lazy {
        AuthGuardedProfileService(
            authSessionService = authSessionService,
            delegate =
                SessionBackedProfileService(
                    sessionStore = sessionStore,
                    apiClient = backend.profileApiClient,
                    presenceApiClient = backend.presenceApiClient,
                ),
        )
    }

    override val presenceService: PresenceService by lazy {
        AuthGuardedPresenceService(
            authSessionService = authSessionService,
            delegate =
                SessionBackedPresenceService(
                    sessionStore = sessionStore,
                    apiClient = backend.presenceApiClient,
                ),
        )
    }

    override fun shutdown() {
        if (!backendLazy.isInitialized()) return
        backend.shutdown()
    }
}

private enum class RuntimeBackendSelector(val factory: RuntimeBackendFactory) {
    STUB(StubRuntimeBackendFactory),
    REAL(RealRuntimeBackendFactory),
    ;

    companion object {
        // Real backend is the default; stub mode requires explicit env override.
        private val default = REAL

        fun resolve(environmentBackend: String?): RuntimeBackendSelector {
            return parse(environmentBackend)
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

internal interface RuntimeBackendFactory {
    fun create(environment: Map<String, String>): RuntimeBackend
}

internal interface RuntimeBackend {
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
            private val authRuntimeLazy = lazy { SdkKaliumAuthRuntime(environment) }
            private val profileRuntimeLazy = lazy { SdkKaliumProfileRuntime(environment) }
            private val presenceRuntimeLazy = lazy { SdkKaliumPresenceRuntime(environment) }

            private val authRuntime by authRuntimeLazy
            private val profileRuntime by profileRuntimeLazy
            private val presenceRuntime by presenceRuntimeLazy

            override val authApiClient: AuthApiClient by lazy { RealKaliumAuthClient(authRuntime) }
            override val profileApiClient: ProfileApiClient by lazy { RealKaliumProfileApiClient(profileRuntime) }
            override val presenceApiClient: PresenceApiClient by lazy { RealKaliumPresenceApiClient(presenceRuntime) }

            override fun shutdown() {
                if (authRuntimeLazy.isInitialized()) authRuntime.close()
                if (profileRuntimeLazy.isInitialized()) profileRuntime.close()
                if (presenceRuntimeLazy.isInitialized()) presenceRuntime.close()
            }
        }
    }
}

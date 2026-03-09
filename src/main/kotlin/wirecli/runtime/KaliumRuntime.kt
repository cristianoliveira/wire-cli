package wirecli.runtime

import java.util.Locale
import wirecli.auth.AuthApiClient
import wirecli.auth.AuthSession
import wirecli.auth.AuthSessionService
import wirecli.auth.AuthSessionServiceImpl
import wirecli.auth.ExitCodes
import wirecli.auth.EnvironmentKaliumAuthRuntime
import wirecli.auth.FileAuthSessionStore
import wirecli.auth.RealAuthApiClient
import wirecli.auth.StubAuthApiClient
import wirecli.profile.AuthGuardedProfileService
import wirecli.profile.ProfileApiClient
import wirecli.profile.ProfileResult
import wirecli.profile.ProfileService
import wirecli.profile.SessionBackedProfileService
import wirecli.profile.StubProfileApiClient

interface KaliumRuntime {
    val authSessionService: AuthSessionService
    val profileService: ProfileService
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

    override val authSessionService: AuthSessionService = AuthSessionServiceImpl(
        apiClient = backendFactory.createAuthApiClient(environment),
        sessionStore = sessionStore
    )

    override val profileService: ProfileService = AuthGuardedProfileService(
        authSessionService = authSessionService,
        delegate = SessionBackedProfileService(
            sessionStore = sessionStore,
            apiClient = backendFactory.createProfileApiClient(environment)
        )
    )
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
    fun createAuthApiClient(environment: Map<String, String>): AuthApiClient
    fun createProfileApiClient(environment: Map<String, String>): ProfileApiClient
}

private object StubRuntimeBackendFactory : RuntimeBackendFactory {
    override fun createAuthApiClient(environment: Map<String, String>): AuthApiClient {
        return StubAuthApiClient(environment)
    }

    override fun createProfileApiClient(environment: Map<String, String>): ProfileApiClient {
        return StubProfileApiClient(environment)
    }
}

private object RealRuntimeBackendFactory : RuntimeBackendFactory {
    override fun createAuthApiClient(environment: Map<String, String>): AuthApiClient {
        return RealAuthApiClient(
            runtime = EnvironmentKaliumAuthRuntime(environment)
        )
    }

    override fun createProfileApiClient(environment: Map<String, String>): ProfileApiClient {
        return RealProfileApiClient
    }
}

private object RealProfileApiClient : ProfileApiClient {
    override fun fetchProfile(session: AuthSession): ProfileResult {
        return ProfileResult.Failure(
            message = "Real profile backend is selected but not wired yet.",
            exitCode = ExitCodes.SERVER_ERROR
        )
    }
}

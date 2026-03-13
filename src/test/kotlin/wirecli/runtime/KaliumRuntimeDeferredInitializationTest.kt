package wirecli.runtime

import wirecli.auth.AuthApiClient
import wirecli.auth.AuthApiResult
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.LoginInput
import wirecli.presence.PresenceApiClient
import wirecli.presence.PresenceResult
import wirecli.presence.WritablePresenceState
import wirecli.profile.ProfileApiClient
import wirecli.profile.ProfileResult
import kotlin.test.Test
import kotlin.test.assertEquals

class KaliumRuntimeDeferredInitializationTest {
    @Test
    fun `auth service initialization does not touch profile and presence clients`() {
        val counters = BackendCounters()
        val runtime = KaliumRuntimeBootstrap.createWithBackend(emptyMap(), countingBackendFactory(counters))

        runtime.authSessionService

        assertEquals(1, counters.authApiClientAccesses)
        assertEquals(0, counters.profileApiClientAccesses)
        assertEquals(0, counters.presenceApiClientAccesses)

        runtime.close()
    }

    @Test
    fun `shutdown does not initialize backend when no service is used`() {
        val counters = BackendCounters()
        val runtime = KaliumRuntimeBootstrap.createWithBackend(emptyMap(), countingBackendFactory(counters))

        runtime.close()

        assertEquals(0, counters.authApiClientAccesses)
        assertEquals(0, counters.profileApiClientAccesses)
        assertEquals(0, counters.presenceApiClientAccesses)
        assertEquals(0, counters.shutdownCalls)
    }
}

private data class BackendCounters(
    var authApiClientAccesses: Int = 0,
    var profileApiClientAccesses: Int = 0,
    var presenceApiClientAccesses: Int = 0,
    var shutdownCalls: Int = 0,
)

private fun countingBackendFactory(counters: BackendCounters): RuntimeBackendFactory {
    return object : RuntimeBackendFactory {
        override fun create(environment: Map<String, String>): RuntimeBackend {
            return object : RuntimeBackend {
                override val authApiClient: AuthApiClient
                    get() {
                        counters.authApiClientAccesses += 1
                        return NoopAuthApiClient
                    }

                override val profileApiClient: ProfileApiClient
                    get() {
                        counters.profileApiClientAccesses += 1
                        return NoopProfileApiClient
                    }

                override val presenceApiClient: PresenceApiClient
                    get() {
                        counters.presenceApiClientAccesses += 1
                        return NoopPresenceApiClient
                    }

                override fun shutdown() {
                    counters.shutdownCalls += 1
                }
            }
        }
    }
}

private object NoopAuthApiClient : AuthApiClient {
    override fun login(input: LoginInput): AuthApiResult {
        return AuthApiResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun logout(session: AuthSession): AuthApiResult {
        return AuthApiResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }
}

private object NoopProfileApiClient : ProfileApiClient {
    override fun fetchProfile(session: AuthSession): ProfileResult {
        return ProfileResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }
}

private object NoopPresenceApiClient : PresenceApiClient {
    override fun fetchPresence(session: AuthSession): PresenceResult {
        return PresenceResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }

    override fun updatePresence(
        session: AuthSession,
        state: WritablePresenceState,
    ): PresenceResult {
        return PresenceResult.Failure("not used", ExitCodes.UNKNOWN_ERROR)
    }
}

package wirecli.presence

import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

class RealKaliumPresenceApiClientTest {
    private val session = AuthSession(
        userId = "jane@example.com",
        accessToken = "token",
        server = null
    )

    @Test
    fun `returns normalized presence from runtime status`() {
        val runtime = FakeRuntime(
            sessionScopeResult = PresenceStepResult.Success(KaliumPresenceSessionScope(session.userId, session.server)),
            statusResult = PresenceStepResult.Success(UserAvailabilityStatus.AVAILABLE)
        )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.ONLINE, success.presence.state)
    }

    @Test
    fun `maps NONE availability to offline`() {
        val runtime = FakeRuntime(
            sessionScopeResult = PresenceStepResult.Success(KaliumPresenceSessionScope(session.userId, session.server)),
            statusResult = PresenceStepResult.Success(UserAvailabilityStatus.NONE)
        )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.OFFLINE, success.presence.state)
    }

    @Test
    fun `maps null availability to unknown`() {
        val runtime = FakeRuntime(
            sessionScopeResult = PresenceStepResult.Success(KaliumPresenceSessionScope(session.userId, session.server)),
            statusResult = PresenceStepResult.Success(null)
        )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.UNKNOWN, success.presence.state)
    }

    @Test
    fun `maps unauthorized failure to auth semantics`() {
        val runtime = FakeRuntime(
            sessionScopeResult = PresenceStepResult.Failure(PresenceFailureCategory.UNAUTHORIZED),
            statusResult = PresenceStepResult.Success(UserAvailabilityStatus.AWAY)
        )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `maps network failure to retry semantics`() {
        val runtime = FakeRuntime(
            sessionScopeResult = PresenceStepResult.Success(KaliumPresenceSessionScope(session.userId, session.server)),
            statusResult = PresenceStepResult.Failure(PresenceFailureCategory.NETWORK)
        )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(
            "Presence fetch failed: network is unreachable. Check your connection and retry.",
            failure.message
        )
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `maps server failure to server semantics`() {
        val runtime = FakeRuntime(
            sessionScopeResult = PresenceStepResult.Success(KaliumPresenceSessionScope(session.userId, session.server)),
            statusResult = PresenceStepResult.Failure(PresenceFailureCategory.SERVER)
        )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(
            "Presence service is unavailable. Retry later or check server settings.",
            failure.message
        )
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    private class FakeRuntime(
        private val sessionScopeResult: PresenceStepResult<KaliumPresenceSessionScope>,
        private val statusResult: PresenceStepResult<UserAvailabilityStatus?>
    ) : RealKaliumPresenceRuntime {
        override fun resolveSessionScope(session: AuthSession): PresenceStepResult<KaliumPresenceSessionScope> {
            return sessionScopeResult
        }

        override fun getSelfAvailabilityStatus(sessionScope: KaliumPresenceSessionScope): PresenceStepResult<UserAvailabilityStatus?> {
            return statusResult
        }

        override fun shutdown() {
        }
    }
}

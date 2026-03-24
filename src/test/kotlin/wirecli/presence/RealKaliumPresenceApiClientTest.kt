package wirecli.presence

import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RealKaliumPresenceApiClientTest {
    private val session =
        AuthSession(
            userId = "jane@example.com",
            accessToken = "token",
            server = null,
        )

    @Test
    fun `returns normalized presence from runtime status`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult =
                    PresenceStepResult.Success(
                        KaliumPresenceSessionScope(session.userId, session.server),
                    ),
                statusResult = PresenceStepResult.Success(UserAvailabilityStatus.AVAILABLE),
            )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.ONLINE, success.presence.state)
    }

    @Test
    fun `maps NONE availability to offline`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult =
                    PresenceStepResult.Success(
                        KaliumPresenceSessionScope(session.userId, session.server),
                    ),
                statusResult = PresenceStepResult.Success(UserAvailabilityStatus.NONE),
            )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.OFFLINE, success.presence.state)
    }

    @Test
    fun `fails explicitly when availability status is null`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult =
                    PresenceStepResult.Success(
                        KaliumPresenceSessionScope(session.userId, session.server),
                    ),
                statusResult = PresenceStepResult.Failure(PresenceFailureCategory.UNKNOWN),
            )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        // Now explicitly fails instead of silently returning null
        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(ExitCodes.UNKNOWN_ERROR, failure.exitCode)
    }

    @Test
    fun `maps unauthorized failure to auth semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = PresenceStepResult.Failure(PresenceFailureCategory.UNAUTHORIZED),
                statusResult = PresenceStepResult.Success(UserAvailabilityStatus.AWAY),
            )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `maps network failure to retry semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult =
                    PresenceStepResult.Success(
                        KaliumPresenceSessionScope(session.userId, session.server),
                    ),
                statusResult = PresenceStepResult.Failure(PresenceFailureCategory.NETWORK),
            )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(
            "Presence fetch failed: network is unreachable. Check your connection and retry.",
            failure.message,
        )
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `maps server failure to server semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult =
                    PresenceStepResult.Success(
                        KaliumPresenceSessionScope(session.userId, session.server),
                    ),
                statusResult = PresenceStepResult.Failure(PresenceFailureCategory.SERVER),
            )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.fetchPresence(session)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(
            "Presence service is unavailable. Retry later or check server settings.",
            failure.message,
        )
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `updates availability status when set succeeds`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult =
                    PresenceStepResult.Success(
                        KaliumPresenceSessionScope(session.userId, session.server),
                    ),
                statusResult = PresenceStepResult.Success(UserAvailabilityStatus.AWAY),
                setResult = PresenceStepResult.Success(Unit),
            )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.updatePresence(session, WritablePresenceState.BUSY)

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.BUSY, success.presence.state)
        assertEquals(UserAvailabilityStatus.BUSY, runtime.lastSetStatus)
    }

    @Test
    fun `maps unauthorized failure for set to auth semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult = PresenceStepResult.Failure(PresenceFailureCategory.UNAUTHORIZED),
                statusResult = PresenceStepResult.Success(UserAvailabilityStatus.AWAY),
                setResult = PresenceStepResult.Success(Unit),
            )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.updatePresence(session, WritablePresenceState.ONLINE)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `maps network failure for set to retry semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult =
                    PresenceStepResult.Success(
                        KaliumPresenceSessionScope(session.userId, session.server),
                    ),
                statusResult = PresenceStepResult.Success(UserAvailabilityStatus.AWAY),
                setResult = PresenceStepResult.Failure(PresenceFailureCategory.NETWORK),
            )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.updatePresence(session, WritablePresenceState.AWAY)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(
            "Presence update failed: network is unreachable. Check your connection and retry.",
            failure.message,
        )
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `maps server failure for set to server semantics`() {
        val runtime =
            FakeRuntime(
                sessionScopeResult =
                    PresenceStepResult.Success(
                        KaliumPresenceSessionScope(session.userId, session.server),
                    ),
                statusResult = PresenceStepResult.Success(UserAvailabilityStatus.AWAY),
                setResult = PresenceStepResult.Failure(PresenceFailureCategory.SERVER),
            )
        val client = RealKaliumPresenceApiClient(runtime)

        val result = client.updatePresence(session, WritablePresenceState.OFFLINE)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(
            "Presence update could not be completed. Retry later or check server settings.",
            failure.message,
        )
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    private class FakeRuntime(
        private val sessionScopeResult: PresenceStepResult<KaliumPresenceSessionScope>,
        private val statusResult: PresenceStepResult<UserAvailabilityStatus>,
        private val setResult: PresenceStepResult<Unit> = PresenceStepResult.Success(Unit),
    ) : RealKaliumPresenceRuntime {
        var lastSetStatus: UserAvailabilityStatus? = null

        override fun resolveSessionScope(session: AuthSession): PresenceStepResult<KaliumPresenceSessionScope> {
            return sessionScopeResult
        }

        override fun getSelfAvailabilityStatus(sessionScope: KaliumPresenceSessionScope): PresenceStepResult<UserAvailabilityStatus> {
            return statusResult
        }

        override fun setSelfAvailabilityStatus(
            sessionScope: KaliumPresenceSessionScope,
            status: UserAvailabilityStatus,
        ): PresenceStepResult<Unit> {
            lastSetStatus = status
            return setResult
        }

        override fun shutdown() {
            // No-op for test stub
        }
    }
}

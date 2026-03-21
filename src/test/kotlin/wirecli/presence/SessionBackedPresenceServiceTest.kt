package wirecli.presence

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionBackedPresenceServiceTest {
    @Test
    fun `returns unauthorized when no session is persisted`() {
        val service =
            SessionBackedPresenceService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient = FakePresenceApiClient(PresenceResult.Success(PresenceView(PresenceState.ONLINE))),
            )

        val result = service.getCurrentPresence()

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns backend presence result for persisted session`() {
        val expected: PresenceResult = PresenceResult.Success(PresenceView(PresenceState.BUSY))
        val service =
            SessionBackedPresenceService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "jane@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = FakePresenceApiClient(expected),
            )

        val result = service.getCurrentPresence()

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.BUSY, success.presence.state)
    }

    @Test
    fun `returns unauthorized for set when no session is persisted`() {
        val service =
            SessionBackedPresenceService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient = FakePresenceApiClient(PresenceResult.Success(PresenceView(PresenceState.ONLINE))),
            )

        val result = service.setCurrentPresence(WritablePresenceState.AWAY)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates set to backend for persisted session`() {
        val expected: PresenceResult = PresenceResult.Success(PresenceView(PresenceState.AWAY))
        val apiClient = FakePresenceApiClient(expected)
        val service =
            SessionBackedPresenceService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "jane@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = apiClient,
            )

        val result = service.setCurrentPresence(WritablePresenceState.AWAY)

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.AWAY, success.presence.state)
        assertEquals(WritablePresenceState.AWAY, apiClient.lastSetState)
    }

    private class FakeSessionStore(private val activeSession: AuthSession?) : SessionProvider {
        override fun readActiveSession(): AuthSession? = activeSession
    }

    private class FakePresenceApiClient(private val result: PresenceResult) : PresenceApiClient {
        var lastSetState: WritablePresenceState? = null

        override fun fetchPresence(session: AuthSession): PresenceResult = result

        override fun updatePresence(
            session: AuthSession,
            state: WritablePresenceState,
        ): PresenceResult {
            lastSetState = state
            return result
        }
    }
}

package wirecli.presence

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes
import wirecli.auth.SessionInventory
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

    private class FakeSessionStore(private val activeSession: AuthSession?) : AuthSessionStore {
        override fun readActiveSession(): AuthSession? = activeSession

        override fun readSessionInventory(): SessionInventory =
            SessionInventory(
                activeSession = activeSession,
                validSessions = if (activeSession == null) 0 else 1,
                invalidSessions = 0,
            )

        override fun writeActiveSession(session: AuthSession) {
        }

        override fun clearActiveSession() {
        }
    }

    private class FakePresenceApiClient(private val result: PresenceResult) : PresenceApiClient {
        override fun fetchPresence(session: AuthSession): PresenceResult = result
    }
}

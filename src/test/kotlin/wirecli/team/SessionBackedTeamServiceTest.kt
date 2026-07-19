package wirecli.team

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionBackedTeamServiceTest {
    private val session =
        AuthSession(
            userId = "alice@example.com",
            accessToken = "token",
            server = null,
        )

    @Test
    fun `delegates to api client when session exists`() {
        val sessionStore = FakeSessionStore(session)
        val apiClient = StubTeamApiClient(mapOf("WIRE_STUB_MODE" to "read_ok"))
        val service = SessionBackedTeamService(sessionStore, apiClient)

        val result = service.read()

        val success = assertIs<TeamReadResult.Success>(result)
        assertEquals("team-001", success.view.id)
    }

    @Test
    fun `returns failure when no active session`() {
        val sessionStore = FakeSessionStore(null)
        val apiClient = StubTeamApiClient(emptyMap())
        val service = SessionBackedTeamService(sessionStore, apiClient)

        val result = service.read()

        val failure = assertIs<TeamReadResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns failure when api client returns failure`() {
        val sessionStore = FakeSessionStore(session)
        val apiClient = StubTeamApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))
        val service = SessionBackedTeamService(sessionStore, apiClient)

        val result = service.read()

        val failure = assertIs<TeamReadResult.Failure>(result)
        assertEquals(TeamMessages.SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns no team failure from api client`() {
        val sessionStore = FakeSessionStore(session)
        val apiClient = StubTeamApiClient(mapOf("WIRE_STUB_MODE" to "no_team"))
        val service = SessionBackedTeamService(sessionStore, apiClient)

        val result = service.read()

        val failure = assertIs<TeamReadResult.Failure>(result)
        assertEquals(TeamMessages.NOT_A_TEAM_MEMBER, failure.message)
        assertEquals(TeamExitCodes.NO_TEAM, failure.exitCode)
    }

    private class FakeSessionStore(private val activeSession: AuthSession?) : SessionProvider {
        override fun readActiveSession(): AuthSession? = activeSession
    }
}

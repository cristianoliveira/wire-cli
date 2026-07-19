package wirecli.team

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StubTeamApiClientTest {
    private val session =
        AuthSession(
            userId = "alice@example.com",
            accessToken = "token",
            server = null,
        )

    @Test
    fun `returns team by default`() {
        val client = StubTeamApiClient(emptyMap())

        val result = client.readTeam(session)

        val success = assertIs<TeamReadResult.Success>(result)
        assertEquals("team-001", success.view.id)
        assertEquals("Acme Corp", success.view.name)
        assertEquals("default", success.view.icon)
        assertEquals("creator@example.com", success.view.creator)
        assertEquals(true, success.view.binding)
    }

    @Test
    fun `returns team in read_ok mode`() {
        val client = StubTeamApiClient(mapOf("WIRE_STUB_MODE" to "read_ok"))

        val result = client.readTeam(session)

        val success = assertIs<TeamReadResult.Success>(result)
        assertEquals("team-001", success.view.id)
    }

    @Test
    fun `returns no team failure in no_team mode`() {
        val client = StubTeamApiClient(mapOf("WIRE_STUB_MODE" to "no_team"))

        val result = client.readTeam(session)

        val failure = assertIs<TeamReadResult.Failure>(result)
        assertEquals(TeamMessages.NOT_A_TEAM_MEMBER, failure.message)
        assertEquals(TeamExitCodes.NO_TEAM, failure.exitCode)
    }

    @Test
    fun `returns not found failure in not_found mode`() {
        val client = StubTeamApiClient(mapOf("WIRE_STUB_MODE" to "not_found"))

        val result = client.readTeam(session)

        val failure = assertIs<TeamReadResult.Failure>(result)
        assertEquals(TeamMessages.TEAM_NOT_FOUND, failure.message)
        assertEquals(TeamExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns server error failure in server_error mode`() {
        val client = StubTeamApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))

        val result = client.readTeam(session)

        val failure = assertIs<TeamReadResult.Failure>(result)
        assertEquals(TeamMessages.SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized failure in unauthorized mode`() {
        val client = StubTeamApiClient(mapOf("WIRE_STUB_MODE" to "unauthorized"))

        val result = client.readTeam(session)

        val failure = assertIs<TeamReadResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }
}

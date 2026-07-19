package wirecli.team

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import wirecli.auth.LoginInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthGuardedTeamServiceTest {
    @Test
    fun `returns auth failure when read called without session`() {
        val service =
            AuthGuardedTeamService(
                authSessionService = FakeAuthSessionService(isAuthorized = false),
                delegate = FakeTeamService(),
            )

        val result = service.read()

        val failure = assertIs<TeamReadResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates read when session is valid`() {
        val expectedTeam = TeamView("team-001", "Acme Corp", "default", "creator@example.com", true)
        val delegate =
            FakeTeamService(
                TeamReadResult.Success(expectedTeam),
            )
        val service =
            AuthGuardedTeamService(
                authSessionService = FakeAuthSessionService(isAuthorized = true),
                delegate = delegate,
            )

        val result = service.read()

        val success = assertIs<TeamReadResult.Success>(result)
        assertEquals("team-001", success.view.id)
        assertEquals("Acme Corp", success.view.name)
    }

    @Test
    fun `returns failure when delegate returns failure`() {
        val delegate =
            FakeTeamService(
                TeamReadResult.Failure("No team", TeamExitCodes.NO_TEAM),
            )
        val service =
            AuthGuardedTeamService(
                authSessionService = FakeAuthSessionService(isAuthorized = true),
                delegate = delegate,
            )

        val result = service.read()

        val failure = assertIs<TeamReadResult.Failure>(result)
        assertEquals("No team", failure.message)
        assertEquals(TeamExitCodes.NO_TEAM, failure.exitCode)
    }

    private class FakeAuthSessionService(private val isAuthorized: Boolean) : AuthSessionService {
        override fun login(input: LoginInput): AuthResult {
            throw NotImplementedError()
        }

        override fun logout(): AuthResult {
            throw NotImplementedError()
        }

        override fun requireActiveSession(): AuthResult {
            return if (isAuthorized) {
                AuthResult.Success("Session is valid")
            } else {
                AuthResult.Failure("No active session", ExitCodes.UNAUTHORIZED)
            }
        }
    }

    private class FakeTeamService(
        private val readResult: TeamReadResult =
            TeamReadResult.Success(
                TeamView("team-001", "Acme Corp", "default", "creator@example.com", true),
            ),
    ) : TeamService {
        override fun read(): TeamReadResult = readResult
    }
}

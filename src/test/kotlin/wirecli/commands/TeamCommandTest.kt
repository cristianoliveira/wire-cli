package wirecli.commands

import wirecli.team.TeamReadResult
import wirecli.team.TeamService
import wirecli.team.TeamView
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamCommandTest {
    private val testTeam =
        TeamView(
            id = "team-001",
            name = "Acme Corp",
            icon = "default",
            creator = "creator@example.com",
            binding = true,
        )

    @Test
    fun `read command returns team details`() {
        val service =
            FakeTeamService(
                readResult = TeamReadResult.Success(testTeam),
            )

        val result = service.read()

        val success = result as TeamReadResult.Success
        assertEquals("team-001", success.view.id)
        assertEquals("Acme Corp", success.view.name)
        assertEquals("default", success.view.icon)
        assertEquals("creator@example.com", success.view.creator)
        assertEquals(true, success.view.binding)
    }

    @Test
    fun `read command handles no team failure`() {
        val service =
            FakeTeamService(
                readResult =
                    TeamReadResult.Failure(
                        message = "You are not a member of a team.",
                        exitCode = 14,
                    ),
            )

        val result = service.read()

        val failure = result as TeamReadResult.Failure
        assertEquals(14, failure.exitCode)
        assertEquals("You are not a member of a team.", failure.message)
    }

    @Test
    fun `read command handles unauthorized failure`() {
        val service =
            FakeTeamService(
                readResult =
                    TeamReadResult.Failure(
                        message = "Session is invalid or expired. Please log in again.",
                        exitCode = 11,
                    ),
            )

        val result = service.read()

        val failure = result as TeamReadResult.Failure
        assertEquals(11, failure.exitCode)
    }

    @Test
    fun `read command handles server error`() {
        val service =
            FakeTeamService(
                readResult =
                    TeamReadResult.Failure(
                        message = "Team service is unavailable.",
                        exitCode = 13,
                    ),
            )

        val result = service.read()

        val failure = result as TeamReadResult.Failure
        assertEquals(13, failure.exitCode)
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

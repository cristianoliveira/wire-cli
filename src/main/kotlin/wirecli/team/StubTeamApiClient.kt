package wirecli.team

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

class StubTeamApiClient(
    private val environment: Map<String, String>,
) : TeamApiClient {
    private val defaultTeam =
        TeamView(
            id = "team-001",
            name = "Acme Corp",
            icon = "default",
            creator = "creator@example.com",
            binding = true,
        )

    override fun readTeam(session: AuthSession): TeamReadResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "read_ok" ->
                TeamReadResult.Success(view = defaultTeam)

            "no_team" ->
                TeamReadResult.Failure(
                    message = TeamMessages.NOT_A_TEAM_MEMBER,
                    exitCode = TeamExitCodes.NO_TEAM,
                )

            "not_found" ->
                TeamReadResult.Failure(
                    message = TeamMessages.TEAM_NOT_FOUND,
                    exitCode = TeamExitCodes.NOT_FOUND,
                )

            "server_error" ->
                TeamReadResult.Failure(
                    message = TeamMessages.SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "unauthorized" ->
                TeamReadResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else ->
                TeamReadResult.Success(view = defaultTeam)
        }
    }
}

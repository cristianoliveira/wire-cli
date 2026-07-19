package wirecli.team

import wirecli.auth.AuthSession

data class TeamView(
    val id: String,
    val name: String,
    val icon: String,
    val creator: String,
    val binding: Boolean,
)

sealed interface TeamReadResult {
    data class Success(val view: TeamView) : TeamReadResult

    data class Failure(val message: String, val exitCode: Int) : TeamReadResult
}

interface TeamApiClient {
    fun readTeam(session: AuthSession): TeamReadResult
}

interface TeamService {
    fun read(): TeamReadResult
}

object TeamExitCodes {
    const val OK = 0
    const val UNAUTHORIZED = 11
    const val NETWORK_ERROR = 12
    const val NOT_FOUND = 13
    const val NO_TEAM = 14
}

internal object TeamMessages {
    const val TEAM_NOT_FOUND = "Team not found. The authenticated user may not belong to a team."
    const val NETWORK_FAILURE = "Team fetch failed: network is unreachable. Check your connection and retry."
    const val SERVER_FAILURE = "Team service is unavailable. Retry later or check server settings."
    const val UNKNOWN_FAILURE = "Team fetch failed unexpectedly. Retry and check your setup."
    const val NOT_A_TEAM_MEMBER = "You are not a member of a team. Team commands are only available for team accounts."
}

internal sealed interface TeamStepResult<out T> {
    data class Success<T>(val value: T) : TeamStepResult<T>

    data class Failure(val category: TeamFailureCategory) : TeamStepResult<Nothing>
}

internal enum class TeamFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    NOT_FOUND,
    NO_TEAM,
    UNKNOWN,
}

internal data class KaliumTeamSessionScope(
    val userId: String,
    val server: String?,
)

internal interface TeamRuntime {
    fun resolveSessionScope(session: AuthSession): TeamStepResult<KaliumTeamSessionScope>

    fun readTeam(sessionScope: KaliumTeamSessionScope): TeamStepResult<TeamView>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

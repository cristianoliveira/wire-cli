package wirecli.team

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class AuthGuardedTeamService(
    private val authSessionService: AuthSessionService,
    private val delegate: TeamService,
) : TeamService {
    override fun read(): TeamReadResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.read()
            is AuthResult.Failure ->
                TeamReadResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

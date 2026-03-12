package wirecli.presence

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class AuthGuardedPresenceService(
    private val authSessionService: AuthSessionService,
    private val delegate: PresenceService,
) : PresenceService {
    override fun getCurrentPresence(): PresenceResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getCurrentPresence()
            is AuthResult.Failure ->
                PresenceResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

package wirecli.presence

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.shared.PresenceError

class AuthGuardedPresenceService(
    private val authSessionService: AuthSessionService,
    private val delegate: PresenceService,
) : PresenceService {
    override fun getCurrentPresence(): PresenceResult<PresenceView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getCurrentPresence()
            is AuthResult.Failure ->
                PresenceResult.Failure(
                    error = PresenceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun setCurrentPresence(state: WritablePresenceState): PresenceResult<PresenceView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.setCurrentPresence(state)
            is AuthResult.Failure ->
                PresenceResult.Failure(
                    error = PresenceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }
}

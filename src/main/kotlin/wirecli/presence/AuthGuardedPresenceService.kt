package wirecli.presence

import wirecli.auth.AuthSessionService
import wirecli.shared.PresenceError
import wirecli.shared.Result

class AuthGuardedPresenceService(
    private val authSessionService: AuthSessionService,
    private val delegate: PresenceService,
) : PresenceService {
    override fun getCurrentPresence(): PresenceResult<PresenceView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> delegate.getCurrentPresence()
            is Result.Failure ->
                Result.Failure(
                    error = PresenceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun setCurrentPresence(state: WritablePresenceState): PresenceResult<PresenceView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> delegate.setCurrentPresence(state)
            is Result.Failure ->
                Result.Failure(
                    error = PresenceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }
}

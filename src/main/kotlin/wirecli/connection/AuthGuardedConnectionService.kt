package wirecli.connection

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class AuthGuardedConnectionService(
    private val authSessionService: AuthSessionService,
    private val delegate: ConnectionService,
) : ConnectionService {
    override fun sendRequest(userId: String): ConnectionActionResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.sendRequest(userId)
            is AuthResult.Failure ->
                ConnectionActionResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun blockUser(userId: String): ConnectionActionResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.blockUser(userId)
            is AuthResult.Failure ->
                ConnectionActionResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun unblockUser(userId: String): ConnectionActionResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.unblockUser(userId)
            is AuthResult.Failure ->
                ConnectionActionResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

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

    override fun acceptRequest(userId: String): ConnectionActionResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.acceptRequest(userId)
            is AuthResult.Failure ->
                ConnectionActionResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun ignoreRequest(userId: String): ConnectionActionResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.ignoreRequest(userId)
            is AuthResult.Failure ->
                ConnectionActionResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun cancelRequest(userId: String): ConnectionActionResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.cancelRequest(userId)
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

    override fun listConnections(): ConnectionListResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.listConnections()
            is AuthResult.Failure ->
                ConnectionListResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

package wirecli.user

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class AuthGuardedUserService(
    private val authSessionService: AuthSessionService,
    private val delegate: UserService,
) : UserService {
    override fun search(query: UserSearchQuery): UserSearchResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.search(query)
            is AuthResult.Failure ->
                UserSearchResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun get(userId: String): UserGetResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.get(userId)
            is AuthResult.Failure ->
                UserGetResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

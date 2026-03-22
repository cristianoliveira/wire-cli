package wirecli.profile

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.shared.ProfileError

class AuthGuardedProfileService(
    private val authSessionService: AuthSessionService,
    private val delegate: ProfileService,
) : ProfileService {
    override fun getCurrentProfile(): ProfileResult<ProfileView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getCurrentProfile()
            is AuthResult.Failure ->
                ProfileResult.Failure(
                    error = ProfileError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }
}

package wirecli.profile

import wirecli.auth.AuthSessionService
import wirecli.shared.ProfileError
import wirecli.shared.Result

class AuthGuardedProfileService(
    private val authSessionService: AuthSessionService,
    private val delegate: ProfileService,
) : ProfileService {
    override fun getCurrentProfile(): ProfileResult<ProfileView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> delegate.getCurrentProfile()
            is Result.Failure ->
                Result.Failure(
                    error = ProfileError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }
}

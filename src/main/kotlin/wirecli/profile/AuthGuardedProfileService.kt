package wirecli.profile

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class AuthGuardedProfileService(
    private val authSessionService: AuthSessionService,
    private val delegate: ProfileService,
) : ProfileService {
    override fun getCurrentProfile(): ProfileResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getCurrentProfile()
            is AuthResult.Failure ->
                ProfileResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun updateProfile(update: ProfileUpdate): ProfileUpdateResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.updateProfile(update)
            is AuthResult.Failure ->
                ProfileUpdateResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

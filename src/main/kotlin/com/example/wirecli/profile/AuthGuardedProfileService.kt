package com.example.wirecli.profile

import com.example.wirecli.auth.AuthResult
import com.example.wirecli.auth.AuthSessionService

class AuthGuardedProfileService(
    private val authSessionService: AuthSessionService,
    private val delegate: ProfileService
) : ProfileService {
    override fun getCurrentProfile(): ProfileResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getCurrentProfile()
            is AuthResult.Failure -> ProfileResult.Failure(
                message = authResult.message,
                exitCode = authResult.exitCode
            )
        }
    }
}

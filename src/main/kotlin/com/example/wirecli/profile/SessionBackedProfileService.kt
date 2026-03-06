package com.example.wirecli.profile

import com.example.wirecli.auth.AuthSessionStore
import com.example.wirecli.auth.AuthMessages
import com.example.wirecli.auth.ExitCodes

class SessionBackedProfileService(
    private val sessionStore: AuthSessionStore,
    private val apiClient: ProfileApiClient
) : ProfileService {
    // TODO: Consider using SessionInventory for more detailed error messages when not guarded by AuthGuardedProfileService.
    override fun getCurrentProfile(): ProfileResult {
        val session = sessionStore.readActiveSession()
            ?: return ProfileResult.Failure(
                message = AuthMessages.noActiveSession(),
                exitCode = ExitCodes.UNAUTHORIZED
            )

        return apiClient.fetchProfile(session)
    }
}

package wirecli.profile

import wirecli.auth.AuthSession
import wirecli.presence.PresenceState

data class ProfileView(
    val name: String?,
    val email: String?,
    val handle: String?,
    val presence: PresenceState = PresenceState.UNKNOWN,
)

data class ProfileUpdate(
    val name: String? = null,
    val handle: String? = null,
) {
    fun hasChanges(): Boolean = name != null || handle != null
}

sealed interface ProfileResult {
    data class Success(val profile: ProfileView) : ProfileResult

    data class Failure(val message: String, val exitCode: Int) : ProfileResult
}

sealed interface ProfileUpdateResult {
    data class Success(val profile: ProfileView) : ProfileUpdateResult

    data class Failure(val message: String, val exitCode: Int) : ProfileUpdateResult
}

interface ProfileApiClient {
    fun fetchProfile(session: AuthSession): ProfileResult

    fun updateProfile(
        session: AuthSession,
        update: ProfileUpdate,
    ): ProfileUpdateResult
}

interface ProfileService {
    fun getCurrentProfile(): ProfileResult

    fun updateProfile(update: ProfileUpdate): ProfileUpdateResult
}

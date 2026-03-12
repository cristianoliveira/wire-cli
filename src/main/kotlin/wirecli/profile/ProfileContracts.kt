package wirecli.profile

import wirecli.auth.AuthSession
import wirecli.presence.PresenceState

data class ProfileView(
    val name: String?,
    val email: String?,
    val handle: String?,
    val presence: PresenceState = PresenceState.UNKNOWN,
)

sealed interface ProfileResult {
    data class Success(val profile: ProfileView) : ProfileResult

    data class Failure(val message: String, val exitCode: Int) : ProfileResult
}

interface ProfileApiClient {
    fun fetchProfile(session: AuthSession): ProfileResult
}

interface ProfileService {
    fun getCurrentProfile(): ProfileResult
}

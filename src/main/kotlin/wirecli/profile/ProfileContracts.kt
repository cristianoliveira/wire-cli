package wirecli.profile

import wirecli.auth.AuthSession
import wirecli.presence.PresenceState
import wirecli.shared.ProfileResult

data class ProfileView(
    val name: String?,
    val email: String?,
    val handle: String?,
    val presence: PresenceState = PresenceState.UNKNOWN,
)

typealias ProfileResult = ProfileResult<ProfileView>

interface ProfileApiClient {
    fun fetchProfile(session: AuthSession): ProfileResult
}

interface ProfileService {
    fun getCurrentProfile(): ProfileResult
}

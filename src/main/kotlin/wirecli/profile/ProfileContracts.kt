package wirecli.profile

import wirecli.auth.AuthSession
import wirecli.presence.PresenceState
import wirecli.shared.Result
import wirecli.shared.ProfileError

// Type aliases for module-specific Result types
typealias ProfileResult<T> = Result<T, ProfileError>

data class ProfileView(
    val name: String?,
    val email: String?,
    val handle: String?,
    val presence: PresenceState = PresenceState.UNKNOWN,
)

interface ProfileApiClient {
    fun fetchProfile(session: AuthSession): ProfileResult<ProfileView>
}

interface ProfileService {
    fun getCurrentProfile(): ProfileResult<ProfileView>
}

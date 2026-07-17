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

// Step result for runtime-level operations (SDK adapter layer)
internal sealed interface ProfileStepResult<out T> {
    data class Success<T>(val value: T) : ProfileStepResult<T>

    data class Failure(val category: ProfileFailureCategory) : ProfileStepResult<Nothing>
}

// Failure categories for runtime-level profile operations
internal enum class ProfileFailureCategory {
    NETWORK,
    TIMEOUT,
    SERVER,
    UNAUTHORIZED,
    UNKNOWN,
}

// Scoped context for profile operations on a specific authenticated user
internal data class KaliumProfileSessionScope(
    val userId: String,
    val server: String?,
)

// Kalium self-user data transfer object
internal data class KaliumSelfUser(
    val name: String?,
    val email: String?,
    val handle: String?,
)

// Runtime-level interface for SDK adapters
internal interface ProfileRuntime {
    fun resolveSessionScope(session: AuthSession): ProfileStepResult<KaliumProfileSessionScope>

    fun getSelfUser(sessionScope: KaliumProfileSessionScope): ProfileStepResult<KaliumSelfUser>

    fun updateSelf(
        sessionScope: KaliumProfileSessionScope,
        name: String?,
        handle: String?,
    ): ProfileStepResult<Unit>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

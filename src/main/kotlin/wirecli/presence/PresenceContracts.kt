package wirecli.presence

import wirecli.auth.AuthSession

enum class PresenceState(val value: String) {
    ONLINE("online"),
    BUSY("busy"),
    AWAY("away"),
    OFFLINE("offline"),
    UNKNOWN("unknown"),
    ;

    override fun toString(): String = value
}

enum class WritablePresenceState(val value: String) {
    ONLINE("online"),
    BUSY("busy"),
    AWAY("away"),
    OFFLINE("offline"),
    ;

    override fun toString(): String = value
}

object PresenceStatusContract {
    val writableValues: Set<String> = WritablePresenceState.entries.map { it.value }.toSet()
    val normalizedValues: Set<String> = PresenceState.entries.map { it.value }.toSet()

    fun normalize(rawState: String?): PresenceState {
        val value = rawState?.trim()?.lowercase().takeUnless { it.isNullOrBlank() } ?: return PresenceState.UNKNOWN

        return when (value) {
            "online" -> PresenceState.ONLINE
            "busy" -> PresenceState.BUSY
            "away", "not_available" -> PresenceState.AWAY
            "offline" -> PresenceState.OFFLINE
            else -> PresenceState.UNKNOWN
        }
    }

    fun parseWritable(rawState: String?): WritablePresenceState? {
        val value = rawState?.trim()?.lowercase().takeUnless { it.isNullOrBlank() } ?: return null

        return when (value) {
            "online" -> WritablePresenceState.ONLINE
            "busy" -> WritablePresenceState.BUSY
            "away" -> WritablePresenceState.AWAY
            "offline" -> WritablePresenceState.OFFLINE
            else -> null
        }
    }
}

object PresenceNormalizer {
    fun normalize(rawState: String?): PresenceState {
        return PresenceStatusContract.normalize(rawState)
    }
}

data class PresenceView(val state: PresenceState)

sealed interface PresenceResult {
    data class Success(val presence: PresenceView) : PresenceResult

    data class Failure(val message: String, val exitCode: Int) : PresenceResult
}

interface PresenceApiClient {
    fun fetchPresence(session: AuthSession): PresenceResult

    fun updatePresence(
        session: AuthSession,
        state: WritablePresenceState,
    ): PresenceResult
}

interface PresenceService {
    fun getCurrentPresence(): PresenceResult

    fun setCurrentPresence(state: WritablePresenceState): PresenceResult
}

internal object PresenceMessages {
    const val NETWORK_FAILURE = "Presence fetch failed: network is unreachable. Check your connection and retry."
    const val SERVER_FAILURE = "Presence service is unavailable. Retry later or check server settings."
    const val UNKNOWN_FAILURE = "Presence fetch failed unexpectedly. Retry and check your setup."

    const val SET_NETWORK_FAILURE = "Presence update failed: network is unreachable. Check your connection and retry."
    const val SET_SERVER_FAILURE = "Presence update could not be completed. Retry later or check server settings."
    const val SET_UNKNOWN_FAILURE = "Presence update failed unexpectedly. Retry and check your setup."
}

// Step result for runtime-level operations (SDK adapter layer)
internal sealed interface PresenceStepResult<out T> {
    data class Success<T>(val value: T) : PresenceStepResult<T>

    data class Failure(val category: PresenceFailureCategory) : PresenceStepResult<Nothing>
}

// Failure categories for runtime-level presence operations
internal enum class PresenceFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    UNKNOWN,
}

// Scoped context for presence operations on a specific authenticated user
internal data class KaliumPresenceSessionScope(
    val userId: String,
    val server: String?,
)

// Runtime-level interface for SDK adapters
internal interface PresenceRuntime {
    fun resolveSessionScope(
        session: AuthSession,
        requireLiveSync: Boolean = false,
    ): PresenceStepResult<KaliumPresenceSessionScope>

    fun getSelfAvailabilityStatus(
        sessionScope: KaliumPresenceSessionScope,
    ): PresenceStepResult<com.wire.kalium.logic.data.user.UserAvailabilityStatus>

    fun setSelfAvailabilityStatus(
        sessionScope: KaliumPresenceSessionScope,
        status: com.wire.kalium.logic.data.user.UserAvailabilityStatus,
    ): PresenceStepResult<Unit>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

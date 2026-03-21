package wirecli.presence

import wirecli.auth.AuthSession
import wirecli.shared.PresenceResult

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
        val value = rawState?.trim()?.lowercase() ?: return PresenceState.UNKNOWN
        if (value.isBlank()) return PresenceState.UNKNOWN

        return when (value) {
            "online" -> PresenceState.ONLINE
            "busy" -> PresenceState.BUSY
            "away", "not_available" -> PresenceState.AWAY
            "offline" -> PresenceState.OFFLINE
            else -> PresenceState.UNKNOWN
        }
    }

    fun parseWritable(rawState: String?): WritablePresenceState? {
        val value = rawState?.trim()?.lowercase() ?: return null
        if (value.isBlank()) return null

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

typealias PresenceResult = PresenceResult<PresenceView>

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

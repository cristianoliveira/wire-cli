package wirecli.presence

import wirecli.auth.AuthSession

enum class PresenceState(val value: String) {
    ONLINE("online"),
    BUSY("busy"),
    AWAY("away"),
    OFFLINE("offline"),
    UNKNOWN("unknown");

    override fun toString(): String = value
}

object PresenceNormalizer {
    fun normalize(rawState: String?): PresenceState {
        val value = rawState?.trim()?.lowercase()
            ?: return PresenceState.UNKNOWN

        if (value.isBlank()) {
            return PresenceState.UNKNOWN
        }

        return when (value) {
            "online" -> PresenceState.ONLINE
            "busy" -> PresenceState.BUSY
            "away", "not_available" -> PresenceState.AWAY
            "offline" -> PresenceState.OFFLINE
            else -> PresenceState.UNKNOWN
        }
    }
}

data class PresenceView(val state: PresenceState)

sealed interface PresenceResult {
    data class Success(val presence: PresenceView) : PresenceResult
    data class Failure(val message: String, val exitCode: Int) : PresenceResult
}

interface PresenceApiClient {
    fun fetchPresence(session: AuthSession): PresenceResult
}

interface PresenceService {
    fun getCurrentPresence(): PresenceResult
}

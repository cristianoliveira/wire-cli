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

internal object PresenceMessages {
    const val NETWORK_FAILURE = "Presence fetch failed: network is unreachable. Check your connection and retry."
    const val SERVER_FAILURE = "Presence service is unavailable. Retry later or check server settings."
    const val UNKNOWN_FAILURE = "Presence fetch failed unexpectedly. Retry and check your setup."
}

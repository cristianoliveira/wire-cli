package wirecli.connection

import wirecli.auth.AuthSession

/**
 * Result of a connection lifecycle action (request, block, unblock).
 * Success carries a user-facing confirmation message; Failure carries a
 * stable message and exit code.
 */
sealed interface ConnectionActionResult {
    data class Success(val message: String) : ConnectionActionResult

    data class Failure(val message: String, val exitCode: Int) : ConnectionActionResult
}

interface ConnectionApiClient {
    fun sendRequest(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult

    fun blockUser(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult

    fun unblockUser(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult
}

interface ConnectionService {
    fun sendRequest(userId: String): ConnectionActionResult

    fun blockUser(userId: String): ConnectionActionResult

    fun unblockUser(userId: String): ConnectionActionResult
}

// Exit codes for connection operations following standard CLI conventions.
object ConnectionExitCodes {
    const val OK = 0
    const val UNAUTHORIZED = 11
    const val NETWORK_ERROR = 12
    const val SERVER_ERROR = 13
    const val NOT_FOUND = 13
    const val INVALID_INPUT = 14
    const val CONFLICT = 17
}

internal object ConnectionMessages {
    const val REQUEST_SUCCESS = "Connection request sent."
    const val BLOCK_SUCCESS = "User blocked."
    const val UNBLOCK_SUCCESS = "User unblocked."

    const val USER_NOT_FOUND = "User not found. Check the user ID and try again."
    const val REQUEST_NETWORK_FAILURE =
        "Connection request failed: network is unreachable. Check your connection and retry."
    const val REQUEST_SERVER_FAILURE =
        "Connection request could not be completed. Retry later or check server settings."
    const val REQUEST_UNKNOWN_FAILURE = "Connection request failed unexpectedly. Retry and check your setup."
    const val REQUEST_FEDERATION_DENIED = "Connection request denied: federation is not allowed with this user."
    const val REQUEST_LEGAL_HOLD =
        "Connection request blocked: missing legal hold consent. Contact your administrator."

    const val BLOCK_NETWORK_FAILURE = "Block failed: network is unreachable. Check your connection and retry."
    const val BLOCK_SERVER_FAILURE = "Block could not be completed. Retry later or check server settings."
    const val BLOCK_UNKNOWN_FAILURE = "Block failed unexpectedly. Retry and check your setup."

    const val UNBLOCK_NETWORK_FAILURE = "Unblock failed: network is unreachable. Check your connection and retry."
    const val UNBLOCK_SERVER_FAILURE = "Unblock could not be completed. Retry later or check server settings."
    const val UNBLOCK_UNKNOWN_FAILURE = "Unblock failed unexpectedly. Retry and check your setup."
}

// Step result for runtime-level operations (SDK adapter layer)
internal sealed interface ConnectionStepResult<out T> {
    data class Success<T>(val value: T) : ConnectionStepResult<T>

    data class Failure(val category: ConnectionFailureCategory) : ConnectionStepResult<Nothing>
}

// Failure categories for runtime-level connection operations
internal enum class ConnectionFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    USER_NOT_FOUND,
    FEDERATION_DENIED,
    LEGAL_HOLD,
    UNKNOWN,
}

/**
 * Stable outcome of a connection action at the runtime/SDK layer.
 * The real adapter translates Kalium result types into this enum so the
 * API client can map to user-facing messages without SDK coupling.
 */
internal enum class ConnectionOutcome {
    SUCCESS,
    FEDERATION_DENIED,
    LEGAL_HOLD,
    FAILURE,
}

// Scoped context for connection operations on a specific authenticated user
internal data class KaliumConnectionSessionScope(
    val userId: String,
    val server: String?,
)

// Runtime-level interface for SDK adapters
internal interface ConnectionRuntime {
    fun resolveSessionScope(session: AuthSession): ConnectionStepResult<KaliumConnectionSessionScope>

    fun sendConnectionRequest(
        sessionScope: KaliumConnectionSessionScope,
        userId: String,
    ): ConnectionStepResult<ConnectionOutcome>

    fun blockUser(
        sessionScope: KaliumConnectionSessionScope,
        userId: String,
    ): ConnectionStepResult<ConnectionOutcome>

    fun unblockUser(
        sessionScope: KaliumConnectionSessionScope,
        userId: String,
    ): ConnectionStepResult<ConnectionOutcome>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

package wirecli.connection

import wirecli.auth.AuthSession
import wirecli.user.UserConnectionState

/**
 * Result of a connection lifecycle action (request, block, unblock).
 * Success carries a user-facing confirmation message; Failure carries a
 * stable message and exit code.
 */
sealed interface ConnectionActionResult {
    data class Success(val message: String) : ConnectionActionResult

    data class Failure(val message: String, val exitCode: Int) : ConnectionActionResult
}

/**
 * User-facing view of a single connection.
 */
data class ConnectionView(
    val userId: String,
    val userName: String?,
    val handle: String?,
    val status: UserConnectionState,
    val lastUpdate: String?,
)

/**
 * Versioned list view for script-friendly JSON output.
 */
data class ConnectionListView(
    val connections: List<ConnectionView>,
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

sealed interface ConnectionListResult {
    data class Success(val view: ConnectionListView) : ConnectionListResult

    data class Failure(val message: String, val exitCode: Int) : ConnectionListResult
}

interface ConnectionApiClient {
    fun sendRequest(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult

    fun acceptRequest(
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

    fun listConnections(session: AuthSession): ConnectionListResult
}

interface ConnectionService {
    fun sendRequest(userId: String): ConnectionActionResult

    fun acceptRequest(userId: String): ConnectionActionResult

    fun blockUser(userId: String): ConnectionActionResult

    fun unblockUser(userId: String): ConnectionActionResult

    fun listConnections(): ConnectionListResult
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
    const val ACCEPT_SUCCESS = "Connection request accepted."
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

    const val ACCEPT_NETWORK_FAILURE = "Accept failed: network is unreachable. Check your connection and retry."
    const val ACCEPT_SERVER_FAILURE = "Accept could not be completed. Retry later or check server settings."
    const val ACCEPT_UNKNOWN_FAILURE = "Accept failed unexpectedly. Retry and check your setup."

    const val BLOCK_NETWORK_FAILURE = "Block failed: network is unreachable. Check your connection and retry."
    const val BLOCK_SERVER_FAILURE = "Block could not be completed. Retry later or check server settings."
    const val BLOCK_UNKNOWN_FAILURE = "Block failed unexpectedly. Retry and check your setup."

    const val UNBLOCK_NETWORK_FAILURE = "Unblock failed: network is unreachable. Check your connection and retry."
    const val UNBLOCK_SERVER_FAILURE = "Unblock could not be completed. Retry later or check server settings."
    const val UNBLOCK_UNKNOWN_FAILURE = "Unblock failed unexpectedly. Retry and check your setup."

    const val LIST_NETWORK_FAILURE =
        "Connection list failed: network is unreachable. Check your connection and retry."
    const val LIST_SERVER_FAILURE =
        "Connection list could not be retrieved. Retry later or check server settings."
    const val LIST_UNKNOWN_FAILURE = "Connection list failed unexpectedly. Retry and check your setup."
    const val NO_CONNECTIONS = "No connections found."
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
    LIST_NETWORK,
    LIST_SERVER,
    LIST_UNKNOWN,
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

// Runtime-level data for a single connection
internal data class KaliumConnectionEntry(
    val userId: String,
    val userName: String?,
    val handle: String?,
    val status: UserConnectionState,
    val lastUpdate: String?,
)

// Runtime-level interface for SDK adapters
internal interface ConnectionRuntime {
    fun resolveSessionScope(session: AuthSession): ConnectionStepResult<KaliumConnectionSessionScope>

    fun sendConnectionRequest(
        sessionScope: KaliumConnectionSessionScope,
        userId: String,
    ): ConnectionStepResult<ConnectionOutcome>

    fun acceptConnectionRequest(
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

    fun listConnections(sessionScope: KaliumConnectionSessionScope): ConnectionStepResult<List<KaliumConnectionEntry>>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

package wirecli.user

import wirecli.auth.AuthSession

/**
 * Connection state between the authenticated user and another user.
 * Maps Kalium's ConnectionState to a stable user-facing representation.
 */
enum class UserConnectionState(val value: String) {
    NOT_CONNECTED("not_connected"),
    PENDING("pending"),
    SENT("sent"),
    ACCEPTED("accepted"),
    BLOCKED("blocked"),
    IGNORED("ignored"),
    CANCELLED("cancelled"),
    UNKNOWN("unknown"),
    ;

    override fun toString(): String = value
}

/**
 * User-facing view of a Wire user.
 */
data class UserView(
    val id: String,
    val name: String?,
    val handle: String?,
    val email: String? = null,
    val team: String? = null,
    val connection: UserConnectionState = UserConnectionState.UNKNOWN,
)

/**
 * Versioned list view for script-friendly JSON output.
 */
data class UserListView(
    val users: List<UserView>,
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

/**
 * Validated search query with optional result limit.
 *
 * @throws IllegalArgumentException when query is blank or limit is out of range.
 */
data class UserSearchQuery(
    val query: String,
    val limit: Int = DEFAULT_LIMIT,
    val contactsOnly: Boolean = false,
) {
    init {
        require(query.isNotBlank()) { "Search query must not be blank." }
        require(limit in MIN_LIMIT..MAX_LIMIT) {
            "Search limit must be between $MIN_LIMIT and $MAX_LIMIT."
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 100
    }
}

sealed interface UserSearchResult {
    data class Success(val view: UserListView) : UserSearchResult

    data class Failure(val message: String, val exitCode: Int) : UserSearchResult
}

sealed interface UserGetResult {
    data class Success(val view: UserView) : UserGetResult

    data class Failure(val message: String, val exitCode: Int) : UserGetResult
}

interface UserApiClient {
    fun searchUsers(
        session: AuthSession,
        query: UserSearchQuery,
    ): UserSearchResult

    fun getUser(
        session: AuthSession,
        userId: String,
    ): UserGetResult
}

interface UserService {
    fun search(query: UserSearchQuery): UserSearchResult

    fun get(userId: String): UserGetResult
}

// Exit codes for user operations following standard CLI conventions.
object UserExitCodes {
    const val OK = 0
    const val UNAUTHORIZED = 11
    const val NETWORK_ERROR = 12
    const val SERVER_ERROR = 13
    const val NOT_FOUND = 13
    const val INVALID_INPUT = 14
}

internal object UserMessages {
    const val USER_NOT_FOUND = "User not found. Check the user ID and try again."
    const val NO_RESULTS = "No users found for the given query."
    const val SEARCH_NETWORK_FAILURE =
        "User search failed: network is unreachable. Check your connection and retry."
    const val SEARCH_SERVER_FAILURE = "User search service is unavailable. Retry later or check server settings."
    const val SEARCH_UNKNOWN_FAILURE = "User search failed unexpectedly. Retry and check your setup."
    const val GET_NETWORK_FAILURE = "User fetch failed: network is unreachable. Check your connection and retry."
    const val GET_SERVER_FAILURE = "User fetch service is unavailable. Retry later or check server settings."
    const val GET_UNKNOWN_FAILURE = "User fetch failed unexpectedly. Retry and check your setup."
}

// Step result for runtime-level operations (SDK adapter layer)
internal sealed interface UserStepResult<out T> {
    data class Success<T>(val value: T) : UserStepResult<T>

    data class Failure(val category: UserFailureCategory) : UserStepResult<Nothing>
}

// Failure categories for runtime-level user operations
internal enum class UserFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    USER_NOT_FOUND,
    UNKNOWN,
}

// Scoped context for user operations on a specific authenticated user
internal data class KaliumUserSessionScope(
    val userId: String,
    val server: String?,
)

// Kalium search result transfer object (connected + not connected buckets merged)
internal data class KaliumUserSearchResult(
    val users: List<KaliumUser>,
)

internal data class KaliumUser(
    val id: String,
    val name: String?,
    val handle: String?,
    val email: String?,
    val team: String?,
    val connection: UserConnectionState,
)

// Runtime-level interface for SDK adapters
internal interface UserRuntime {
    fun resolveSessionScope(session: AuthSession): UserStepResult<KaliumUserSessionScope>

    fun searchUsers(
        sessionScope: KaliumUserSessionScope,
        query: String,
        limit: Int,
        contactsOnly: Boolean,
    ): UserStepResult<KaliumUserSearchResult>

    fun getUser(
        sessionScope: KaliumUserSessionScope,
        userId: String,
    ): UserStepResult<KaliumUser>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

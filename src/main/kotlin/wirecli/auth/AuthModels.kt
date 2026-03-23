package wirecli.auth

import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.SsoManagedBy

/**
 * Contract for Kalium SDK authentication runtime operations.
 *
 * This interface abstracts Kalium SDK interactions for authentication flows,
 * enabling testability and separation between CLI and SDK concerns.
 *
 * @invariant All methods return non-null AuthStepResult
 * @invariant Shutdown must be called to cleanup resources
 */
internal interface RealKaliumAuthRuntime {
    /**
     * Resolves the authentication scope for the given server.
     *
     * @param server Optional server URL/domain (null uses default server)
     * @return AuthStepResult with KaliumAuthScope for login operations or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre server must be null or a valid URL/domain string
     * @post Result is either Success with functional KaliumAuthScope or Failure
     */
    fun resolveAuthScope(server: String?): AuthStepResult<KaliumAuthScope>

    /**
     * Persists an authenticated account to local Kalium storage.
     *
     * @param account The account details with tokens and configuration to persist
     * @return AuthStepResult with Unit on success or Failure with error details
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre account must have valid userId, tokens, and serverConfigId
     * @post If successful, account can be recovered by subsequent CLI invocations
     */
    fun addAuthenticatedAccount(account: PersistedAccount): AuthStepResult<Unit>

    /**
     * Resolves the session scope for a specific user.
     *
     * @param userId The qualified user ID (format: value@domain)
     * @return AuthStepResult with KaliumSessionScope or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre userId must be non-null and in qualified format (value@domain)
     * @post Result is either Success with functional session scope or Failure
     */
    fun resolveSessionScope(userId: String): AuthStepResult<KaliumSessionScope>

    /**
     * Registers or retrieves a client device for the authenticated session.
     *
     * @param sessionScope The session context for the authenticated user
     * @param password The user's password for client registration
     * @return AuthStepResult with Unit on success or Failure with error details
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre sessionScope must represent a valid authenticated user
     * @pre password must match the user's credentials
     * @post If successful, a device is registered and ready for use
     */
    fun ensureClient(
        sessionScope: KaliumSessionScope,
        password: String,
    ): AuthStepResult<Unit>

    /**
     * Logs out the authenticated user and invalidates session state.
     *
     * @param session The session to logout
     * @return AuthStepResult with Unit on success or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre session must represent a valid authenticated user
     * @post If successful, session tokens are invalidated server-side
     */
    fun logout(session: AuthSession): AuthStepResult<Unit>

    /**
     * Closes the runtime and releases all resources.
     * Delegates to shutdown() for implementation.
     */
    fun close() {
        shutdown()
    }

    /**
     * Shuts down the Kalium runtime, cleaning up resources and active sessions.
     */
    fun shutdown()
}

/**
 * Contract for Kalium authentication scope operations.
 *
 * This interface represents an established authentication context with the Kalium SDK,
 * allowing login operations without re-establishing connection to the server.
 *
 * @invariant Represents a single authentication attempt/scope
 */
internal interface KaliumAuthScope {
    /**
     * Performs email/password login within this authentication scope.
     *
     * @param email User email address (non-null, valid format)
     * @param password User password (non-null, ≥8 chars recommended)
     * @return AuthStepResult with AuthenticatedPrincipal on success or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre email must be non-null and non-empty
     * @pre password must be non-null and non-empty
     * @post Result is either Success with valid tokens or Failure with error details
     */
    fun login(
        email: String,
        password: String,
    ): AuthStepResult<AuthenticatedPrincipal>
}

/**
 * Principal returned after successful authentication against the Wire server.
 *
 * Contains all tokens and metadata required to establish a user session and
 * register a client device.
 *
 * @invariant userId is in qualified format (value@domain)
 * @invariant accessToken and refreshToken are non-empty and valid JWT
 * @invariant serverConfigId matches a valid Kalium server configuration
 */
internal data class AuthenticatedPrincipal(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val cookieLabel: String?,
    val serverConfigId: String,
    val ssoId: SsoId?,
    val managedBy: SsoManagedBy?,
    val proxyCredentials: ProxyCredentials?,
)

/**
 * Account information persisted to local storage for session resumption.
 *
 * All fields from the authenticated principal plus server configuration,
 * allowing the CLI to resume sessions without re-authenticating.
 *
 * @invariant userId is in qualified format (value@domain)
 * @invariant All token fields are non-empty strings
 */
internal data class PersistedAccount(
    val userId: String,
    val server: String?,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val cookieLabel: String?,
    val serverConfigId: String,
    val ssoId: SsoId?,
    val managedBy: SsoManagedBy?,
    val proxyCredentials: ProxyCredentials?,
)

/**
 * Scoped context for session operations on a specific authenticated user.
 *
 * @invariant userId is in qualified format (value@domain)
 */
internal data class KaliumSessionScope(
    val userId: String,
)

/**
 * Result type for authentication step operations (sealed interface).
 *
 * Represents either a successful step result with typed value or a failure
 * with category and optional diagnostic message.
 *
 * @invariant Each operation returns exactly one sealed subtype
 */
internal sealed interface AuthStepResult<out T> {
    /**
     * Successful step result with associated value.
     *
     * @param value The result value from the successful step
     */
    data class Success<T>(val value: T) : AuthStepResult<T>

    /**
     * Failed step result with error details.
     *
     * @param category The category of failure (determines error message and exit code)
     * @param message Optional diagnostic message providing additional context
     */
    data class Failure(
        val category: AuthFailureCategory,
        val message: String? = null,
    ) : AuthStepResult<Nothing>
}

/**
 * Enumeration of possible authentication failure categories.
 *
 * Each category maps to specific user-facing messages and exit codes.
 * INVALID_CREDENTIALS: Email/password mismatch
 * PASSWORD_REQUIRED: Device registration requires password re-entry
 * NETWORK: Network connectivity issues
 * SERVER: Server errors (5xx, unavailable)
 * UNAUTHORIZED: Account suspended, pending activation, or other auth failures
 * UNKNOWN: Unexpected/unclassifiable errors
 */
internal enum class AuthFailureCategory {
    INVALID_CREDENTIALS,
    PASSWORD_REQUIRED,
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    NOMAD_SINGLE_USER_VIOLATION,
    UNKNOWN,
}

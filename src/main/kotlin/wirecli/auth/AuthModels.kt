package wirecli.auth

import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.SsoManagedBy
import com.wire.kalium.logic.data.user.UserId

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

/**
 * Parses a qualified user ID string into a UserId or returns null if invalid.
 *
 * Expected format: "value@domain" where both value and domain are non-blank.
 *
 * @receiver The string to parse
 * @return UserId if valid qualified format, null otherwise
 *
 * @pre receiver must be non-null
 * @post If successful, result.value and result.domain are both non-blank
 */
internal fun String.toQualifiedIdOrNull(): UserId? {
    val trimmed = trim()
    val atIndex = trimmed.lastIndexOf('@')
    val isValidFormat = atIndex > 0 && atIndex < trimmed.lastIndex

    return if (isValidFormat) {
        val value = trimmed.substring(0, atIndex)
        val domain = trimmed.substring(atIndex + 1)
        if (value.isNotBlank() && domain.isNotBlank()) UserId(value = value, domain = domain) else null
    } else {
        null
    }
}

/**
 * Serializes a UserId into qualified string format.
 *
 * @receiver The UserId to serialize
 * @return Qualified string in format "value@domain"
 *
 * @post Returns non-null non-empty string in "value@domain" format
 */
internal fun UserId.serialize(): String = "$value@$domain"

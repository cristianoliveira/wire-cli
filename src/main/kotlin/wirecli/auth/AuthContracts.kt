package wirecli.auth
// Follow-up: consider unifying AuthResult and AuthApiResult into a generic Result<T> to reduce duplication.

data class LoginInput(
    val email: String,
    val password: String,
    val server: String?,
)

data class AuthSession(
    val userId: String,
    val accessToken: String,
    val server: String?,
)

/**
 * Full multi-account view of local credential storage.
 *
 * - [accounts] holds every persisted account, keyed by [AuthSession.userId].
 * - [activeUserId] is the explicitly selected account (like kubectl `current-context`).
 *   It may point at no account (null) even when accounts exist.
 * - [invalidAccounts] / [diagnosticMessage] carry parse/migration diagnostics.
 *
 * @invariant [activeUserId] is null or matches one entry in [accounts] in a healthy file.
 */
data class AccountInventory(
    val accounts: List<AuthSession>,
    val activeUserId: String?,
    val invalidAccounts: Int = 0,
    val diagnosticMessage: String? = null,
) {
    val activeAccount: AuthSession?
        get() = accounts.firstOrNull { it.userId == activeUserId }
}

sealed interface AuthResult {
    data class Success(val message: String) : AuthResult

    data class Failure(val message: String, val exitCode: Int) : AuthResult
}

interface AuthApiClient {
    fun login(input: LoginInput): AuthApiResult

    fun logout(session: AuthSession): AuthApiResult
}

sealed interface AuthApiResult {
    data class Success(val session: AuthSession) : AuthApiResult

    data class Failure(val message: String, val exitCode: Int) : AuthApiResult
}

interface SessionProvider {
    fun readActiveSession(): AuthSession?
}

/**
 * Multi-account credential store. Read path returns the active account via
 * [SessionProvider]; write paths operate on individual accounts so multiple
 * accounts can coexist and the active one can be switched without re-auth.
 *
 * Switching ([setActiveAccount]) and removal ([removeAccount]) are local-only
 * operations: they never contact Wire and return the affected account or null
 * when the requested account is absent.
 */
interface AuthSessionStore : SessionProvider {
    fun readAccounts(): AccountInventory

    fun addAccount(
        account: AuthSession,
        makeActive: Boolean = true,
    )

    fun setActiveAccount(userId: String): AuthSession?

    fun removeAccount(userId: String): AuthSession?
}

interface AuthSessionService {
    fun login(input: LoginInput): AuthResult

    fun logout(): AuthResult

    fun requireActiveSession(): AuthResult
}

/**
 * Local account management: list, inspect the active account, and switch or
 * remove accounts without touching the network. Backed by [AuthSessionStore].
 */
interface AccountsService {
    fun listAccounts(): AccountsListing

    fun currentAccount(): AuthSession?

    /** Activates the account for [userId]; returns it, or null when absent. */
    fun useAccount(userId: String): AuthSession?

    /** Removes the account for [userId]; returns the removed account, or null when absent. */
    fun removeAccount(userId: String): AuthSession?
}

data class AccountsListing(
    val accounts: List<AuthSession>,
    val activeUserId: String?,
)

// Follow-up: consider converting ExitCodes to enum class for better type safety.
object ExitCodes {
    const val OK = 0
    const val AUTH_FAILED = 10
    const val UNAUTHORIZED = 11
    const val NETWORK_ERROR = 12
    const val SERVER_ERROR = 13
    const val VALIDATION_ERROR = 2
    const val PASSWORD_REQUIRED = 15
    const val NOMAD_SINGLE_USER_VIOLATION = 16
    const val UNKNOWN_ERROR = 1
}

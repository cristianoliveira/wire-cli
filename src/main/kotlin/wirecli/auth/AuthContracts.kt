package wirecli.auth
// FIXME: Consider unifying AuthResult and AuthApiResult into a generic Result<T> to reduce duplication.

data class LoginInput(
    val email: String,
    val password: String,
    val server: String?
)

data class AuthSession(
    val userId: String,
    val accessToken: String,
    val server: String?
)

data class SessionInventory(
    val activeSession: AuthSession?,
    val validSessions: Int,
    val invalidSessions: Int
)

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

interface AuthSessionStore {
    fun readActiveSession(): AuthSession?
    fun readSessionInventory(): SessionInventory
    fun writeActiveSession(session: AuthSession)
    fun clearActiveSession()
}

interface AuthSessionService {
    fun login(input: LoginInput): AuthResult
    fun logout(): AuthResult
    fun requireActiveSession(): AuthResult
}

// TODO: Consider converting ExitCodes to enum class for better type safety.
object ExitCodes {
    const val OK = 0
    const val AUTH_FAILED = 10
    const val UNAUTHORIZED = 11
    const val NETWORK_ERROR = 12
    const val SERVER_ERROR = 13
    const val VALIDATION_ERROR = 14
    const val UNKNOWN_ERROR = 1
}

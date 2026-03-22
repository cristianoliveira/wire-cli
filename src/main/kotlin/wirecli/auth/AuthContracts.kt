package wirecli.auth

import wirecli.shared.Result
import wirecli.shared.AuthError

// Type aliases for module-specific Result types
typealias AuthResult<T> = Result<T, AuthError>
typealias AuthApiResult<T> = Result<T, AuthError>

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

data class SessionInventory(
    val activeSession: AuthSession?,
    val validSessions: Int,
    val invalidSessions: Int,
    val diagnosticMessage: String? = null,
)

interface AuthApiClient {
    fun login(input: LoginInput): AuthApiResult<AuthSession>

    fun logout(session: AuthSession): AuthApiResult<String>
}

interface SessionProvider {
    fun readActiveSession(): AuthSession?
}

interface AuthSessionStore : SessionProvider {
    fun readSessionInventory(): SessionInventory

    fun writeActiveSession(session: AuthSession)

    fun clearActiveSession()
}

interface AuthSessionService {
    fun login(input: LoginInput): AuthResult<String>

    fun logout(): AuthResult<String>

    fun requireActiveSession(): AuthResult<String>
}

// TODO: Consider converting ExitCodes to enum class for better type safety.
object ExitCodes {
    const val OK = 0
    const val AUTH_FAILED = 10
    const val UNAUTHORIZED = 11
    const val NETWORK_ERROR = 12
    const val SERVER_ERROR = 13
    const val VALIDATION_ERROR = 14
    const val PASSWORD_REQUIRED = 15
    const val NOMAD_SINGLE_USER_VIOLATION = 16
    const val UNKNOWN_ERROR = 1
}

package wirecli.auth

import wirecli.shared.Result

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

typealias AuthResult = Result<String>
typealias AuthApiResult = Result<AuthSession>

interface AuthApiClient {
    fun login(input: LoginInput): AuthApiResult

    fun logout(session: AuthSession): AuthApiResult
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
    const val PASSWORD_REQUIRED = 15
    const val NOMAD_SINGLE_USER_VIOLATION = 16
    const val UNKNOWN_ERROR = 1
}

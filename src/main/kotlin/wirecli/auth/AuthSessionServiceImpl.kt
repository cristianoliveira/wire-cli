package wirecli.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.shared.AuthError

private val logger = KotlinLogging.logger {}

class AuthSessionServiceImpl(
    private val apiClient: AuthApiClient,
    private val sessionStore: AuthSessionStore,
) : AuthSessionService {
    override fun login(input: LoginInput): AuthResult {
        logger.debug { "Login process starting for email: ${input.email}" }

        return when (val loginResult = apiClient.login(input)) {
            is AuthApiResult.Success -> {
                logger.debug { "Authentication API call succeeded - attempting to persist session" }
                try {
                    logger.debug { "Writing active session to store for userId: ${loginResult.value.userId}" }
                    sessionStore.writeActiveSession(loginResult.value)
                    logger.info { "Session persisted successfully for email: ${input.email}" }
                    AuthResult.Success("Login successful.")
                } catch (e: RuntimeException) {
                    logger.error(e) { "Session write failed during login - session data not persisted" }
                    AuthResult.Failure(
                        error = AuthError(
                            message = "Login succeeded, but local session could not be saved. Try again.",
                            exitCode = ExitCodes.SERVER_ERROR,
                        ),
                    )
                }
            }

            is AuthApiResult.Failure -> {
                logger.warn { "Authentication API failed: ${AuthRedactor.redact(loginResult.error.message)}" }
                AuthResult.Failure(loginResult.error)
            }
        }
    }

    override fun logout(): AuthResult {
        logger.debug { "Logout process starting" }

        val inventory = sessionStore.readSessionInventory()
        logger.debug {
            "Session inventory read: active=${inventory.activeSession != null}, valid=${inventory.validSessions}, invalid=${inventory.invalidSessions}"
        }

        val session =
            inventory.activeSession
                ?: run {
                    logger.warn { "Logout failed - no active session found" }
                    return AuthResult.Failure(
                        message = missingSessionMessage(inventory),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    )
                }

        logger.debug { "Found active session for userId: ${session.userId} - calling API logout" }
        return when (val logoutResult = apiClient.logout(session)) {
            is AuthApiResult.Success -> {
                logger.debug { "Logout API call succeeded - clearing local session" }
                try {
                    sessionStore.clearActiveSession()
                    logger.info { "Local session cleared successfully" }
                    AuthResult.Success("Logged out.")
                } catch (e: RuntimeException) {
                    logger.error(e) { "Session cleanup failed during logout - remote session cleared but local session remains" }
                    AuthResult.Failure(
                        error = AuthError(
                            message = "Logout completed remotely, but local session cleanup failed.",
                            exitCode = ExitCodes.SERVER_ERROR,
                        ),
                    )
                }
            }

            is AuthApiResult.Failure -> {
                logger.warn { "Logout API failed: ${AuthRedactor.redact(logoutResult.error.message)}" }
                AuthResult.Failure(logoutResult.error)
            }
        }
    }

    override fun requireActiveSession(): AuthResult {
        logger.debug { "Checking for active session requirement" }

        val inventory = sessionStore.readSessionInventory()
        logger.debug {
            "Session inventory: active=${inventory.activeSession != null}, valid=${inventory.validSessions}, invalid=${inventory.invalidSessions}"
        }

        return if (inventory.activeSession == null) {
            logger.warn { "Active session requirement failed - no valid session found" }
            AuthResult.Failure(
                error = AuthError(
                    message = missingSessionMessage(inventory),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ),
            )
        } else {
            logger.debug { "Active session available - requirement satisfied" }
            AuthResult.Success("Active session available.")
        }
    }

    private fun missingSessionMessage(inventory: SessionInventory): String {
        inventory.diagnosticMessage?.let { return it }
        return if (inventory.invalidSessions > 0) {
            AuthMessages.noValidSession(inventory.invalidSessions)
        } else {
            AuthMessages.noActiveSession()
        }
    }
}

class StubAuthApiClient(
    private val environment: Map<String, String>,
) : AuthApiClient {
    override fun login(input: LoginInput): AuthApiResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "login_ok" ->
                AuthApiResult.Success(
                    value =
                        AuthSession(
                            userId = "user-jane",
                            accessToken = "stub-token",
                            server = input.server,
                        ),
                )

            "login_invalid" ->
                AuthApiResult.Failure(
                    error = AuthError(
                        message = AuthMessages.invalidCredentials(),
                        exitCode = ExitCodes.AUTH_FAILED,
                    ),
                )

            "login_network_error" ->
                AuthApiResult.Failure(
                    error = AuthError(
                        message = AuthMessages.networkFailure("Authentication"),
                        exitCode = ExitCodes.NETWORK_ERROR,
                    ),
                )

            "login_secret_failure" ->
                AuthApiResult.Failure(
                    error = AuthError(
                        message = "Authentication failed: token=abc123 password=super-secret",
                        exitCode = ExitCodes.AUTH_FAILED,
                    ),
                )

            else ->
                AuthApiResult.Failure(
                    error = AuthError(
                        message = AuthMessages.authServiceUnavailable(),
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )
        }
    }

    override fun logout(session: AuthSession): AuthApiResult<String> {
        return AuthApiResult.Success(value = session.userId)
    }
}

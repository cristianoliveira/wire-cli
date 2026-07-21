package wirecli.auth

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class AuthSessionServiceImpl(
    private val apiClient: AuthApiClient,
    private val sessionStore: AuthSessionStore,
) : AuthSessionService {
    override fun login(input: LoginInput): AuthResult {
        logger.debug { "Login process starting for email: ${input.email}" }

        return when (val loginResult = apiClient.login(input)) {
            is AuthApiResult.Success -> {
                logger.debug { "Authentication API call succeeded - attempting to persist account" }
                try {
                    logger.debug { "Adding account to store for userId: ${loginResult.session.userId}" }
                    sessionStore.addAccount(loginResult.session, makeActive = true)
                    logger.info { "Account persisted successfully for email: ${input.email}" }
                    AuthResult.Success("Login successful.")
                } catch (e: IllegalStateException) {
                    logger.error(e) { "Account write failed during login - account data not persisted" }
                    AuthResult.Failure(
                        message = "Login succeeded, but local account could not be saved. Try again.",
                        exitCode = ExitCodes.SERVER_ERROR,
                    )
                } catch (e: IllegalArgumentException) {
                    logger.error(e) { "Account validation failed during write - account data not persisted" }
                    AuthResult.Failure(
                        message = "Login succeeded, but local account could not be saved. Try again.",
                        exitCode = ExitCodes.SERVER_ERROR,
                    )
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    logger.error(e) { "Unexpected error during account write" }
                    AuthResult.Failure(
                        message = "Login succeeded, but local account could not be saved. Try again.",
                        exitCode = ExitCodes.SERVER_ERROR,
                    )
                }
            }

            is AuthApiResult.Failure -> {
                logger.warn { "Authentication API failed: ${AuthRedactor.redact(loginResult.message)}" }
                AuthResult.Failure(loginResult.message, loginResult.exitCode)
            }
        }
    }

    override fun logout(): AuthResult {
        logger.debug { "Logout process starting" }

        val inventory = sessionStore.readAccounts()
        logger.debug {
            "Account inventory read: active=${inventory.activeUserId != null}, " +
                "accounts=${inventory.accounts.size}"
        }

        val session = inventory.activeAccount
        if (session == null) {
            logger.warn { "Logout failed - no active session found" }
            return AuthResult.Failure(
                message = missingSessionMessage(inventory),
                exitCode = ExitCodes.UNAUTHORIZED,
            )
        }

        logger.debug { "Found active session for userId: ${session.userId} - calling API logout" }
        return when (val logoutResult = apiClient.logout(session)) {
            is AuthApiResult.Success -> {
                logger.debug { "Logout API call succeeded - removing local account" }
                try {
                    sessionStore.removeAccount(session.userId)
                    logger.info { "Local account removed successfully" }
                    AuthResult.Success("Logged out.")
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    logger.error(
                        e,
                    ) { "Account cleanup failed during logout - remote session cleared but local account remains" }
                    AuthResult.Failure(
                        message = "Logout completed remotely, but local account cleanup failed.",
                        exitCode = ExitCodes.SERVER_ERROR,
                    )
                }
            }

            is AuthApiResult.Failure -> {
                logger.warn { "Logout API failed: ${AuthRedactor.redact(logoutResult.message)}" }
                AuthResult.Failure(logoutResult.message, logoutResult.exitCode)
            }
        }
    }

    override fun requireActiveSession(): AuthResult {
        logger.debug { "Checking for active session requirement" }

        val inventory = sessionStore.readAccounts()
        logger.debug {
            "Account inventory: active=${inventory.activeUserId != null}, " +
                "accounts=${inventory.accounts.size}"
        }

        return if (inventory.activeAccount == null) {
            logger.warn { "Active session requirement failed - no valid session found" }
            AuthResult.Failure(
                message = missingSessionMessage(inventory),
                exitCode = ExitCodes.UNAUTHORIZED,
            )
        } else {
            logger.debug { "Active session available - requirement satisfied" }
            AuthResult.Success("Active session available.")
        }
    }

    private fun missingSessionMessage(inventory: AccountInventory): String {
        inventory.diagnosticMessage?.let { return it }
        return if (inventory.invalidAccounts > 0) {
            AuthMessages.noValidSession(inventory.invalidAccounts)
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
                    session =
                        AuthSession(
                            userId = "user-jane",
                            accessToken = "stub-token",
                            server = input.server,
                        ),
                )

            "login_invalid" ->
                AuthApiResult.Failure(
                    message = AuthMessages.INVALID_CREDENTIALS,
                    exitCode = ExitCodes.AUTH_FAILED,
                )

            "login_network_error" ->
                AuthApiResult.Failure(
                    message = AuthMessages.networkFailure("Authentication"),
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "login_secret_failure" ->
                AuthApiResult.Failure(
                    message = "Authentication failed: token=abc123 password=super-secret",
                    exitCode = ExitCodes.AUTH_FAILED,
                )

            else ->
                AuthApiResult.Failure(
                    message = AuthMessages.AUTH_SERVICE_UNAVAILABLE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )
        }
    }

    override fun logout(session: AuthSession): AuthApiResult {
        return AuthApiResult.Success(session)
    }
}

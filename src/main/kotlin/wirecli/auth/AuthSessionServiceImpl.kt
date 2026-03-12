package wirecli.auth

class AuthSessionServiceImpl(
    private val apiClient: AuthApiClient,
    private val sessionStore: AuthSessionStore,
) : AuthSessionService {
    override fun login(input: LoginInput): AuthResult {
        return when (val loginResult = apiClient.login(input)) {
            is AuthApiResult.Success -> {
                try {
                    sessionStore.writeActiveSession(loginResult.session)
                    AuthResult.Success("Login successful.")
                } catch (_: RuntimeException) {
                    AuthResult.Failure(
                        message = "Login succeeded, but local session could not be saved. Try again.",
                        exitCode = ExitCodes.SERVER_ERROR,
                    )
                }
            }

            is AuthApiResult.Failure -> AuthResult.Failure(loginResult.message, loginResult.exitCode)
        }
    }

    override fun logout(): AuthResult {
        val inventory = sessionStore.readSessionInventory()
        val session =
            inventory.activeSession
                ?: return AuthResult.Failure(
                    message = missingSessionMessage(inventory),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return when (val logoutResult = apiClient.logout(session)) {
            is AuthApiResult.Success -> {
                try {
                    sessionStore.clearActiveSession()
                    AuthResult.Success("Logged out.")
                } catch (_: RuntimeException) {
                    AuthResult.Failure(
                        message = "Logout completed remotely, but local session cleanup failed.",
                        exitCode = ExitCodes.SERVER_ERROR,
                    )
                }
            }

            is AuthApiResult.Failure -> AuthResult.Failure(logoutResult.message, logoutResult.exitCode)
        }
    }

    override fun requireActiveSession(): AuthResult {
        val inventory = sessionStore.readSessionInventory()

        return if (inventory.activeSession == null) {
            AuthResult.Failure(
                message = missingSessionMessage(inventory),
                exitCode = ExitCodes.UNAUTHORIZED,
            )
        } else {
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
                    session =
                        AuthSession(
                            userId = "user-jane",
                            accessToken = "stub-token",
                            server = input.server,
                        ),
                )

            "login_invalid" ->
                AuthApiResult.Failure(
                    message = AuthMessages.invalidCredentials(),
                    exitCode = ExitCodes.AUTH_FAILED,
                )

            "login_network_error" ->
                AuthApiResult.Failure(
                    message = AuthMessages.networkFailure("Authentication"),
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            else ->
                AuthApiResult.Failure(
                    message = AuthMessages.authServiceUnavailable(),
                    exitCode = ExitCodes.SERVER_ERROR,
                )
        }
    }

    override fun logout(session: AuthSession): AuthApiResult {
        return AuthApiResult.Success(session)
    }
}

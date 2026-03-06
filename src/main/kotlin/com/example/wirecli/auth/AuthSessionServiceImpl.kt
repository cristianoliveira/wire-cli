package com.example.wirecli.auth

class AuthSessionServiceImpl(
    private val apiClient: AuthApiClient,
    private val sessionStore: AuthSessionStore
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
                        exitCode = ExitCodes.SERVER_ERROR
                    )
                }
            }

            is AuthApiResult.Failure -> AuthResult.Failure(loginResult.message, loginResult.exitCode)
        }
    }

    override fun logout(): AuthResult {
        val session = sessionStore.readActiveSession()
            ?: return AuthResult.Failure(
                message = "No active session. Run wire login.",
                exitCode = ExitCodes.UNAUTHORIZED
            )

        return when (val logoutResult = apiClient.logout(session)) {
            is AuthApiResult.Success -> {
                sessionStore.clearActiveSession()
                AuthResult.Success("Logged out.")
            }

            is AuthApiResult.Failure -> AuthResult.Failure(logoutResult.message, logoutResult.exitCode)
        }
    }

    override fun requireActiveSession(): AuthResult {
        return if (sessionStore.readActiveSession() == null) {
            AuthResult.Failure(
                message = "No active session. Run wire login.",
                exitCode = ExitCodes.UNAUTHORIZED
            )
        } else {
            AuthResult.Success("Active session available.")
        }
    }
}

class StubAuthApiClient(
    private val environment: Map<String, String>
) : AuthApiClient {
    override fun login(input: LoginInput): AuthApiResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "login_ok" -> AuthApiResult.Success(
                session = AuthSession(
                    userId = "user-jane",
                    accessToken = "stub-token",
                    server = input.server
                )
            )

            "login_invalid" -> AuthApiResult.Failure(
                message = "Invalid email or password. Verify credentials and try again.",
                exitCode = ExitCodes.AUTH_FAILED
            )

            "login_network_error" -> AuthApiResult.Failure(
                message = "Authentication failed: network is unreachable. Check your connection and retry.",
                exitCode = ExitCodes.NETWORK_ERROR
            )

            else -> AuthApiResult.Failure(
                message = "Authentication service is unavailable. Retry later or check server settings.",
                exitCode = ExitCodes.SERVER_ERROR
            )
        }
    }

    override fun logout(session: AuthSession): AuthApiResult {
        return AuthApiResult.Success(session)
    }
}

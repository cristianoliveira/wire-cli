package wirecli.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RealKaliumAuthClientTest {
    private val loginInput = LoginInput(email = "jane@example.com", password = "correct-horse", server = null)

    @Test
    fun `maps invalid credentials login branch to auth failed exit code`() {
        val client =
            RealKaliumAuthClient(
                FakeOrchestrator(
                    loginResult =
                        AuthApiResult.Failure(
                            exitCode = ExitCodes.AUTH_FAILED,
                            message = AuthMessages.invalidCredentials(),
                        ),
                ),
            )

        val result = client.login(loginInput)

        val failure = assertIs<AuthApiResult.Failure>(result)
        assertEquals(AuthMessages.invalidCredentials(), failure.message)
        assertEquals(ExitCodes.AUTH_FAILED, failure.exitCode)
    }

    @Test
    fun `maps network login branch to retry guidance`() {
        val client =
            RealKaliumAuthClient(
                FakeOrchestrator(
                    loginResult =
                        AuthApiResult.Failure(
                            exitCode = ExitCodes.NETWORK_ERROR,
                            message = AuthMessages.networkFailure("Authentication"),
                        ),
                ),
            )

        val result = client.login(loginInput)

        val failure = assertIs<AuthApiResult.Failure>(result)
        assertEquals(AuthMessages.networkFailure("Authentication"), failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `maps unauthorized client registration to unauthorized exit code`() {
        val client =
            RealKaliumAuthClient(
                FakeOrchestrator(
                    loginResult =
                        AuthApiResult.Failure(
                            exitCode = ExitCodes.UNAUTHORIZED,
                            message = AuthMessages.clientRegistrationFailed(),
                        ),
                ),
            )

        val result = client.login(loginInput)

        val failure = assertIs<AuthApiResult.Failure>(result)
        assertEquals(AuthMessages.clientRegistrationFailed(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `maps server logout branch to server exit code`() {
        val client =
            RealKaliumAuthClient(
                FakeOrchestrator(
                    logoutResult =
                        AuthApiResult.Failure(
                            exitCode = ExitCodes.SERVER_ERROR,
                            message = AuthMessages.authServiceUnavailable(),
                        ),
                ),
            )

        val result = client.logout(authSession())

        val failure = assertIs<AuthApiResult.Failure>(result)
        assertEquals(AuthMessages.authServiceUnavailable(), failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `maps password auth required client registration to password required exit code`() {
        val client =
            RealKaliumAuthClient(
                FakeOrchestrator(
                    loginResult =
                        AuthApiResult.Failure(
                            exitCode = ExitCodes.PASSWORD_REQUIRED,
                            message = AuthMessages.passwordRequired(),
                        ),
                ),
            )

        val result = client.login(loginInput)

        val failure = assertIs<AuthApiResult.Failure>(result)
        assertEquals(AuthMessages.passwordRequired(), failure.message)
        assertEquals(ExitCodes.PASSWORD_REQUIRED, failure.exitCode)
    }

    @Test
    fun `redacts secrets in explicit failure messages`() {
        val client =
            RealKaliumAuthClient(
                FakeOrchestrator(
                    loginResult =
                        AuthApiResult.Failure(
                            exitCode = ExitCodes.SERVER_ERROR,
                            message = "Authentication failed: token=<redacted> password=<redacted>",
                        ),
                ),
            )

        val result = client.login(loginInput)

        val failure = assertIs<AuthApiResult.Failure>(result)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
        assertEquals(
            "Authentication failed: token=<redacted> password=<redacted>",
            failure.message,
        )
    }

    @Test
    fun `throws on blank login email precondition`() {
        val client =
            RealKaliumAuthClient(
                FakeOrchestrator(
                    loginResult =
                        AuthApiResult.Failure(
                            exitCode = ExitCodes.AUTH_FAILED,
                            message = "",
                        ),
                ),
            )

        assertFailsWith<IllegalArgumentException> {
            client.login(LoginInput(email = " ", password = "valid-password", server = null))
        }
    }

    private fun authSession(): AuthSession {
        return AuthSession(
            userId = "jane@example.com",
            accessToken = "token",
            server = null,
        )
    }

    private class FakeOrchestrator(
        private val loginResult: AuthApiResult =
            AuthApiResult.Success(
                AuthSession(
                    userId = "jane@example.com",
                    accessToken = "access-token",
                    server = null,
                ),
            ),
        private val logoutResult: AuthApiResult =
            AuthApiResult.Success(
                AuthSession(
                    userId = "jane@example.com",
                    accessToken = "token",
                    server = null,
                ),
            ),
    ) : AuthenticationOrchestrator {
        override fun login(input: LoginInput): AuthApiResult {
            require(input.email.isNotBlank()) { "Login email must not be blank." }
            require(input.password.isNotBlank()) { "Login password must not be blank." }
            return loginResult
        }

        override fun logout(session: AuthSession): AuthApiResult = logoutResult
    }
}

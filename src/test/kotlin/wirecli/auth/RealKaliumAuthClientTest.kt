package wirecli.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RealKaliumAuthClientTest {
    private val loginInput = LoginInput(email = "jane@example.com", password = "correct-horse", server = null)

    @Test
    fun `maps invalid credentials login branch to auth failed exit code`() {
        val client =
            RealKaliumAuthClient(
                FakeRuntime(
                    authScopeResult =
                        AuthStepResult.Success(
                            FakeAuthScope(AuthStepResult.Failure(AuthFailureCategory.INVALID_CREDENTIALS)),
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
                FakeRuntime(
                    authScopeResult = AuthStepResult.Failure(AuthFailureCategory.NETWORK),
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
                FakeRuntime(
                    authScopeResult = AuthStepResult.Success(FakeAuthScope(AuthStepResult.Success(authenticatedPrincipal()))),
                    addAccountResult = AuthStepResult.Success(Unit),
                    resolveSessionResult = AuthStepResult.Success(KaliumSessionScope("jane@example.com")),
                    ensureClientResult = AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED),
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
                FakeRuntime(
                    authScopeResult = AuthStepResult.Success(FakeAuthScope(AuthStepResult.Success(authenticatedPrincipal()))),
                    logoutResult = AuthStepResult.Failure(AuthFailureCategory.SERVER),
                ),
            )

        val result = client.logout(authSession())

        val failure = assertIs<AuthApiResult.Failure>(result)
        assertEquals(AuthMessages.authServiceUnavailable(), failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `redacts secrets in explicit failure messages`() {
        val client =
            RealKaliumAuthClient(
                FakeRuntime(
                    authScopeResult =
                        AuthStepResult.Failure(
                            category = AuthFailureCategory.SERVER,
                            message = "Authentication failed: token=abc123 password=super-secret",
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

    private fun authenticatedPrincipal(): AuthenticatedPrincipal {
        return AuthenticatedPrincipal(
            userId = "jane@example.com",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            tokenType = "Bearer",
            cookieLabel = null,
            serverConfigId = "production",
            ssoId = null,
            managedBy = null,
            proxyCredentials = null,
        )
    }

    private fun authSession(): AuthSession {
        return AuthSession(
            userId = "jane@example.com",
            accessToken = "token",
            server = null,
        )
    }

    private class FakeRuntime(
        private val authScopeResult: AuthStepResult<KaliumAuthScope>,
        private val addAccountResult: AuthStepResult<Unit> = AuthStepResult.Success(Unit),
        private val resolveSessionResult: AuthStepResult<KaliumSessionScope> =
            AuthStepResult.Success(KaliumSessionScope("jane@example.com")),
        private val ensureClientResult: AuthStepResult<Unit> = AuthStepResult.Success(Unit),
        private val logoutResult: AuthStepResult<Unit> = AuthStepResult.Success(Unit),
    ) : RealKaliumAuthRuntime {
        override fun resolveAuthScope(server: String?): AuthStepResult<KaliumAuthScope> = authScopeResult

        override fun addAuthenticatedAccount(account: PersistedAccount): AuthStepResult<Unit> = addAccountResult

        override fun resolveSessionScope(userId: String): AuthStepResult<KaliumSessionScope> = resolveSessionResult

        override fun ensureClient(
            sessionScope: KaliumSessionScope,
            password: String,
        ): AuthStepResult<Unit> = ensureClientResult

        override fun logout(session: AuthSession): AuthStepResult<Unit> = logoutResult

        override fun shutdown() {
        }
    }

    private class FakeAuthScope(
        private val loginResult: AuthStepResult<AuthenticatedPrincipal>,
    ) : KaliumAuthScope {
        override fun login(
            email: String,
            password: String,
        ): AuthStepResult<AuthenticatedPrincipal> = loginResult
    }
}

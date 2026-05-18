package wirecli.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthResponseParserTest {
    private val parser = StandardAuthResponseParser()
    private val server = "production"

    @Test
    fun `parseSuccess returns valid session with correct userId`() {
        val principal = authenticatedPrincipal()

        val result = parser.parseSuccess(principal, server)

        assertIs<AuthApiResult.Success>(result)
        assertEquals(principal.userId, result.session.userId)
    }

    @Test
    fun `parseSuccess returns valid session with correct accessToken`() {
        val principal = authenticatedPrincipal()

        val result = parser.parseSuccess(principal, server)

        assertIs<AuthApiResult.Success>(result)
        assertEquals(principal.accessToken, result.session.accessToken)
    }

    @Test
    fun `parseSuccess returns valid session with correct server`() {
        val principal = authenticatedPrincipal()
        val customServer = "custom.wire.link"

        val result = parser.parseSuccess(principal, customServer)

        assertIs<AuthApiResult.Success>(result)
        assertEquals(customServer, result.session.server)
    }

    @Test
    fun `parseSuccess handles null server`() {
        val principal = authenticatedPrincipal()

        val result = parser.parseSuccess(principal, null)

        assertIs<AuthApiResult.Success>(result)
        assertEquals(null, result.session.server)
    }

    @Test
    fun `parseFailure with INVALID_CREDENTIALS returns correct message and exit code`() {
        val failure = AuthStepResult.Failure(AuthFailureCategory.INVALID_CREDENTIALS)

        val result = parser.parseFailure(failure, action = "Authentication")

        assertIs<AuthApiResult.Failure>(result)
        assertEquals(AuthMessages.INVALID_CREDENTIALS, result.message)
        assertEquals(ExitCodes.AUTH_FAILED, result.exitCode)
    }

    @Test
    fun `parseFailure with PASSWORD_REQUIRED returns correct message and exit code`() {
        val failure = AuthStepResult.Failure(AuthFailureCategory.PASSWORD_REQUIRED)

        val result = parser.parseFailure(failure, action = "Authentication")

        assertIs<AuthApiResult.Failure>(result)
        assertEquals(AuthMessages.PASSWORD_REQUIRED, result.message)
        assertEquals(ExitCodes.PASSWORD_REQUIRED, result.exitCode)
    }

    @Test
    fun `parseFailure with NETWORK returns correct message and exit code`() {
        val failure = AuthStepResult.Failure(AuthFailureCategory.NETWORK)

        val result = parser.parseFailure(failure, action = "Authentication")

        assertIs<AuthApiResult.Failure>(result)
        assertEquals(AuthMessages.networkFailure("Authentication"), result.message)
        assertEquals(ExitCodes.NETWORK_ERROR, result.exitCode)
    }

    @Test
    fun `parseFailure with SERVER returns correct message and exit code`() {
        val failure = AuthStepResult.Failure(AuthFailureCategory.SERVER)

        val result = parser.parseFailure(failure, action = "Authentication")

        assertIs<AuthApiResult.Failure>(result)
        assertEquals(AuthMessages.AUTH_SERVICE_UNAVAILABLE, result.message)
        assertEquals(ExitCodes.SERVER_ERROR, result.exitCode)
    }

    @Test
    fun `parseFailure with UNAUTHORIZED returns correct message and exit code`() {
        val failure = AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)

        val result = parser.parseFailure(failure, action = "TestAction")

        assertIs<AuthApiResult.Failure>(result)
        assertEquals(AuthMessages.unauthorizedAction("TestAction"), result.message)
        assertEquals(ExitCodes.UNAUTHORIZED, result.exitCode)
    }

    @Test
    fun `parseFailure with NOMAD_SINGLE_USER_VIOLATION returns correct message and exit code`() {
        val failure = AuthStepResult.Failure(AuthFailureCategory.NOMAD_SINGLE_USER_VIOLATION)

        val result = parser.parseFailure(failure, action = "Authentication")

        assertIs<AuthApiResult.Failure>(result)
        assertEquals(
            "Nomad single user mode violation: cannot add additional users.",
            result.message,
        )
        assertEquals(ExitCodes.NOMAD_SINGLE_USER_VIOLATION, result.exitCode)
    }

    @Test
    fun `parseFailure with UNKNOWN returns correct message and exit code`() {
        val failure = AuthStepResult.Failure(AuthFailureCategory.UNKNOWN)

        val result = parser.parseFailure(failure, action = "Authentication")

        assertIs<AuthApiResult.Failure>(result)
        assertEquals("Unexpected authentication error. Please retry.", result.message)
        assertEquals(ExitCodes.UNKNOWN_ERROR, result.exitCode)
    }

    @Test
    fun `parseFailure with explicit message uses explicit message`() {
        val explicitMessage = "Custom error message"
        val failure = AuthStepResult.Failure(AuthFailureCategory.SERVER, message = explicitMessage)

        val result = parser.parseFailure(failure, action = "Authentication")

        assertIs<AuthApiResult.Failure>(result)
        assertEquals(explicitMessage, result.message)
    }

    @Test
    fun `parseFailure redacts secrets in explicit failure messages`() {
        val failure =
            AuthStepResult.Failure(
                category = AuthFailureCategory.SERVER,
                message = "Authentication failed: token=abc123 password=super-secret",
            )

        val result = parser.parseFailure(failure, action = "Authentication")

        assertIs<AuthApiResult.Failure>(result)
        assertEquals(
            "Authentication failed: token=<redacted> password=<redacted>",
            result.message,
        )
        assertEquals(ExitCodes.SERVER_ERROR, result.exitCode)
    }

    @Test
    fun `parseFailure with defaultMessage override uses default message`() {
        val failure = AuthStepResult.Failure(AuthFailureCategory.SERVER)
        val customDefault = "Custom default message"

        val result = parser.parseFailure(failure, action = "Authentication", defaultMessage = customDefault)

        assertIs<AuthApiResult.Failure>(result)
        assertEquals(customDefault, result.message)
    }

    @Test
    fun `parseFailure explicit message takes precedence over defaultMessage`() {
        val explicitMessage = "Explicit failure message"
        val customDefault = "Custom default message"
        val failure = AuthStepResult.Failure(AuthFailureCategory.SERVER, message = explicitMessage)

        val result = parser.parseFailure(failure, action = "Authentication", defaultMessage = customDefault)

        assertIs<AuthApiResult.Failure>(result)
        assertEquals(explicitMessage, result.message)
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
}

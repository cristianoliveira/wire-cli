package wirecli.presence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import wirecli.auth.LoginInput

class AuthGuardedPresenceServiceTest {
    @Test
    fun `returns auth failure when session is not active`() {
        val delegate = TrackingPresenceService(PresenceResult.Success(PresenceView(PresenceState.ONLINE)))
        val service = AuthGuardedPresenceService(
            authSessionService = FakeAuthSessionService(
                authResult = AuthResult.Failure(
                    message = "Session is invalid or expired. Run wire login to re-authenticate.",
                    exitCode = ExitCodes.UNAUTHORIZED
                )
            ),
            delegate = delegate
        )

        val result = service.getCurrentPresence()

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
        assertTrue(failure.message.contains("Run wire login"))
        assertFalse(delegate.invoked)
    }

    @Test
    fun `delegates presence lookup when auth check succeeds`() {
        val delegate = TrackingPresenceService(PresenceResult.Success(PresenceView(PresenceState.AWAY)))
        val service = AuthGuardedPresenceService(
            authSessionService = FakeAuthSessionService(authResult = AuthResult.Success("Active session available.")),
            delegate = delegate
        )

        val result = service.getCurrentPresence()

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.AWAY, success.presence.state)
        assertTrue(delegate.invoked)
    }

    private class FakeAuthSessionService(private val authResult: AuthResult) : AuthSessionService {
        override fun login(input: LoginInput): AuthResult = AuthResult.Success("unused")

        override fun logout(): AuthResult = AuthResult.Success("unused")

        override fun requireActiveSession(): AuthResult = authResult
    }

    private class TrackingPresenceService(private val result: PresenceResult) : PresenceService {
        var invoked: Boolean = false

        override fun getCurrentPresence(): PresenceResult {
            invoked = true
            return result
        }
    }
}

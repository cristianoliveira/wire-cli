package wirecli.presence

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import wirecli.auth.LoginInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthGuardedPresenceServiceTest {
    @Test
    fun `returns auth failure when session is not active`() {
        val delegate =
            TrackingPresenceService(
                getResult = PresenceResult.Success(PresenceView(PresenceState.ONLINE)),
                setResult = PresenceResult.Success(PresenceView(PresenceState.ONLINE)),
            )
        val service =
            AuthGuardedPresenceService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult =
                            AuthResult.Failure(
                                message = "Session is invalid or expired. Run wire login to re-authenticate.",
                                exitCode = ExitCodes.UNAUTHORIZED,
                            ),
                    ),
                delegate = delegate,
            )

        val result = service.getCurrentPresence()

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
        assertTrue(failure.message.contains("Run wire login"))
        assertFalse(delegate.getInvoked)
    }

    @Test
    fun `delegates presence lookup when auth check succeeds`() {
        val delegate =
            TrackingPresenceService(
                getResult = PresenceResult.Success(PresenceView(PresenceState.AWAY)),
                setResult = PresenceResult.Success(PresenceView(PresenceState.ONLINE)),
            )
        val service =
            AuthGuardedPresenceService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Success("Active session available."),
                    ),
                delegate = delegate,
            )

        val result = service.getCurrentPresence()

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.AWAY, success.presence.state)
        assertTrue(delegate.getInvoked)
    }

    @Test
    fun `returns auth failure for set when session is not active`() {
        val delegate =
            TrackingPresenceService(
                getResult = PresenceResult.Success(PresenceView(PresenceState.ONLINE)),
                setResult = PresenceResult.Success(PresenceView(PresenceState.ONLINE)),
            )
        val service =
            AuthGuardedPresenceService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult =
                            AuthResult.Failure(
                                message = "Session is invalid or expired. Run wire login to re-authenticate.",
                                exitCode = ExitCodes.UNAUTHORIZED,
                            ),
                    ),
                delegate = delegate,
            )

        val result = service.setCurrentPresence(WritablePresenceState.BUSY)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
        assertTrue(failure.message.contains("Run wire login"))
        assertFalse(delegate.setInvoked)
    }

    @Test
    fun `delegates set when auth check succeeds`() {
        val delegate =
            TrackingPresenceService(
                getResult = PresenceResult.Success(PresenceView(PresenceState.AWAY)),
                setResult = PresenceResult.Success(PresenceView(PresenceState.BUSY)),
            )
        val service =
            AuthGuardedPresenceService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Success("Active session available."),
                    ),
                delegate = delegate,
            )

        val result = service.setCurrentPresence(WritablePresenceState.BUSY)

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.BUSY, success.presence.state)
        assertTrue(delegate.setInvoked)
        assertEquals(WritablePresenceState.BUSY, delegate.lastSetState)
    }

    private class FakeAuthSessionService(private val authResult: AuthResult) : AuthSessionService {
        override fun login(input: LoginInput): AuthResult = AuthResult.Success("unused")

        override fun logout(): AuthResult = AuthResult.Success("unused")

        override fun requireActiveSession(): AuthResult = authResult
    }

    private class TrackingPresenceService(
        private val getResult: PresenceResult,
        private val setResult: PresenceResult,
    ) : PresenceService {
        var getInvoked: Boolean = false
        var setInvoked: Boolean = false
        var lastSetState: WritablePresenceState? = null

        override fun getCurrentPresence(): PresenceResult {
            getInvoked = true
            return getResult
        }

        override fun setCurrentPresence(state: WritablePresenceState): PresenceResult {
            setInvoked = true
            lastSetState = state
            return setResult
        }
    }
}

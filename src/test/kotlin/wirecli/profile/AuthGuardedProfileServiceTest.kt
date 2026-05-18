package wirecli.profile

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import wirecli.auth.LoginInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthGuardedProfileServiceTest {
    @Test
    fun `returns auth failure when session is not active`() {
        val delegate =
            TrackingProfileService(ProfileResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane")))
        val service =
            AuthGuardedProfileService(
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

        val result = service.getCurrentProfile()

        val failure = assertIs<ProfileResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
        assertTrue(failure.message.contains("Run wire login"))
        assertFalse(delegate.invoked)
    }

    @Test
    fun `returns auth failure for updateProfile when session is not active`() {
        val delegate =
            TrackingProfileService(ProfileResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane")))
        val service =
            AuthGuardedProfileService(
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

        val result = service.updateProfile(ProfileUpdate(name = "New Name"))

        val failure = assertIs<ProfileUpdateResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
        assertTrue(failure.message.contains("Run wire login"))
        assertFalse(delegate.updateInvoked)
    }

    @Test
    fun `delegates profile update when auth check succeeds`() {
        val delegate =
            TrackingProfileService(ProfileResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane")))
        val service =
            AuthGuardedProfileService(
                authSessionService = FakeAuthSessionService(authResult = AuthResult.Success("Active session available.")),
                delegate = delegate,
            )

        val result = service.updateProfile(ProfileUpdate(name = "New Name"))

        val success = assertIs<ProfileUpdateResult.Success>(result)
        assertEquals("New Name", success.profile.name)
        assertTrue(delegate.updateInvoked)
    }

    @Test
    fun `delegates profile lookup when auth check succeeds`() {
        val delegate =
            TrackingProfileService(ProfileResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane")))
        val service =
            AuthGuardedProfileService(
                authSessionService = FakeAuthSessionService(authResult = AuthResult.Success("Active session available.")),
                delegate = delegate,
            )

        val result = service.getCurrentProfile()

        val success = assertIs<ProfileResult.Success>(result)
        assertEquals("jane", success.profile.handle)
        assertTrue(delegate.invoked)
    }

    private class FakeAuthSessionService(private val authResult: AuthResult) : AuthSessionService {
        override fun login(input: LoginInput): AuthResult = AuthResult.Success("unused")

        override fun logout(): AuthResult = AuthResult.Success("unused")

        override fun requireActiveSession(): AuthResult = authResult
    }

    private class TrackingProfileService(
        private val result: ProfileResult,
        private val updateResult: ProfileUpdateResult =
            ProfileUpdateResult.Success(
                ProfileView(name = "New Name", email = "jane@example.com", handle = "newhandle"),
            ),
    ) : ProfileService {
        var invoked: Boolean = false
        var updateInvoked: Boolean = false

        override fun getCurrentProfile(): ProfileResult {
            invoked = true
            return result
        }

        override fun updateProfile(update: ProfileUpdate): ProfileUpdateResult {
            updateInvoked = true
            return updateResult
        }
    }
}

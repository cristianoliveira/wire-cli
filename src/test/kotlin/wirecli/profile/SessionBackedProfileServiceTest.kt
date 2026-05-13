package wirecli.profile

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import wirecli.presence.PresenceApiClient
import wirecli.presence.PresenceResult
import wirecli.presence.PresenceState
import wirecli.presence.PresenceView
import wirecli.presence.WritablePresenceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionBackedProfileServiceTest {
    @Test
    fun `updateProfile returns unauthorized when no session is persisted`() {
        val service =
            SessionBackedProfileService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient =
                    FakeProfileApiClient(
                        ProfileResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane")),
                    ),
                presenceApiClient = FakePresenceApiClient(PresenceResult.Success(PresenceView(PresenceState.ONLINE))),
            )

        val result = service.updateProfile(ProfileUpdate(name = "New Name"))

        val failure = assertIs<ProfileUpdateResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `updateProfile returns validation error when no changes provided`() {
        val service =
            SessionBackedProfileService(
                sessionStore = FakeSessionStore(activeSession = activeSession()),
                apiClient =
                    FakeProfileApiClient(
                        ProfileResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane")),
                    ),
                presenceApiClient = FakePresenceApiClient(PresenceResult.Success(PresenceView(PresenceState.ONLINE))),
            )

        val result = service.updateProfile(ProfileUpdate())

        val failure = assertIs<ProfileUpdateResult.Failure>(result)
        assertEquals("At least one of --name or --handle must be provided.", failure.message)
        assertEquals(ExitCodes.VALIDATION_ERROR, failure.exitCode)
    }

    @Test
    fun `updateProfile delegates to api client when session is active`() {
        val service =
            SessionBackedProfileService(
                sessionStore = FakeSessionStore(activeSession = activeSession()),
                apiClient =
                    FakeProfileApiClient(
                        ProfileResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane")),
                        updateResult =
                            ProfileUpdateResult.Success(
                                ProfileView(name = "New Name", email = "jane@example.com", handle = "newhandle"),
                            ),
                    ),
                presenceApiClient = FakePresenceApiClient(PresenceResult.Success(PresenceView(PresenceState.ONLINE))),
            )

        val result = service.updateProfile(ProfileUpdate(name = "New Name", handle = "newhandle"))

        val success = assertIs<ProfileUpdateResult.Success>(result)
        assertEquals("New Name", success.profile.name)
        assertEquals("newhandle", success.profile.handle)
    }

    @Test
    fun `returns unauthorized when no session is persisted`() {
        val service =
            SessionBackedProfileService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient =
                    FakeProfileApiClient(
                        ProfileResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane")),
                    ),
                presenceApiClient = FakePresenceApiClient(PresenceResult.Success(PresenceView(PresenceState.ONLINE))),
            )

        val result = service.getCurrentProfile()

        val failure = assertIs<ProfileResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `includes normalized presence when profile and presence calls succeed`() {
        val service =
            SessionBackedProfileService(
                sessionStore = FakeSessionStore(activeSession = activeSession()),
                apiClient =
                    FakeProfileApiClient(
                        ProfileResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane")),
                    ),
                presenceApiClient = FakePresenceApiClient(PresenceResult.Success(PresenceView(PresenceState.BUSY))),
            )

        val result = service.getCurrentProfile()

        val success = assertIs<ProfileResult.Success>(result)
        assertEquals(PresenceState.BUSY, success.profile.presence)
    }

    @Test
    fun `falls back to unknown presence when presence call fails but profile succeeds`() {
        val service =
            SessionBackedProfileService(
                sessionStore = FakeSessionStore(activeSession = activeSession()),
                apiClient =
                    FakeProfileApiClient(
                        ProfileResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane")),
                    ),
                presenceApiClient =
                    FakePresenceApiClient(
                        PresenceResult.Failure(
                            message = "Presence fetch failed: network is unreachable. Check your connection and retry.",
                            exitCode = ExitCodes.NETWORK_ERROR,
                        ),
                    ),
            )

        val result = service.getCurrentProfile()

        val success = assertIs<ProfileResult.Success>(result)
        assertEquals(PresenceState.UNKNOWN, success.profile.presence)
    }

    @Test
    fun `returns profile failure without leaking profile output when profile fetch fails`() {
        val service =
            SessionBackedProfileService(
                sessionStore = FakeSessionStore(activeSession = activeSession()),
                apiClient =
                    FakeProfileApiClient(
                        ProfileResult.Failure(
                            message = "Session is invalid or expired. Run wire login to re-authenticate.",
                            exitCode = ExitCodes.UNAUTHORIZED,
                        ),
                    ),
                presenceApiClient = FakePresenceApiClient(PresenceResult.Success(PresenceView(PresenceState.ONLINE))),
            )

        val result = service.getCurrentProfile()

        val failure = assertIs<ProfileResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
        assertEquals("Session is invalid or expired. Run wire login to re-authenticate.", failure.message)
    }

    private fun activeSession(): AuthSession {
        return AuthSession(
            userId = "jane",
            accessToken = "token",
            server = null,
        )
    }

    private class FakeSessionStore(private val activeSession: AuthSession?) : SessionProvider {
        override fun readActiveSession(): AuthSession? = activeSession
    }

    private class FakeProfileApiClient(
        private val result: ProfileResult,
        private val updateResult: ProfileUpdateResult = ProfileUpdateResult.Success(ProfileView(name = "Jane", email = "jane@example.com", handle = "jane")),
    ) : ProfileApiClient {
        override fun fetchProfile(session: AuthSession): ProfileResult = result

        override fun updateProfile(session: AuthSession, update: ProfileUpdate): ProfileUpdateResult = updateResult
    }

    private class FakePresenceApiClient(private val result: PresenceResult) : PresenceApiClient {
        override fun fetchPresence(session: AuthSession): PresenceResult = result

        override fun updatePresence(
            session: AuthSession,
            state: WritablePresenceState,
        ): PresenceResult {
            return result
        }
    }
}

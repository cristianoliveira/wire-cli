package wirecli.user

import wirecli.auth.AuthMessages
import wirecli.auth.AuthResult
import wirecli.auth.AuthSession
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UserServiceTest {
    private val session =
        AuthSession(
            userId = "self-uuid@example.wire.com",
            accessToken = "token",
            server = null,
        )
    private val query = UserSearchQuery(query = "alice")

    @Test
    fun `SessionBacked search returns unauthorized when no session`() {
        val service = SessionBackedUserService(NoSessionStore, RecordingUserApiClient())

        val result = service.search(query)

        val failure = assertIs<UserSearchResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `SessionBacked search delegates to api client for persisted session`() {
        val apiClient = RecordingUserApiClient(searchResult = UserSearchResult.Success(UserListView(emptyList())))
        val service = SessionBackedUserService(SessionStore(session), apiClient)

        val result = service.search(query)

        assertIs<UserSearchResult.Success>(result)
        assertEquals(query, apiClient.lastSearchQuery)
    }

    @Test
    fun `SessionBacked get delegates userId to api client`() {
        val apiClient =
            RecordingUserApiClient(
                getResult = UserGetResult.Success(UserView(id = "u", name = "U", handle = null)),
            )
        val service = SessionBackedUserService(SessionStore(session), apiClient)

        val result = service.get("u@example.wire.com")

        assertIs<UserGetResult.Success>(result)
        assertEquals("u@example.wire.com", apiClient.lastGetUserId)
    }

    @Test
    fun `SessionBacked get returns unauthorized when no session`() {
        val service = SessionBackedUserService(NoSessionStore, RecordingUserApiClient())

        val result = service.get("u@example.wire.com")

        val failure = assertIs<UserGetResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `AuthGuarded search returns auth failure when session missing`() {
        val service =
            AuthGuardedUserService(
                FailingAuthSessionService,
                SessionBackedUserService(NoSessionStore, RecordingUserApiClient()),
            )

        val result = service.search(query)

        val failure = assertIs<UserSearchResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
    }

    @Test
    fun `AuthGuarded get returns auth failure when session missing`() {
        val service =
            AuthGuardedUserService(
                FailingAuthSessionService,
                SessionBackedUserService(NoSessionStore, RecordingUserApiClient()),
            )

        val result = service.get("u@example.wire.com")

        assertIs<UserGetResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), result.message)
    }

    private object NoSessionStore : SessionProvider {
        override fun readActiveSession(): AuthSession? = null
    }

    private class SessionStore(private val active: AuthSession) : SessionProvider {
        override fun readActiveSession(): AuthSession? = active
    }

    private object FailingAuthSessionService : AuthSessionService {
        override fun login(input: wirecli.auth.LoginInput): AuthResult = AuthResult.Failure("nope", ExitCodes.UNAUTHORIZED)

        override fun logout(): AuthResult = AuthResult.Failure("nope", ExitCodes.UNAUTHORIZED)

        override fun requireActiveSession(): AuthResult = AuthResult.Failure(AuthMessages.noActiveSession(), ExitCodes.UNAUTHORIZED)
    }

    private class RecordingUserApiClient(
        private val searchResult: UserSearchResult =
            UserSearchResult.Success(UserListView(emptyList())),
        private val getResult: UserGetResult =
            UserGetResult.Success(UserView(id = "u", name = "U", handle = null)),
    ) : UserApiClient {
        var lastSearchQuery: UserSearchQuery? = null
            private set
        var lastGetUserId: String? = null
            private set

        override fun searchUsers(
            session: AuthSession,
            query: UserSearchQuery,
        ): UserSearchResult {
            lastSearchQuery = query
            return searchResult
        }

        override fun getUser(
            session: AuthSession,
            userId: String,
        ): UserGetResult {
            lastGetUserId = userId
            return getResult
        }
    }
}

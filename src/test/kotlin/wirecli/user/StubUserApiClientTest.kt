package wirecli.user

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StubUserApiClientTest {
    private val session =
        AuthSession(
            userId = "self-uuid@example.wire.com",
            accessToken = "token",
            server = null,
        )

    @Test
    fun `search returns matching users by default`() {
        val client = StubUserApiClient(emptyMap())

        val result = client.searchUsers(session, UserSearchQuery(query = "alice"))

        val success = assertIs<UserSearchResult.Success>(result)
        assertEquals(1, success.view.users.size)
        assertEquals("alice", success.view.users[0].handle)
        assertEquals("alice-uuid@example.wire.com", success.view.users[0].id)
        assertEquals(UserConnectionState.ACCEPTED, success.view.users[0].connection)
    }

    @Test
    fun `search matches by handle name or email case-insensitively`() {
        val client = StubUserApiClient(emptyMap())

        val byHandle = client.searchUsers(session, UserSearchQuery(query = "BOB"))
        val byEmail = client.searchUsers(session, UserSearchQuery(query = "cara@example.com"))

        assertEquals(1, (byEmail as UserSearchResult.Success).view.users.size)
        assertEquals("bob", (byHandle as UserSearchResult.Success).view.users[0].handle)
    }

    @Test
    fun `contacts-only search excludes users without accepted connection`() {
        val client = StubUserApiClient(emptyMap())

        val result = client.searchUsers(session, UserSearchQuery(query = "cara", contactsOnly = true))

        val success = assertIs<UserSearchResult.Success>(result)
        assertTrue(success.view.users.isEmpty())
    }

    @Test
    fun `search respects the limit`() {
        val client = StubUserApiClient(emptyMap())

        // Empty-ish query matches all known users, then capped by limit.
        val result = client.searchUsers(session, UserSearchQuery(query = "e", limit = 2))

        val success = assertIs<UserSearchResult.Success>(result)
        assertTrue(success.view.users.size <= 2)
    }

    @Test
    fun `search returns empty list when nothing matches`() {
        val client = StubUserApiClient(emptyMap())

        val result = client.searchUsers(session, UserSearchQuery(query = "zzz-nope"))

        val success = assertIs<UserSearchResult.Success>(result)
        assertEquals(0, success.view.users.size)
    }

    @Test
    fun `search maps network error mode`() {
        val client = StubUserApiClient(mapOf("WIRE_STUB_MODE" to "user_search_network_error"))

        val result = client.searchUsers(session, UserSearchQuery(query = "alice"))

        val failure = assertIs<UserSearchResult.Failure>(result)
        assertEquals(UserMessages.SEARCH_NETWORK_FAILURE, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `search maps server error mode`() {
        val client = StubUserApiClient(mapOf("WIRE_STUB_MODE" to "user_search_server_error"))

        val result = client.searchUsers(session, UserSearchQuery(query = "alice"))

        val failure = assertIs<UserSearchResult.Failure>(result)
        assertEquals(UserMessages.SEARCH_SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `search maps unauthorized mode`() {
        val client = StubUserApiClient(mapOf("WIRE_STUB_MODE" to "user_unauthorized"))

        val result = client.searchUsers(session, UserSearchQuery(query = "alice"))

        val failure = assertIs<UserSearchResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `get returns user detail for known id`() {
        val client = StubUserApiClient(emptyMap())

        val result = client.getUser(session, "alice-uuid@example.wire.com")

        val success = assertIs<UserGetResult.Success>(result)
        assertEquals("alice", success.view.handle)
        assertEquals("Acme", success.view.team)
    }

    @Test
    fun `get returns not found for unknown id`() {
        val client = StubUserApiClient(emptyMap())

        val result = client.getUser(session, "ghost@example.wire.com")

        val failure = assertIs<UserGetResult.Failure>(result)
        assertEquals(UserMessages.USER_NOT_FOUND, failure.message)
        assertEquals(UserExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `get maps network and server error modes`() {
        val network =
            StubUserApiClient(mapOf("WIRE_STUB_MODE" to "user_get_network_error"))
                .getUser(session, "alice-uuid@example.wire.com") as UserGetResult.Failure
        assertEquals(UserMessages.GET_NETWORK_FAILURE, network.message)
        assertEquals(ExitCodes.NETWORK_ERROR, network.exitCode)

        val server =
            StubUserApiClient(mapOf("WIRE_STUB_MODE" to "user_get_server_error"))
                .getUser(session, "alice-uuid@example.wire.com") as UserGetResult.Failure
        assertEquals(UserMessages.GET_SERVER_FAILURE, server.message)
        assertEquals(ExitCodes.SERVER_ERROR, server.exitCode)
    }
}

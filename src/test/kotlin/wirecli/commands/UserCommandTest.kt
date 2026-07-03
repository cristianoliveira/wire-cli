package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.user.UserConnectionState
import wirecli.user.UserGetResult
import wirecli.user.UserListView
import wirecli.user.UserSearchQuery
import wirecli.user.UserSearchResult
import wirecli.user.UserService
import wirecli.user.UserView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserCommandTest {
    private val alice =
        UserView(
            id = "alice-uuid@example.wire.com",
            name = "Alice Almond",
            handle = "alice",
            email = "alice@example.com",
            team = "Acme",
            connection = UserConnectionState.ACCEPTED,
        )

    @Test
    fun `search command prints table by default`() {
        val service = StubUserService(searchResult = UserSearchResult.Success(UserListView(listOf(alice))))
        val result = execute(UserSearchCommand { service }, listOf("alice"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("alice-uuid@example.wire.com"))
        assertTrue(result.stdout.contains("Alice Almond"))
        assertEquals("alice", service.lastSearchQuery?.query)
    }

    @Test
    fun `search command supports json output`() {
        val service = StubUserService(searchResult = UserSearchResult.Success(UserListView(listOf(alice))))
        val result = execute(UserSearchCommand { service }, listOf("alice", "--json"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"schemaVersion\""))
        assertTrue(result.stdout.contains("\"users\""))
        assertTrue(result.stdout.contains("alice-uuid@example.wire.com"))
    }

    @Test
    fun `search command supports json-lines output`() {
        val service = StubUserService(searchResult = UserSearchResult.Success(UserListView(listOf(alice))))
        val result = execute(UserSearchCommand { service }, listOf("alice", "--json-lines"))

        assertEquals(0, result.exitCode)
        assertEquals(1, result.stdout.trim().split("\n").size)
        assertTrue(result.stdout.contains("alice-uuid@example.wire.com"))
    }

    @Test
    fun `search command surfaces failure with exit code`() {
        val service =
            StubUserService(
                searchResult = UserSearchResult.Failure(message = "boom", exitCode = 19),
            )
        val result = execute(UserSearchCommand { service }, listOf("alice"))

        assertEquals(19, result.exitCode)
        assertTrue(result.stderr.contains("boom"))
    }

    @Test
    fun `search command prints no-results message on empty list`() {
        val service = StubUserService(searchResult = UserSearchResult.Success(UserListView(emptyList())))
        val result = execute(UserSearchCommand { service }, listOf("zzz"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("No users found"))
    }

    @Test
    fun `get command prints detail by default`() {
        val service = StubUserService(getResult = UserGetResult.Success(alice))
        val result = execute(UserGetCommand { service }, listOf("alice-uuid@example.wire.com"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("alice-uuid@example.wire.com"))
        assertTrue(result.stdout.contains("Alice Almond"))
        assertEquals("alice-uuid@example.wire.com", service.lastGetUserId)
    }

    @Test
    fun `get command supports json output`() {
        val service = StubUserService(getResult = UserGetResult.Success(alice))
        val result = execute(UserGetCommand { service }, listOf("alice-uuid@example.wire.com", "--json"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"id\":\"alice-uuid@example.wire.com\""))
    }

    @Test
    fun `get command surfaces not found`() {
        val service =
            StubUserService(
                getResult = UserGetResult.Failure(message = "User not found.", exitCode = 18),
            )
        val result = execute(UserGetCommand { service }, listOf("ghost@example.wire.com"))

        assertEquals(18, result.exitCode)
        assertTrue(result.stderr.contains("User not found"))
    }

    @Test
    fun `get command rejects invalid user id`() {
        val service = StubUserService()
        val result = execute(UserGetCommand { service }, listOf("not-a-qualified-id"))

        assertEquals(14, result.exitCode)
        assertEquals(null, service.lastGetUserId)
    }

    @Test
    fun `search command forwards --limit to service`() {
        val service = StubUserService(searchResult = UserSearchResult.Success(UserListView(emptyList())))
        execute(UserSearchCommand { service }, listOf("a", "--limit", "5"))

        assertEquals(5, service.lastSearchQuery?.limit)
    }

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private inline fun <reified C : com.github.ajalt.clikt.core.CliktCommand> execute(
        command: C,
        args: List<String>,
    ): ExecutionResult {
        val stdoutBuffer = java.io.ByteArrayOutputStream()
        val stderrBuffer = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err

        var exitCode = 0
        try {
            System.setOut(java.io.PrintStream(stdoutBuffer))
            System.setErr(java.io.PrintStream(stderrBuffer))
            command.parse(args)
        } catch (programResult: ProgramResult) {
            exitCode = programResult.statusCode
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        return ExecutionResult(
            exitCode = exitCode,
            stdout = stdoutBuffer.toString(Charsets.UTF_8),
            stderr = stderrBuffer.toString(Charsets.UTF_8),
        )
    }

    private class StubUserService(
        private val searchResult: UserSearchResult =
            UserSearchResult.Success(UserListView(emptyList())),
        private val getResult: UserGetResult =
            UserGetResult.Success(
                UserView(
                    id = "u",
                    name = "U",
                    handle = null,
                    email = null,
                    team = null,
                    connection = UserConnectionState.UNKNOWN,
                ),
            ),
    ) : UserService {
        var lastSearchQuery: UserSearchQuery? = null
            private set
        var lastGetUserId: String? = null
            private set

        override fun search(query: UserSearchQuery): UserSearchResult {
            lastSearchQuery = query
            return searchResult
        }

        override fun get(userId: String): UserGetResult {
            lastGetUserId = userId
            return getResult
        }
    }
}

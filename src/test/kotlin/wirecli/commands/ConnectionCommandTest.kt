package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.connection.ConnectionActionResult
import wirecli.connection.ConnectionService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionCommandTest {
    @Test
    fun `request command prints success message`() {
        val service = StubConnectionService()
        val result = execute(ConnectionRequestCommand { service }, listOf("bob-uuid@example.wire.com"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Connection request sent."))
        assertEquals("bob-uuid@example.wire.com", service.lastRequestUserId)
    }

    @Test
    fun `request command rejects invalid user id`() {
        val service = StubConnectionService()
        val result = execute(ConnectionRequestCommand { service }, listOf("not-a-qualified-id"))

        assertEquals(14, result.exitCode)
        assertTrue(result.stderr.contains("validation error", ignoreCase = true))
        assertEquals(null, service.lastRequestUserId)
    }

    @Test
    fun `request command surfaces failure with exit code`() {
        val service =
            StubConnectionService(
                requestResult =
                    ConnectionActionResult.Failure(
                        message = "You are already connected with this user.",
                        exitCode = 17,
                    ),
            )
        val result = execute(ConnectionRequestCommand { service }, listOf("bob-uuid@example.wire.com"))

        assertEquals(17, result.exitCode)
        assertTrue(result.stderr.contains("already connected"))
    }

    @Test
    fun `block command requires --yes confirmation`() {
        val service = StubConnectionService()
        val result = execute(ConnectionBlockCommand { service }, listOf("bob-uuid@example.wire.com"))

        assertEquals(14, result.exitCode)
        assertTrue(result.stderr.contains("--yes"))
        assertEquals(null, service.lastBlockUserId)
    }

    @Test
    fun `block command proceeds with --yes`() {
        val service = StubConnectionService()
        val result =
            execute(ConnectionBlockCommand { service }, listOf("bob-uuid@example.wire.com", "--yes"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("User blocked."))
        assertEquals("bob-uuid@example.wire.com", service.lastBlockUserId)
    }

    @Test
    fun `unblock command prints success message`() {
        val service = StubConnectionService()
        val result = execute(ConnectionUnblockCommand { service }, listOf("bob-uuid@example.wire.com"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("User unblocked."))
        assertEquals("bob-uuid@example.wire.com", service.lastUnblockUserId)
    }

    @Test
    fun `action commands support json output`() {
        val service = StubConnectionService()
        val result =
            execute(
                ConnectionRequestCommand { service },
                listOf("bob-uuid@example.wire.com", "--json"),
            )

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"ok\":true"))
        assertTrue(result.stdout.contains("\"message\":\"Connection request sent.\""))
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

    private class StubConnectionService(
        private val requestResult: ConnectionActionResult =
            ConnectionActionResult.Success("Connection request sent."),
        private val blockResult: ConnectionActionResult =
            ConnectionActionResult.Success("User blocked."),
        private val unblockResult: ConnectionActionResult =
            ConnectionActionResult.Success("User unblocked."),
    ) : ConnectionService {
        var lastRequestUserId: String? = null
            private set
        var lastBlockUserId: String? = null
            private set
        var lastUnblockUserId: String? = null
            private set

        override fun sendRequest(userId: String): ConnectionActionResult {
            lastRequestUserId = userId
            return requestResult
        }

        override fun blockUser(userId: String): ConnectionActionResult {
            lastBlockUserId = userId
            return blockResult
        }

        override fun unblockUser(userId: String): ConnectionActionResult {
            lastUnblockUserId = userId
            return unblockResult
        }
    }
}

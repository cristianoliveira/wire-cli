package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.message.MessageService
import wirecli.message.SendMessageResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageSendCommandTest {
    // ==================== MessageSendCommand Tests ====================

    @Test
    fun `send command with positional message succeeds`() {
        val command =
            MessageSendCommand {
                StubMessageService(sendMessageResult = SendMessageResult.Success)
            }

        val result = execute(command, listOf("conv-001", "Hello World"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.trim().contains("Message sent"))
    }

    @Test
    fun `send command validates blank conversation ID`() {
        val command =
            MessageSendCommand {
                StubMessageService(sendMessageResult = SendMessageResult.Success)
            }

        val result = execute(command, listOf("", "Hello"))

        assertEquals(14, result.exitCode)
        assertEquals("validation error: conversation required", result.stderr.trim())
    }

    @Test
    fun `send command validates blank message`() {
        val command =
            MessageSendCommand {
                StubMessageService(sendMessageResult = SendMessageResult.Success)
            }

        val result = execute(command, listOf("conv-001", ""))

        assertEquals(14, result.exitCode)
        assertEquals("validation error: message required", result.stderr.trim())
    }

    @Test
    fun `send command validates message with only whitespace`() {
        val command =
            MessageSendCommand {
                StubMessageService(sendMessageResult = SendMessageResult.Success)
            }

        val result = execute(command, listOf("conv-001", "   "))

        assertEquals(14, result.exitCode)
        assertEquals("validation error: message required", result.stderr.trim())
    }

    @Test
    fun `send command returns failure when service fails with network error`() {
        val command =
            MessageSendCommand {
                StubMessageService(
                    sendMessageResult =
                        SendMessageResult.Failure(
                            message = "network error while sending message",
                            exitCode = 12,
                        ),
                )
            }

        val result = execute(command, listOf("conv-001", "Hello"))

        assertEquals(12, result.exitCode)
        assertEquals("network error while sending message", result.stderr.trim())
    }

    @Test
    fun `send command returns failure when service fails with authorization error`() {
        val command =
            MessageSendCommand {
                StubMessageService(
                    sendMessageResult =
                        SendMessageResult.Failure(
                            message = "you must be logged in to send messages",
                            exitCode = 11,
                        ),
                )
            }

        val result = execute(command, listOf("conv-001", "Hello"))

        assertEquals(11, result.exitCode)
        assertEquals("you must be logged in to send messages", result.stderr.trim())
    }

    @Test
    fun `send command returns failure when service fails with server error`() {
        val command =
            MessageSendCommand {
                StubMessageService(
                    sendMessageResult =
                        SendMessageResult.Failure(
                            message = "server error while sending message",
                            exitCode = 13,
                        ),
                )
            }

        val result = execute(command, listOf("conv-001", "Hello"))

        assertEquals(13, result.exitCode)
        assertEquals("server error while sending message", result.stderr.trim())
    }

    @Test
    fun `send command with multi-word message succeeds`() {
        val command =
            MessageSendCommand {
                StubMessageService(sendMessageResult = SendMessageResult.Success)
            }

        val result = execute(command, listOf("conv-001", "This is a longer message with multiple words"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.trim().contains("Message sent"))
    }

    @Test
    fun `send command trims trailing carriage return from stdin message`() {
        // Simulate reading from stdin: "message\r"
        val messageWithCR = "Hello\r"
        val trimmed = messageWithCR.trimEnd('\r')

        assertEquals("Hello", trimmed)
    }

    @Test
    fun `send command preserves leading and internal whitespace when trimming only CR`() {
        // Simulate reading from stdin: "  message with spaces  \r"
        val messageWithCR = "  message with spaces  "
        val trimmed = messageWithCR.trimEnd('\r')

        assertEquals("  message with spaces  ", trimmed)
    }

    @Test
    fun `send command validates conversation ID that looks like blank but has spaces`() {
        val command =
            MessageSendCommand {
                StubMessageService(sendMessageResult = SendMessageResult.Success)
            }

        val result = execute(command, listOf("   ", "Hello"))

        assertEquals(14, result.exitCode)
        assertEquals("validation error: conversation required", result.stderr.trim())
    }

    // ==================== Helper Classes ====================

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: MessageSendCommand,
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

    private class StubMessageService(
        private val sendMessageResult: SendMessageResult = SendMessageResult.Success,
    ) : MessageService {
        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult {
            // No validation - just return configured result
            return sendMessageResult
        }

        override fun fetchMessages(conversationId: String): wirecli.message.FetchMessagesResult {
            return wirecli.message.FetchMessagesResult.Success(
                wirecli.message.FetchMessagesView(
                    conversationId = conversationId,
                    messages = emptyList(),
                ),
            )
        }
    }
}

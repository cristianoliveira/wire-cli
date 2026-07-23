package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wirecli.message.FetchMessagesResult
import wirecli.message.FetchMessagesView
import wirecli.message.MessageService
import wirecli.message.SearchMessagesResult
import wirecli.message.SendMessageResult
import wirecli.message.SetMessageReadResult
import wirecli.message.ToggleReactionResult
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageSetCommandTest {
    @Test
    fun `set read marks requested message and prints confirmation`() {
        val calls = mutableListOf<Pair<String, String>>()
        val command = MessageSetCommand { FakeMessageService(calls = calls) }

        val result = execute(command, listOf("conv-1", "--read", "msg-1"))

        assertEquals(0, result.exitCode)
        assertEquals("Message marked as read.\n", result.stdout)
        assertEquals(listOf("conv-1" to "msg-1"), calls)
    }

    @Test
    fun `set read prints applied result as JSON`() {
        val command = MessageSetCommand { FakeMessageService() }

        val result = execute(command, listOf("conv-1", "--read", "msg-1", "--json"))

        assertEquals(0, result.exitCode)
        assertEquals("", result.stderr)
        val json = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals("conv-1", json["conversationId"]?.jsonPrimitive?.content)
        assertEquals("msg-1", json["messageId"]?.jsonPrimitive?.content)
        assertEquals("read", json["state"]?.jsonPrimitive?.content)
        assertEquals("applied", json["outcome"]?.jsonPrimitive?.content)
    }

    @Test
    fun `set read prints no-op result as JSON`() {
        val command =
            MessageSetCommand {
                FakeMessageService(result = SetMessageReadResult.AlreadyRead)
            }

        val result = execute(command, listOf("conv-1", "--read", "msg-1", "--json"))

        assertEquals(0, result.exitCode)
        assertEquals("", result.stderr)
        assertEquals(
            "{\"conversationId\":\"conv-1\",\"messageId\":\"msg-1\",\"state\":\"read\",\"outcome\":\"already_read\"}\n",
            result.stdout,
        )
    }

    @Test
    fun `set read emits structured network errors only to stdout`() {
        val command =
            MessageSetCommand {
                FakeMessageService(
                    result = SetMessageReadResult.Failure("network unavailable", 12),
                )
            }

        val result = execute(command, listOf("conv-1", "--read", "msg-1", "--json"))

        assertEquals(1, result.exitCode)
        assertEquals("", result.stderr)
        assertEquals(
            "{\"error\":{\"code\":\"network_error\",\"message\":\"network unavailable\",\"retryable\":true," +
                "\"next\":\"wire message set conv-1 --read msg-1 --json\"}}\n",
            result.stdout,
        )
    }

    @Test
    fun `set read emits structured validation errors only to stdout`() {
        val command = MessageSetCommand { FakeMessageService() }

        val result = execute(command, listOf("   ", "--read", "msg-1", "--json"))

        assertEquals(2, result.exitCode)
        assertEquals("", result.stderr)
        assertEquals(
            "{\"error\":{\"code\":\"validation_error\",\"message\":\"conversation-id required\"," +
                "\"retryable\":false}}\n",
            result.stdout,
        )
    }

    @Test
    fun `set read rejects blank conversation id`() {
        val command = MessageSetCommand { FakeMessageService() }

        val result = execute(command, listOf("   ", "--read", "msg-1"))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: conversation-id required", result.stderr.trim())
    }

    @Test
    fun `set read rejects blank message id`() {
        val command = MessageSetCommand { FakeMessageService() }

        val result = execute(command, listOf("conv-1", "--read", "   "))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: message-id required", result.stderr.trim())
    }

    @Test
    fun `set read maps service failure to stderr`() {
        val command =
            MessageSetCommand {
                FakeMessageService(
                    result = SetMessageReadResult.Failure("message not found", 13),
                )
            }

        val result = execute(command, listOf("conv-1", "--read", "missing"))

        assertEquals(1, result.exitCode)
        assertEquals("message not found", result.stderr.trim())
    }

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: MessageSetCommand,
        args: List<String>,
    ): ExecutionResult {
        val stdout = java.io.ByteArrayOutputStream()
        val stderr = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        var exitCode = 0

        try {
            System.setOut(java.io.PrintStream(stdout))
            System.setErr(java.io.PrintStream(stderr))
            command.parse(args)
        } catch (result: ProgramResult) {
            exitCode = result.statusCode
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        return ExecutionResult(
            exitCode = exitCode,
            stdout = stdout.toString(Charsets.UTF_8),
            stderr = stderr.toString(Charsets.UTF_8),
        )
    }

    private class FakeMessageService(
        private val result: SetMessageReadResult = SetMessageReadResult.Success,
        private val calls: MutableList<Pair<String, String>> = mutableListOf(),
    ) : MessageService {
        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult = SendMessageResult.Success

        override fun fetchMessages(
            conversationId: String,
            limit: Int,
        ): FetchMessagesResult = FetchMessagesResult.Success(FetchMessagesView(conversationId, emptyList()))

        override fun searchMessages(
            query: String,
            conversationId: String?,
            limit: Int,
        ): SearchMessagesResult = SearchMessagesResult.Success(emptyList())

        override fun toggleReaction(
            conversationId: String,
            messageId: String,
            emoji: String,
        ): ToggleReactionResult = ToggleReactionResult.Success(wirecli.message.ReactionAction.ADDED)

        override fun setMessageRead(
            conversationId: String,
            messageId: String,
        ): SetMessageReadResult {
            calls += conversationId to messageId
            return result
        }
    }
}

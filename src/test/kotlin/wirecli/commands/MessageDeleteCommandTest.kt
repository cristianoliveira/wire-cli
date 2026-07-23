package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wirecli.message.DeleteMessageResult
import wirecli.message.DeleteScope
import wirecli.message.FetchMessagesResult
import wirecli.message.FetchMessagesView
import wirecli.message.MessageService
import wirecli.message.SearchMessagesResult
import wirecli.message.SendMessageResult
import wirecli.message.ToggleReactionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageDeleteCommandTest {
    @Test
    fun `delete command prints 'Message deleted' on default scope`() {
        val command =
            MessageDeleteCommand {
                FakeDeleteService(DeleteMessageResult.Success(DeleteScope.FOR_ME))
            }

        val result = execute(command, listOf("conv-1", "msg-1"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Message deleted."))
    }

    @Test
    fun `delete command prints 'for everyone' when --for-everyone is set`() {
        val command =
            MessageDeleteCommand {
                FakeDeleteService(DeleteMessageResult.Success(DeleteScope.FOR_EVERYONE))
            }

        val result = execute(command, listOf("conv-1", "msg-1", "--for-everyone"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Message deleted for everyone."))
    }

    @Test
    fun `delete command with --json outputs scope for_me by default`() {
        val command =
            MessageDeleteCommand {
                FakeDeleteService(DeleteMessageResult.Success(DeleteScope.FOR_ME))
            }

        val result = execute(command, listOf("conv-1", "msg-1", "--json"))

        assertEquals(0, result.exitCode)
        val obj = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals("for_me", obj.getValue("scope").jsonPrimitive.content)
    }

    @Test
    fun `delete command with --json and --for-everyone outputs scope for_everyone`() {
        val command =
            MessageDeleteCommand {
                FakeDeleteService(DeleteMessageResult.Success(DeleteScope.FOR_EVERYONE))
            }

        val result = execute(command, listOf("conv-1", "msg-1", "--for-everyone", "--json"))

        assertEquals(0, result.exitCode)
        val obj = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals("for_everyone", obj.getValue("scope").jsonPrimitive.content)
    }

    @Test
    fun `delete command validates blank conversation-id`() {
        val command =
            MessageDeleteCommand {
                FakeDeleteService(DeleteMessageResult.Success(DeleteScope.FOR_ME))
            }

        val result = execute(command, listOf("   ", "msg-1"))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: conversation-id required", result.stderr.trim())
    }

    @Test
    fun `delete command validates blank message-id`() {
        val command =
            MessageDeleteCommand {
                FakeDeleteService(DeleteMessageResult.Success(DeleteScope.FOR_ME))
            }

        val result = execute(command, listOf("conv-1", "   "))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: message-id required", result.stderr.trim())
    }

    @Test
    fun `delete command maps service failure to exit code and stderr`() {
        val command =
            MessageDeleteCommand {
                FakeDeleteService(
                    DeleteMessageResult.Failure(
                        message = "network error while deleting message",
                        exitCode = 12,
                    ),
                )
            }

        val result = execute(command, listOf("conv-1", "msg-1"))

        assertEquals(1, result.exitCode)
        assertEquals("network error while deleting message", result.stderr.trim())
    }

    @Test
    fun `delete command passes for-everyone flag as FOR_EVERYONE scope`() {
        val captured = mutableListOf<Triple<String, String, DeleteScope>>()
        val command =
            MessageDeleteCommand {
                object : MessageService {
                    override fun sendMessage(
                        conversationId: String,
                        text: String,
                    ): SendMessageResult = SendMessageResult.Success

                    override fun fetchMessages(
                        conversationId: String,
                        limit: Int,
                    ) = FetchMessagesResult.Success(FetchMessagesView(conversationId, emptyList()))

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

                    override fun deleteMessage(
                        conversationId: String,
                        messageId: String,
                        scope: DeleteScope,
                    ): DeleteMessageResult {
                        captured.add(Triple(conversationId, messageId, scope))
                        return DeleteMessageResult.Success(scope)
                    }
                }
            }

        val result = execute(command, listOf("conv-abc", "msg-xyz", "--for-everyone"))

        assertEquals(0, result.exitCode)
        assertEquals(1, captured.size)
        assertEquals("conv-abc", captured[0].first)
        assertEquals("msg-xyz", captured[0].second)
        assertEquals(DeleteScope.FOR_EVERYONE, captured[0].third)
    }

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: MessageDeleteCommand,
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

    private class FakeDeleteService(
        private val deleteResult: DeleteMessageResult,
    ) : MessageService {
        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult = SendMessageResult.Success

        override fun fetchMessages(
            conversationId: String,
            limit: Int,
        ) = FetchMessagesResult.Success(FetchMessagesView(conversationId, emptyList()))

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

        override fun deleteMessage(
            conversationId: String,
            messageId: String,
            scope: DeleteScope,
        ): DeleteMessageResult = deleteResult
    }
}

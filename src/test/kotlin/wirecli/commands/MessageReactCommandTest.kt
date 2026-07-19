package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wirecli.message.FetchMessagesResult
import wirecli.message.FetchMessagesView
import wirecli.message.MessageService
import wirecli.message.ReactionAction
import wirecli.message.SearchMessagesResult
import wirecli.message.SendMessageResult
import wirecli.message.ToggleReactionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageReactCommandTest {
    @Test
    fun `react command prints added on toggle add`() {
        val command =
            MessageReactCommand {
                FakeReactService(
                    reactionResult = ToggleReactionResult.Success(ReactionAction.ADDED),
                )
            }

        val result = execute(command, listOf("conv-1", "msg-1", "👍"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Reaction 👍 added."))
    }

    @Test
    fun `react command prints removed on toggle remove`() {
        val command =
            MessageReactCommand {
                FakeReactService(
                    reactionResult = ToggleReactionResult.Success(ReactionAction.REMOVED),
                )
            }

        val result = execute(command, listOf("conv-1", "msg-1", "❤️"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Reaction ❤️ removed."))
    }

    @Test
    fun `react command with --json outputs JSON on added`() {
        val command =
            MessageReactCommand {
                FakeReactService(
                    reactionResult = ToggleReactionResult.Success(ReactionAction.ADDED),
                )
            }

        val result = execute(command, listOf("conv-1", "msg-1", "👍", "--json"))

        assertEquals(0, result.exitCode)
        val obj = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals("added", obj.getValue("action").jsonPrimitive.content)
        assertEquals("👍", obj.getValue("emoji").jsonPrimitive.content)
    }

    @Test
    fun `react command with --json outputs JSON on removed`() {
        val command =
            MessageReactCommand {
                FakeReactService(
                    reactionResult = ToggleReactionResult.Success(ReactionAction.REMOVED),
                )
            }

        val result = execute(command, listOf("conv-1", "msg-1", "❤️", "--json"))

        assertEquals(0, result.exitCode)
        val obj = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals("removed", obj.getValue("action").jsonPrimitive.content)
        assertEquals("❤️", obj.getValue("emoji").jsonPrimitive.content)
    }

    @Test
    fun `react command validates blank conversation-id`() {
        val command =
            MessageReactCommand {
                FakeReactService(
                    reactionResult = ToggleReactionResult.Success(ReactionAction.ADDED),
                )
            }

        val result = execute(command, listOf("   ", "msg-1", "👍"))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: conversation-id required", result.stderr.trim())
    }

    @Test
    fun `react command validates blank message-id`() {
        val command =
            MessageReactCommand {
                FakeReactService(
                    reactionResult = ToggleReactionResult.Success(ReactionAction.ADDED),
                )
            }

        val result = execute(command, listOf("conv-1", "   ", "👍"))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: message-id required", result.stderr.trim())
    }

    @Test
    fun `react command validates blank emoji`() {
        val command =
            MessageReactCommand {
                FakeReactService(
                    reactionResult = ToggleReactionResult.Success(ReactionAction.ADDED),
                )
            }

        val result = execute(command, listOf("conv-1", "msg-1", "   "))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: emoji cannot be blank", result.stderr.trim())
    }

    @Test
    fun `react command maps service failure to exit code and stderr`() {
        val command =
            MessageReactCommand {
                FakeReactService(
                    reactionResult =
                        ToggleReactionResult.Failure(
                            message = "network error while toggling reaction",
                            exitCode = 12,
                        ),
                )
            }

        val result = execute(command, listOf("conv-1", "msg-1", "👍"))

        assertEquals(1, result.exitCode)
        assertEquals("network error while toggling reaction", result.stderr.trim())
    }

    @Test
    fun `react command passes all arguments to service`() {
        val captured = mutableListOf<Triple<String, String, String>>()
        val command =
            MessageReactCommand {
                object : MessageService {
                    override fun sendMessage(
                        conversationId: String,
                        text: String,
                    ): SendMessageResult = SendMessageResult.Success

                    override fun fetchMessages(conversationId: String) =
                        FetchMessagesResult.Success(
                            FetchMessagesView(conversationId, emptyList()),
                        )

                    override fun searchMessages(
                        query: String,
                        conversationId: String?,
                        limit: Int,
                    ): SearchMessagesResult = SearchMessagesResult.Success(emptyList())

                    override fun toggleReaction(
                        conversationId: String,
                        messageId: String,
                        emoji: String,
                    ): ToggleReactionResult {
                        captured.add(Triple(conversationId, messageId, emoji))
                        return ToggleReactionResult.Success(ReactionAction.ADDED)
                    }
                }
            }

        val result = execute(command, listOf("conv-abc", "msg-xyz", "🔥"))

        assertEquals(0, result.exitCode)
        assertEquals(1, captured.size)
        assertEquals("conv-abc", captured[0].first)
        assertEquals("msg-xyz", captured[0].second)
        assertEquals("🔥", captured[0].third)
    }

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: MessageReactCommand,
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

    private class FakeReactService(
        private val reactionResult: ToggleReactionResult,
    ) : MessageService {
        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult = SendMessageResult.Success

        override fun fetchMessages(conversationId: String) =
            FetchMessagesResult.Success(
                FetchMessagesView(conversationId, emptyList()),
            )

        override fun searchMessages(
            query: String,
            conversationId: String?,
            limit: Int,
        ): SearchMessagesResult = SearchMessagesResult.Success(emptyList())

        override fun toggleReaction(
            conversationId: String,
            messageId: String,
            emoji: String,
        ): ToggleReactionResult = reactionResult
    }
}

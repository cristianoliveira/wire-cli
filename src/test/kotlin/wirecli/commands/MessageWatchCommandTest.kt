package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wirecli.message.ConversationMessage
import wirecli.message.FetchMessagesResult
import wirecli.message.FetchMessagesView
import wirecli.message.MessageService
import wirecli.message.MessageUserMessages
import wirecli.message.ReactionAction
import wirecli.message.SearchMessagesResult
import wirecli.message.SendMessageResult
import wirecli.message.ToggleReactionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageWatchCommandTest {
    @Test
    fun `watch command streams new messages from reactive flow`() {
        val existing = message("msg-1", "existing")
        val incoming = message("msg-2", "incoming")
        val incomingSecond = message("msg-3", "incoming-2")

        val service =
            ReactiveMessageService(
                flowOf(
                    success(existing),
                    success(existing, incoming),
                    success(existing, incoming),
                    success(existing, incoming, incomingSecond),
                ),
            )
        val command = MessageWatchCommand(messageServiceProvider = { service })

        val result = execute(command, listOf("conv-123"))

        assertEquals(0, result.exitCode)
        assertEquals("incoming\nincoming-2", result.stdout.trim())
        assertEquals(0, service.fetchCalls)
    }

    @Test
    fun `watch command with json format streams new messages as JSON lines`() {
        val existing = message("msg-1", "existing")
        val incoming = message("msg-2", "hello\r\n\t\\\" world")

        val service =
            ReactiveMessageService(
                flowOf(
                    success(existing),
                    success(existing, incoming),
                ),
            )
        val command = MessageWatchCommand(messageServiceProvider = { service })

        val result = execute(command, listOf("conv-123", "--format", "json"))

        assertEquals(0, result.exitCode)
        val json = Json.parseToJsonElement(result.stdout.trim()).jsonObject
        assertEquals("conv-123", json.getValue("conversationId").jsonPrimitive.content)
        assertEquals("msg-2", json.getValue("messageId").jsonPrimitive.content)
        assertEquals("sender@example.com", json.getValue("senderId").jsonPrimitive.content)
        assertEquals("Sender", json.getValue("senderName").jsonPrimitive.content)
        assertEquals("2026-03-20T10:00:00Z", json.getValue("timestamp").jsonPrimitive.content)
        assertEquals("hello\r\n\t\\\" world", json.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `watch command rejects unsupported format`() {
        val command = MessageWatchCommand(messageServiceProvider = { ReactiveMessageService(flowOf(success())) })

        val result = execute(command, listOf("conv-123", "--format", "xml"))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: format must be one of: text, json", result.stderr.trim())
    }

    @Test
    fun `watch command keeps collecting reactive flow after transient failure`() {
        val incoming = message("msg-2", "incoming")

        val command =
            MessageWatchCommand(
                messageServiceProvider = {
                    ReactiveMessageService(
                        flowOf(
                            success(),
                            FetchMessagesResult.Failure(
                                message = "network error while observing messages",
                                exitCode = 12,
                            ),
                            success(incoming),
                        ),
                    )
                },
            )

        val result = execute(command, listOf("conv-123"))

        assertEquals(0, result.exitCode)
        assertEquals("incoming", result.stdout.trim())
        assertTrue(result.stderr.contains("network error while observing messages"))
    }

    @Test
    fun `watch command keeps fatal behavior for non-retryable errors`() {
        val command =
            MessageWatchCommand(
                messageServiceProvider = {
                    ReactiveMessageService(
                        flowOf(
                            FetchMessagesResult.Failure(
                                message = MessageUserMessages.CONVERSATION_NOT_FOUND,
                                exitCode = 13,
                            ),
                        ),
                    )
                },
            )

        val result = execute(command, listOf("conv-123"))

        assertEquals(1, result.exitCode)
        assertEquals(MessageUserMessages.CONVERSATION_NOT_FOUND, result.stderr.trim())
    }

    @Test
    fun `watch command validates blank conversation id`() {
        val command =
            MessageWatchCommand(
                messageServiceProvider = {
                    ReactiveMessageService(flowOf(success()))
                },
            )

        val result = execute(command, listOf("   "))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: conversation required", result.stderr.trim())
    }

    private fun message(
        id: String,
        content: String,
    ) = ConversationMessage(
        id = id,
        senderId = "sender@example.com",
        senderName = "Sender",
        timestamp = "2026-03-20T10:00:00Z",
        content = content,
    )

    private fun success(vararg messages: ConversationMessage) =
        FetchMessagesResult.Success(FetchMessagesView("conv-123", messages.toList()))

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: MessageWatchCommand,
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

    private class ReactiveMessageService(
        private val results: Flow<FetchMessagesResult>,
    ) : MessageService {
        var fetchCalls = 0
            private set

        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult = SendMessageResult.Success

        override fun fetchMessages(conversationId: String): FetchMessagesResult {
            fetchCalls += 1
            return FetchMessagesResult.Success(FetchMessagesView(conversationId, emptyList()))
        }

        override fun observeMessages(conversationId: String): Flow<FetchMessagesResult> = results

        override fun searchMessages(
            query: String,
            conversationId: String?,
            limit: Int,
        ): SearchMessagesResult = SearchMessagesResult.Success(emptyList())

        override fun toggleReaction(
            conversationId: String,
            messageId: String,
            emoji: String,
        ): ToggleReactionResult = ToggleReactionResult.Success(ReactionAction.ADDED)
    }
}

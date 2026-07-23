package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wirecli.message.ConversationMessage
import wirecli.message.FetchMessagesResult
import wirecli.message.FetchMessagesView
import wirecli.message.MessageService
import wirecli.message.ReactionAction
import wirecli.message.SearchMessagesResult
import wirecli.message.SendMessageResult
import wirecli.message.ToggleReactionResult
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageFetchCommandTest {
    @Test
    fun `fetch command prints formatted messages on success`() {
        val command =
            MessageFetchCommand {
                FakeMessageService(
                    fetchResult =
                        FetchMessagesResult.Success(
                            FetchMessagesView(
                                conversationId = "conv-123",
                                messages =
                                    listOf(
                                        ConversationMessage(
                                            id = "msg-1",
                                            senderId = "alice@example.com",
                                            senderName = "Alice",
                                            timestamp = "2026-03-20T10:00:00Z",
                                            content = "hello",
                                        ),
                                    ),
                            ),
                        ),
                )
            }

        val result = execute(command, listOf("conv-123"))

        assertEquals(0, result.exitCode)
        assertEquals("[2026-03-20T10:00:00Z] Alice: hello", result.stdout.trim())
    }

    @Test
    fun `fetch command outputs structured messages as json`() {
        val command =
            MessageFetchCommand {
                FakeMessageService(
                    fetchResult =
                        FetchMessagesResult.Success(
                            FetchMessagesView(
                                conversationId = "conv-123",
                                messages =
                                    listOf(
                                        ConversationMessage(
                                            id = "msg-1",
                                            senderId = "alice@example.com",
                                            senderName = "Alice",
                                            timestamp = "2026-03-20T10:00:00Z",
                                            content = "hello\nworld",
                                        ),
                                    ),
                            ),
                        ),
                )
            }

        val result = execute(command, listOf("--json", "conv-123"))

        assertEquals(0, result.exitCode)
        val envelope = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals("conv-123", envelope.getValue("conversationId").jsonPrimitive.content)
        assertEquals(1, envelope.getValue("returned").jsonPrimitive.int)
        assertEquals(false, envelope.getValue("truncated").jsonPrimitive.boolean)
        val message = envelope.getValue("items").jsonArray.single().jsonObject
        assertEquals("msg-1", message.getValue("messageId").jsonPrimitive.content)
        assertEquals("hello\nworld", message.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `fetch command limits output to latest messages`() {
        val messages =
            (1..3).map { index ->
                ConversationMessage(
                    id = "msg-$index",
                    senderId = "alice@example.com",
                    senderName = "Alice",
                    timestamp = "2026-03-20T10:0$index:00Z",
                    content = "message $index",
                )
            }
        val service = FakeMessageService(FetchMessagesResult.Success(FetchMessagesView("conv-123", messages)))
        val command = MessageFetchCommand { service }

        val result = execute(command, listOf("--limit", "2", "--json", "conv-123"))

        assertEquals(0, result.exitCode)
        assertEquals(3, service.fetchLimit, "command fetches one extra message to detect truncation")
        val envelope = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(2, envelope.getValue("returned").jsonPrimitive.int)
        assertEquals(true, envelope.getValue("truncated").jsonPrimitive.boolean)
        assertEquals(
            listOf("msg-2", "msg-3"),
            envelope.getValue("items").jsonArray.map { it.jsonObject.getValue("messageId").jsonPrimitive.content },
        )
    }

    @Test
    fun `fetch command rejects non-positive limit`() {
        val command =
            MessageFetchCommand {
                FakeMessageService(FetchMessagesResult.Success(FetchMessagesView("conv-123", emptyList())))
            }

        val result = execute(command, listOf("--limit", "0", "conv-123"))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: limit must be between 1 and 100", result.stderr.trim())
    }

    @Test
    fun `no-cache flag bypasses daemon cache`() {
        val service =
            FakeMessageService(
                fetchResult = FetchMessagesResult.Success(FetchMessagesView("conv-123", emptyList())),
            )
        val command = MessageFetchCommand { service }

        val result = execute(command, listOf("--no-cache", "conv-123"))

        assertEquals(0, result.exitCode)
        assertEquals("conv-123", service.serverConversationId)
    }

    @Test
    fun `fetch command maps service failure to exit code and stderr`() {
        val command =
            MessageFetchCommand {
                FakeMessageService(
                    fetchResult =
                        FetchMessagesResult.Failure(
                            message = "network error while fetching messages",
                            exitCode = 12,
                        ),
                )
            }

        val result = execute(command, listOf("conv-123"))

        assertEquals(1, result.exitCode)
        assertEquals("network error while fetching messages", result.stderr.trim())
    }

    @Test
    fun `fetch command validates blank conversation id`() {
        val command =
            MessageFetchCommand {
                FakeMessageService(fetchResult = FetchMessagesResult.Success(FetchMessagesView("conv", emptyList())))
            }

        val result = execute(command, listOf("   "))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: conversation required", result.stderr.trim())
    }

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: MessageFetchCommand,
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

    private class FakeMessageService(
        private val fetchResult: FetchMessagesResult,
    ) : MessageService {
        var serverConversationId: String? = null
            private set
        var fetchLimit: Int? = null
            private set

        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult {
            return SendMessageResult.Success
        }

        override fun fetchMessages(
            conversationId: String,
            limit: Int,
        ): FetchMessagesResult {
            fetchLimit = limit
            return fetchResult
        }

        override fun fetchServerMessages(
            conversationId: String,
            limit: Int,
        ): FetchMessagesResult {
            serverConversationId = conversationId
            fetchLimit = limit
            return fetchResult
        }

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

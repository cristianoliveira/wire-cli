package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wirecli.message.ListRecentMessagesResult
import wirecli.message.MessageService
import wirecli.message.ReactionAction
import wirecli.message.RecentMessageItem
import wirecli.message.RecentMessagesView
import wirecli.message.SearchMessagesResult
import wirecli.message.SendMessageResult
import wirecli.message.ToggleReactionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageListCommandTest {
    @Test
    fun `list command prints formatted messages on success`() {
        val command =
            MessageListCommand {
                FakeMessageService(
                    listResult =
                        ListRecentMessagesResult.Success(
                            RecentMessagesView(
                                messages =
                                    listOf(
                                        RecentMessageItem(
                                            conversationId = "conv-123",
                                            conversationName = "Engineering",
                                            messageId = "msg-1",
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

        val result = execute(command, emptyList())

        assertEquals(0, result.exitCode)
        assertEquals("[2026-03-20T10:00:00Z] Alice @ Engineering (conv-123): hello", result.stdout.trim())
    }

    @Test
    fun `list command passes flags to service`() {
        val service =
            FakeMessageService(
                listResult = ListRecentMessagesResult.Success(RecentMessagesView(emptyList())),
            )
        val command = MessageListCommand { service }

        val result = execute(command, listOf("--limit", "5", "--received-only", "--local"))

        assertEquals(0, result.exitCode)
        assertEquals(5, service.capturedLimit)
        assertEquals(true, service.capturedReceivedOnly)
        assertEquals(true, service.capturedLocalOnly)
    }

    @Test
    fun `list command uses default limit 10`() {
        val service =
            FakeMessageService(
                listResult = ListRecentMessagesResult.Success(RecentMessagesView(emptyList())),
            )
        val command = MessageListCommand { service }

        val result = execute(command, emptyList())

        assertEquals(0, result.exitCode)
        assertEquals(10, service.capturedLimit)
    }

    @Test
    fun `list command rejects non-positive limit`() {
        val command =
            MessageListCommand {
                FakeMessageService(
                    listResult = ListRecentMessagesResult.Success(RecentMessagesView(emptyList())),
                )
            }

        val result = execute(command, listOf("--limit", "0"))

        assertEquals(14, result.exitCode)
        assertEquals("validation error: limit must be between 1 and 100", result.stderr.trim())
    }

    @Test
    fun `list command with json outputs JSON array`() {
        val command =
            MessageListCommand {
                FakeMessageService(
                    listResult =
                        ListRecentMessagesResult.Success(
                            RecentMessagesView(
                                messages =
                                    listOf(
                                        RecentMessageItem(
                                            conversationId = "conv-123",
                                            conversationName = "Engineering",
                                            messageId = "msg-1",
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

        val result = execute(command, listOf("--json"))

        assertEquals(0, result.exitCode)
        val item = Json.parseToJsonElement(result.stdout).jsonArray.single().jsonObject
        assertEquals("conv-123", item.getValue("conversationId").jsonPrimitive.content)
        assertEquals("Engineering", item.getValue("conversationName").jsonPrimitive.content)
        assertEquals("msg-1", item.getValue("messageId").jsonPrimitive.content)
    }

    @Test
    fun `list command with json-lines outputs one object per line`() {
        val command =
            MessageListCommand {
                FakeMessageService(
                    listResult =
                        ListRecentMessagesResult.Success(
                            RecentMessagesView(
                                messages =
                                    listOf(
                                        RecentMessageItem(
                                            conversationId = "conv-123",
                                            conversationName = "Engineering",
                                            messageId = "msg-1",
                                            senderId = "alice@example.com",
                                            senderName = "Alice",
                                            timestamp = "2026-03-20T10:00:00Z",
                                            content = "hello",
                                        ),
                                        RecentMessageItem(
                                            conversationId = "conv-456",
                                            conversationName = "General",
                                            messageId = "msg-2",
                                            senderId = "bob@example.com",
                                            senderName = "Bob",
                                            timestamp = "2026-03-20T10:01:00Z",
                                            content = "world",
                                        ),
                                    ),
                            ),
                        ),
                )
            }

        val result = execute(command, listOf("--json-lines"))

        assertEquals(0, result.exitCode)
        val lines = result.stdout.trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("{"))
        assertTrue(lines[1].contains("\"conversationId\":\"conv-456\""))
    }

    @Test
    fun `list command maps service failure to exit code and stderr`() {
        val command =
            MessageListCommand {
                FakeMessageService(
                    listResult =
                        ListRecentMessagesResult.Failure(
                            message = "network error while listing recent messages",
                            exitCode = 12,
                        ),
                )
            }

        val result = execute(command, emptyList())

        assertEquals(12, result.exitCode)
        assertEquals("network error while listing recent messages", result.stderr.trim())
    }

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: MessageListCommand,
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

        return ExecutionResult(exitCode, stdoutBuffer.toString(Charsets.UTF_8), stderrBuffer.toString(Charsets.UTF_8))
    }

    private class FakeMessageService(
        private val listResult: ListRecentMessagesResult,
    ) : MessageService {
        var capturedLimit: Int? = null
            private set
        var capturedReceivedOnly: Boolean? = null
            private set
        var capturedLocalOnly: Boolean? = null
            private set

        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult = SendMessageResult.Success

        override fun fetchMessages(conversationId: String) =
            wirecli.message.FetchMessagesResult.Success(
                wirecli.message.FetchMessagesView(conversationId, emptyList()),
            )

        override fun listRecentMessages(
            limit: Int,
            receivedOnly: Boolean,
            localOnly: Boolean,
        ): ListRecentMessagesResult {
            capturedLimit = limit
            capturedReceivedOnly = receivedOnly
            capturedLocalOnly = localOnly
            return listResult
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

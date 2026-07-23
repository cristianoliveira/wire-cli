package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wirecli.message.ListRecentMessagesResult
import wirecli.message.MessageService
import wirecli.message.ReactionAction
import wirecli.message.RecentMessageItem
import wirecli.message.RecentMessagesQuery
import wirecli.message.RecentMessagesView
import wirecli.message.SearchMessagesResult
import wirecli.message.SendMessageResult
import wirecli.message.ToggleReactionResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

        val result = execute(command, listOf("--limit", "5", "--received-only", "--no-cache"))

        assertEquals(0, result.exitCode)
        assertEquals(5, service.capturedLimit)
        assertEquals(true, service.capturedReceivedOnly)
        assertEquals(true, service.capturedServerPath, "--no-cache must use the server path")
    }

    @Test
    fun `list command accepts daily summary filters`() {
        val service =
            FakeMessageService(
                listResult = ListRecentMessagesResult.Success(RecentMessagesView(emptyList())),
            )
        val command = MessageListCommand { service }

        val result =
            execute(
                command,
                listOf(
                    "--since",
                    "2026-03-20T09:30:00Z",
                    "--conversation-id",
                    "conv-123",
                    "--mentions-me",
                ),
            )

        assertEquals(0, result.exitCode)
        assertEquals(Instant.parse("2026-03-20T09:30:00Z"), service.capturedQuery?.since)
        assertEquals("conv-123", service.capturedQuery?.conversationId)
        assertEquals(true, service.capturedQuery?.mentionsMe)
    }

    @Test
    fun `list command resolves today from local start of day`() {
        val service = FakeMessageService(ListRecentMessagesResult.Success(RecentMessagesView(emptyList())))
        val clock = Clock.fixed(Instant.parse("2026-03-20T14:30:00Z"), ZoneOffset.UTC)

        val result = execute(MessageListCommand(clock) { service }, listOf("--since", "today"))

        assertEquals(0, result.exitCode)
        assertEquals(Instant.parse("2026-03-20T00:00:00Z"), service.capturedQuery?.since)
    }

    @Test
    fun `list command rejects invalid since value before calling service`() {
        val service = FakeMessageService(ListRecentMessagesResult.Success(RecentMessagesView(emptyList())))

        val result = execute(MessageListCommand { service }, listOf("--since", "yesterday-ish"))

        assertEquals(2, result.exitCode)
        assertEquals(null, service.capturedLimit)
        assertEquals(
            "validation error: since must be 'today', an ISO-8601 date, or an ISO-8601 timestamp",
            result.stderr.trim(),
        )
    }

    @Test
    fun `list command defaults to daemon-backed path without --no-cache`() {
        val service =
            FakeMessageService(
                listResult = ListRecentMessagesResult.Success(RecentMessagesView(emptyList())),
            )
        val command = MessageListCommand { service }

        execute(command, listOf("--limit", "3"))

        assertEquals(false, service.capturedServerPath, "default must use the daemon-backed path")
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

        assertEquals(2, result.exitCode)
        assertEquals("validation error: limit must be between 1 and 100", result.stderr.trim())
    }

    @Test
    fun `list command rejects conflicting structured output before calling service`() {
        val service = FakeMessageService(ListRecentMessagesResult.Success(RecentMessagesView(emptyList())))

        val result = execute(MessageListCommand { service }, listOf("--json", "--json-lines"))

        assertEquals(2, result.exitCode)
        assertEquals(null, service.capturedLimit)
        assertTrue(result.stderr.contains("use either --json or --json-lines"))
    }

    @Test
    fun `list command with json outputs stable list envelope`() {
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
        val envelope = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(1, envelope.getValue("returned").jsonPrimitive.int)
        assertEquals(JsonNull, envelope.getValue("total"))
        assertEquals(false, envelope.getValue("truncated").jsonPrimitive.boolean)
        val item = envelope.getValue("items").jsonArray.single().jsonObject
        assertEquals("conv-123", item.getValue("conversationId").jsonPrimitive.content)
        assertEquals("Engineering", item.getValue("conversationName").jsonPrimitive.content)
        assertEquals("msg-1", item.getValue("messageId").jsonPrimitive.content)
    }

    @Test
    fun `list command with json represents an empty result explicitly`() {
        val command =
            MessageListCommand {
                FakeMessageService(
                    listResult = ListRecentMessagesResult.Success(RecentMessagesView(emptyList())),
                )
            }

        val result = execute(command, listOf("--json"))

        assertEquals(0, result.exitCode)
        val envelope = Json.parseToJsonElement(result.stdout).jsonObject
        assertEquals(0, envelope.getValue("returned").jsonPrimitive.int)
        assertEquals(JsonNull, envelope.getValue("total"))
        assertEquals(false, envelope.getValue("truncated").jsonPrimitive.boolean)
        assertTrue(envelope.getValue("items").jsonArray.isEmpty())
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

        assertEquals(1, result.exitCode)
        assertEquals("network error while listing recent messages", result.stderr.trim())
    }

    @Test
    fun `list command truncates long content with size marker in human output`() {
        val longContent = "a".repeat(300)
        val command =
            MessageListCommand {
                FakeMessageService(
                    listResult =
                        ListRecentMessagesResult.Success(
                            RecentMessagesView(
                                messages =
                                    listOf(
                                        RecentMessageItem(
                                            conversationId = "conv-1",
                                            conversationName = "Test",
                                            messageId = "msg-1",
                                            senderId = "alice",
                                            senderName = "Alice",
                                            timestamp = "2026-01-01T00:00:00Z",
                                            content = longContent,
                                        ),
                                    ),
                            ),
                        ),
                )
            }

        val result = execute(command, emptyList())

        assertEquals(0, result.exitCode)
        val stdout = result.stdout
        assertTrue(stdout.contains("..."), "should show truncation marker")
        assertTrue(stdout.contains("bytes"), "should show original size")
    }

    @Test
    fun `list command with json truncates long content with size and marker`() {
        val longContent = "a".repeat(300)
        val command =
            MessageListCommand {
                FakeMessageService(
                    listResult =
                        ListRecentMessagesResult.Success(
                            RecentMessagesView(
                                messages =
                                    listOf(
                                        RecentMessageItem(
                                            conversationId = "conv-1",
                                            conversationName = "Test",
                                            messageId = "msg-1",
                                            senderId = "alice",
                                            senderName = "Alice",
                                            timestamp = "2026-01-01T00:00:00Z",
                                            content = longContent,
                                        ),
                                    ),
                            ),
                        ),
                )
            }

        val result = execute(command, listOf("--json"))

        assertEquals(0, result.exitCode)
        val envelope = Json.parseToJsonElement(result.stdout).jsonObject
        val item = envelope.getValue("items").jsonArray.single().jsonObject
        assertEquals(true, item.getValue("contentTruncated").jsonPrimitive.boolean)
        assertTrue(item.getValue("contentSize").jsonPrimitive.int > 200)
        assertTrue(item.getValue("content").jsonPrimitive.content.length < longContent.length)
    }

    @Test
    fun `list command with --json --full preserves original content`() {
        val longContent = "b".repeat(500)
        val command =
            MessageListCommand {
                FakeMessageService(
                    listResult =
                        ListRecentMessagesResult.Success(
                            RecentMessagesView(
                                messages =
                                    listOf(
                                        RecentMessageItem(
                                            conversationId = "conv-1",
                                            conversationName = "Test",
                                            messageId = "msg-1",
                                            senderId = "alice",
                                            senderName = "Alice",
                                            timestamp = "2026-01-01T00:00:00Z",
                                            content = longContent,
                                        ),
                                    ),
                            ),
                        ),
                )
            }

        val result = execute(command, listOf("--json", "--full"))

        assertEquals(0, result.exitCode)
        val envelope = Json.parseToJsonElement(result.stdout).jsonObject
        val item = envelope.getValue("items").jsonArray.single().jsonObject
        assertEquals(false, item.getValue("contentTruncated").jsonPrimitive.boolean)
        assertEquals(longContent.length, item.getValue("contentSize").jsonPrimitive.int)
        assertEquals(longContent, item.getValue("content").jsonPrimitive.content)
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
        var capturedServerPath: Boolean? = null
            private set
        var capturedQuery: RecentMessagesQuery? = null
            private set

        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult = SendMessageResult.Success

        override fun fetchMessages(
            conversationId: String,
            limit: Int,
        ) = wirecli.message.FetchMessagesResult.Success(
            wirecli.message.FetchMessagesView(conversationId, emptyList()),
        )

        override fun listRecentMessages(query: RecentMessagesQuery): ListRecentMessagesResult {
            capture(query, serverPath = false)
            return listResult
        }

        override fun listServerRecentMessages(query: RecentMessagesQuery): ListRecentMessagesResult {
            capture(query, serverPath = true)
            return listResult
        }

        private fun capture(
            query: RecentMessagesQuery,
            serverPath: Boolean,
        ) {
            capturedQuery = query
            capturedLimit = query.limit
            capturedReceivedOnly = query.receivedOnly
            capturedServerPath = serverPath
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

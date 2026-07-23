package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import wirecli.message.MessageSearchResult
import wirecli.message.MessageService
import wirecli.message.ReactionAction
import wirecli.message.SearchMessagesResult
import wirecli.message.SendMessageResult
import wirecli.message.ToggleReactionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageSearchCommandTest {
    @Test
    fun `search command prints formatted results on success`() {
        val results =
            listOf(
                MessageSearchResult(
                    conversationId = "conv-123",
                    messageId = "msg-1",
                    senderId = "alice@example.com",
                    senderName = "Alice",
                    timestamp = "2026-03-20T10:00:00Z",
                    content = "hello world",
                    matchSnippet = "hello world",
                ),
            )
        val command =
            MessageSearchCommand {
                FakeMessageService(
                    searchResult = SearchMessagesResult.Success(results),
                )
            }

        val result = execute(command, listOf("hello"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("[2026-03-20T10:00:00Z] Alice (conv-123): hello world"))
    }

    @Test
    fun `search command with --json outputs JSON array`() {
        val results =
            listOf(
                MessageSearchResult(
                    conversationId = "conv-123",
                    messageId = "msg-1",
                    senderId = "alice@example.com",
                    senderName = "Alice",
                    timestamp = "2026-03-20T10:00:00Z",
                    content = "hello world",
                    matchSnippet = "hello",
                ),
            )
        val command =
            MessageSearchCommand {
                FakeMessageService(
                    searchResult = SearchMessagesResult.Success(results),
                )
            }

        val result = execute(command, listOf("hello", "--json"))

        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("\"conversationId\":\"conv-123\""))
        assertTrue(result.stdout.contains("\"messageId\":\"msg-1\""))
        assertTrue(result.stdout.contains("\"content\":\"hello world\""))
        assertTrue(result.stdout.trim().startsWith("["))
        assertTrue(result.stdout.trim().endsWith("]"))
    }

    @Test
    fun `search command with --json escapes control characters`() {
        val results =
            listOf(
                MessageSearchResult(
                    conversationId = "conv-123",
                    messageId = "msg-1",
                    senderId = "alice@example.com",
                    senderName = "Alice\tAdmin",
                    timestamp = "2026-03-20T10:00:00Z",
                    content = "hello\r\n\t\\\" world",
                    matchSnippet = "hello\r\n\t\\\"",
                ),
            )
        val command =
            MessageSearchCommand {
                FakeMessageService(
                    searchResult = SearchMessagesResult.Success(results),
                )
            }

        val result = execute(command, listOf("hello", "--json"))

        assertEquals(0, result.exitCode)
        val item = Json.parseToJsonElement(result.stdout).jsonArray.single().jsonObject
        assertEquals("Alice\tAdmin", item.getValue("senderName").jsonPrimitive.content)
        assertEquals("hello\r\n\t\\\" world", item.getValue("content").jsonPrimitive.content)
        assertEquals("hello\r\n\t\\\"", item.getValue("matchSnippet").jsonPrimitive.content)
    }

    @Test
    fun `search command shows empty message when no results`() {
        val command =
            MessageSearchCommand {
                FakeMessageService(
                    searchResult = SearchMessagesResult.Success(emptyList()),
                )
            }

        val result = execute(command, listOf("nothing"))

        assertEquals(0, result.exitCode)
        assertEquals("No messages found for \"nothing\".", result.stdout.trim())
    }

    @Test
    fun `search command with --json outputs empty JSON array when no results`() {
        val command =
            MessageSearchCommand {
                FakeMessageService(
                    searchResult = SearchMessagesResult.Success(emptyList()),
                )
            }

        val result = execute(command, listOf("nothing", "--json"))

        assertEquals(0, result.exitCode)
        assertEquals("[]", result.stdout.trim())
    }

    @Test
    fun `search command rejects non-positive limit`() {
        val command =
            MessageSearchCommand {
                FakeMessageService(
                    searchResult = SearchMessagesResult.Success(emptyList()),
                )
            }

        val result = execute(command, listOf("hello", "--limit", "0"))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: limit must be between 1 and 100", result.stderr.trim())
    }

    @Test
    fun `search command validates blank query`() {
        val command =
            MessageSearchCommand {
                FakeMessageService(
                    searchResult = SearchMessagesResult.Success(emptyList()),
                )
            }

        val result = execute(command, listOf("   "))

        assertEquals(2, result.exitCode)
        assertEquals("validation error: search query required", result.stderr.trim())
    }

    @Test
    fun `search command maps service failure to exit code and stderr`() {
        val command =
            MessageSearchCommand {
                FakeMessageService(
                    searchResult =
                        SearchMessagesResult.Failure(
                            message = "network error while searching messages",
                            exitCode = 12,
                        ),
                )
            }

        val result = execute(command, listOf("hello"))

        assertEquals(1, result.exitCode)
        assertEquals("network error while searching messages", result.stderr.trim())
    }

    @Test
    fun `search command passes conversation-id and limit to service`() {
        val capturedParams = mutableListOf<Triple<String, String?, Int>>()
        val command =
            MessageSearchCommand {
                object : MessageService {
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

                    override fun searchMessages(
                        query: String,
                        conversationId: String?,
                        limit: Int,
                    ): SearchMessagesResult {
                        capturedParams.add(Triple(query, conversationId, limit))
                        return SearchMessagesResult.Success(emptyList())
                    }

                    override fun toggleReaction(
                        conversationId: String,
                        messageId: String,
                        emoji: String,
                    ): ToggleReactionResult = ToggleReactionResult.Success(ReactionAction.ADDED)
                }
            }

        val result = execute(command, listOf("hello", "--conversation-id", "conv-abc", "--limit", "5"))

        assertEquals(0, result.exitCode)
        assertEquals(1, capturedParams.size)
        assertEquals("hello", capturedParams[0].first)
        assertEquals("conv-abc", capturedParams[0].second)
        assertEquals(5, capturedParams[0].third)
    }

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: MessageSearchCommand,
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
        private val searchResult: SearchMessagesResult,
    ) : MessageService {
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

        override fun searchMessages(
            query: String,
            conversationId: String?,
            limit: Int,
        ): SearchMessagesResult = searchResult

        override fun toggleReaction(
            conversationId: String,
            messageId: String,
            emoji: String,
        ): ToggleReactionResult = ToggleReactionResult.Success(ReactionAction.ADDED)
    }
}

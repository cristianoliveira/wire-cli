package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.message.ConversationMessage
import wirecli.message.FetchMessagesResult
import wirecli.message.FetchMessagesView
import wirecli.message.MessageService
import wirecli.message.MessageUserMessages
import wirecli.message.SendMessageResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageWatchCommandTest {
    @Test
    fun `watch command streams new messages until stopped`() {
        val existing =
            ConversationMessage(
                id = "msg-1",
                senderId = "alice@example.com",
                senderName = "Alice",
                timestamp = "2026-03-20T10:00:00Z",
                content = "existing",
            )
        val incoming =
            ConversationMessage(
                id = "msg-2",
                senderId = "bob@example.com",
                senderName = "Bob",
                timestamp = "2026-03-20T10:01:00Z",
                content = "incoming",
            )
        val incomingSecond =
            ConversationMessage(
                id = "msg-3",
                senderId = "charlie@example.com",
                senderName = "Charlie",
                timestamp = "2026-03-20T10:02:00Z",
                content = "incoming-2",
            )

        var iterationsRemaining = 4

        val command =
            MessageWatchCommand(
                messageServiceProvider = {
                    QueueMessageService(
                        listOf(
                            FetchMessagesResult.Success(FetchMessagesView("conv-123", listOf(existing))),
                            FetchMessagesResult.Success(FetchMessagesView("conv-123", listOf(existing, incoming))),
                            FetchMessagesResult.Success(FetchMessagesView("conv-123", listOf(existing, incoming))),
                            FetchMessagesResult.Success(FetchMessagesView("conv-123", listOf(existing, incoming, incomingSecond))),
                        ),
                    )
                },
                pollIntervalMs = 0,
                sleep = {},
                keepWatching = { iterationsRemaining-- > 0 },
            )

        val result = execute(command, listOf("conv-123"))

        assertEquals(0, result.exitCode)
        assertEquals("incoming\nincoming-2", result.stdout.trim())
    }

    @Test
    fun `watch command retries transient failure and recovers with later message`() {
        val incoming =
            ConversationMessage(
                id = "msg-2",
                senderId = "bob@example.com",
                senderName = "Bob",
                timestamp = "2026-03-20T10:01:00Z",
                content = "incoming",
            )
        var iterationsRemaining = 3

        val command =
            MessageWatchCommand(
                messageServiceProvider = {
                    QueueMessageService(
                        listOf(
                            FetchMessagesResult.Success(FetchMessagesView("conv-123", emptyList())),
                            FetchMessagesResult.Failure(
                                message = "network error while fetching messages",
                                exitCode = 12,
                            ),
                            FetchMessagesResult.Success(FetchMessagesView("conv-123", listOf(incoming))),
                        ),
                    )
                },
                pollIntervalMs = 0,
                sleep = {},
                keepWatching = { iterationsRemaining-- > 0 },
            )

        val result = execute(command, listOf("conv-123"))

        assertEquals(0, result.exitCode)
        assertEquals("incoming", result.stdout.trim())
        assertTrue(result.stderr.contains("retrying in"))
    }

    @Test
    fun `watch command does not exit on first transient baseline failure`() {
        val incoming =
            ConversationMessage(
                id = "msg-2",
                senderId = "bob@example.com",
                senderName = "Bob",
                timestamp = "2026-03-20T10:01:00Z",
                content = "incoming-after-baseline-retry",
            )
        var iterationsRemaining = 4

        val command =
            MessageWatchCommand(
                messageServiceProvider = {
                    QueueMessageService(
                        listOf(
                            FetchMessagesResult.Failure(
                                message = "message-fetch preflight sync timeout",
                                exitCode = 12,
                            ),
                            FetchMessagesResult.Success(FetchMessagesView("conv-123", emptyList())),
                            FetchMessagesResult.Success(FetchMessagesView("conv-123", listOf(incoming))),
                        ),
                    )
                },
                pollIntervalMs = 0,
                sleep = {},
                keepWatching = { iterationsRemaining-- > 0 },
            )

        val result = execute(command, listOf("conv-123"))

        assertEquals(0, result.exitCode)
        assertEquals("incoming-after-baseline-retry", result.stdout.trim())
    }

    @Test
    fun `watch command keeps fatal behavior for non-retryable errors`() {
        val command =
            MessageWatchCommand(
                messageServiceProvider = {
                    QueueMessageService(
                        listOf(
                            FetchMessagesResult.Failure(
                                message = MessageUserMessages.CONVERSATION_NOT_FOUND,
                                exitCode = 13,
                            ),
                        ),
                    )
                },
                pollIntervalMs = 0,
                sleep = {},
                keepWatching = { true },
            )

        val result = execute(command, listOf("conv-123"))

        assertEquals(13, result.exitCode)
        assertEquals(MessageUserMessages.CONVERSATION_NOT_FOUND, result.stderr.trim())
    }

    @Test
    fun `watch command validates blank conversation id`() {
        val command =
            MessageWatchCommand(
                messageServiceProvider = {
                    QueueMessageService(
                        listOf(FetchMessagesResult.Success(FetchMessagesView("conv-123", emptyList()))),
                    )
                },
                pollIntervalMs = 0,
                sleep = {},
            )

        val result = execute(command, listOf("   "))

        assertEquals(14, result.exitCode)
        assertEquals("validation error: conversation required", result.stderr.trim())
    }

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

    private class QueueMessageService(
        results: List<FetchMessagesResult>,
    ) : MessageService {
        private val queue = ArrayDeque(results)

        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult = SendMessageResult.Success

        override fun fetchMessages(conversationId: String): FetchMessagesResult =
            queue.removeFirstOrNull() ?: FetchMessagesResult.Success(FetchMessagesView(conversationId, emptyList()))
    }
}

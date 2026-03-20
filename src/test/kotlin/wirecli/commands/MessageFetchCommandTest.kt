package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.message.ConversationMessage
import wirecli.message.FetchMessagesResult
import wirecli.message.FetchMessagesView
import wirecli.message.MessageService
import wirecli.message.SendMessageResult
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

        assertEquals(12, result.exitCode)
        assertEquals("network error while fetching messages", result.stderr.trim())
    }

    @Test
    fun `fetch command validates blank conversation id`() {
        val command =
            MessageFetchCommand {
                FakeMessageService(fetchResult = FetchMessagesResult.Success(FetchMessagesView("conv", emptyList())))
            }

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
        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult {
            return SendMessageResult.Success
        }

        override fun fetchMessages(conversationId: String): FetchMessagesResult = fetchResult
    }
}

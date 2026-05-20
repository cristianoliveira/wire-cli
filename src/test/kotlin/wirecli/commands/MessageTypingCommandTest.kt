package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.message.FetchMessagesResult
import wirecli.message.FetchMessagesView
import wirecli.message.MessageService
import wirecli.message.ReactionAction
import wirecli.message.SearchMessagesResult
import wirecli.message.SendMessageResult
import wirecli.message.SendTypingResult
import wirecli.message.ToggleReactionResult
import wirecli.message.TypingStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageTypingCommandTest {
    @Test
    fun `typing with while-pid sends heartbeats until process exits`() {
        val capturedStatuses = mutableListOf<TypingStatus>()
        val sleptDurationsMs = mutableListOf<Long>()
        val command =
            MessageTypingCommand(
                messageServiceProvider = {
                    FakeMessageService(
                        typingResult = SendTypingResult.Success,
                        capturedStatuses = capturedStatuses,
                    )
                },
                sleep = { sleptDurationsMs += it },
                isProcessAlive = sequenceAliveChecker(true, true, true, false),
            )

        val result = execute(command, listOf("conv-123", "--while-pid", "1234"))

        assertEquals(0, result.exitCode)
        assertEquals(
            listOf(
                TypingStatus.STARTED,
                TypingStatus.STARTED,
                TypingStatus.STARTED,
                TypingStatus.STOPPED,
            ),
            capturedStatuses,
        )
        assertEquals(listOf(3_000L, 3_000L, 3_000L), sleptDurationsMs)
    }

    @Test
    fun `typing sends stopped when pid exits quickly`() {
        val capturedStatuses = mutableListOf<TypingStatus>()
        val sleptDurationsMs = mutableListOf<Long>()
        val command =
            MessageTypingCommand(
                messageServiceProvider = {
                    FakeMessageService(
                        typingResult = SendTypingResult.Success,
                        capturedStatuses = capturedStatuses,
                    )
                },
                sleep = { sleptDurationsMs += it },
                isProcessAlive = sequenceAliveChecker(true, false),
            )

        val result = execute(command, listOf("conv-123", "--while-pid", "1234"))

        assertEquals(0, result.exitCode)
        assertEquals(listOf(TypingStatus.STARTED, TypingStatus.STOPPED), capturedStatuses)
        assertEquals(listOf(3_000L), sleptDurationsMs)
    }

    @Test
    fun `typing command validates while-pid must be alive`() {
        val command =
            MessageTypingCommand(
                messageServiceProvider = { FakeMessageService(typingResult = SendTypingResult.Success) },
                sleep = {},
                isProcessAlive = { false },
            )

        val result = execute(command, listOf("conv-123", "--while-pid", "1234"))

        assertEquals(14, result.exitCode)
        assertEquals("validation error: while-pid must reference a running process", result.stderr.trim())
    }

    @Test
    fun `typing command validates while-pid must be positive`() {
        val command =
            MessageTypingCommand(
                messageServiceProvider = { FakeMessageService(typingResult = SendTypingResult.Success) },
                sleep = {},
            )

        val result = execute(command, listOf("conv-123", "--while-pid", "-1"))

        assertEquals(14, result.exitCode)
        assertEquals("validation error: while-pid must be a positive integer", result.stderr.trim())
    }

    @Test
    fun `typing command validates blank conversation`() {
        val command =
            MessageTypingCommand(
                messageServiceProvider = { FakeMessageService(typingResult = SendTypingResult.Success) },
                sleep = {},
            )

        val result = execute(command, listOf("", "--while-pid", "1234"))

        assertEquals(14, result.exitCode)
        assertEquals("validation error: conversation required", result.stderr.trim())
    }

    @Test
    fun `typing command returns service failure exit code`() {
        val command =
            MessageTypingCommand(
                messageServiceProvider = {
                    FakeMessageService(
                        typingResult =
                            SendTypingResult.Failure(
                                message = "network error while sending typing status",
                                exitCode = 12,
                            ),
                    )
                },
                sleep = {},
                isProcessAlive = { true },
            )

        val result = execute(command, listOf("conv-123", "--while-pid", "1234"))

        assertEquals(12, result.exitCode)
        assertEquals("network error while sending typing status", result.stderr.trim())
    }

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: MessageTypingCommand,
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
        private val typingResult: SendTypingResult,
        private val capturedStatuses: MutableList<TypingStatus>? = null,
    ) : MessageService {
        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult = SendMessageResult.Success

        override fun fetchMessages(conversationId: String): FetchMessagesResult {
            return FetchMessagesResult.Success(FetchMessagesView(conversationId, emptyList()))
        }

        override fun sendTypingStatus(
            conversationId: String,
            status: TypingStatus,
        ): SendTypingResult {
            capturedStatuses?.add(status)
            return typingResult
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

    private fun sequenceAliveChecker(vararg values: Boolean): (Long) -> Boolean {
        var index = 0
        val fallback = values.lastOrNull() ?: false
        return {
            val value = if (index < values.size) values[index] else fallback
            index += 1
            value
        }
    }
}

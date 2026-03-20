package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.message.FetchMessagesResult
import wirecli.message.FetchMessagesView
import wirecli.message.MessageService
import wirecli.message.SendMessageResult
import wirecli.message.SendTypingResult
import wirecli.message.TypingStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageTypingCommandTest {
    @Test
    fun `typing started auto-stops after default delay`() {
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
            )

        val result = execute(command, listOf("conv-123"))

        assertEquals(0, result.exitCode)
        assertEquals(
            listOf(
                TypingStatus.STARTED,
                TypingStatus.STARTED,
                TypingStatus.STARTED,
                TypingStatus.STARTED,
                TypingStatus.STOPPED,
            ),
            capturedStatuses,
        )
        assertEquals(listOf(3_000L, 3_000L, 3_000L, 1_000L), sleptDurationsMs)
    }

    @Test
    fun `typing started with auto-stop disabled sends only started`() {
        val capturedStatuses = mutableListOf<TypingStatus>()
        val command =
            MessageTypingCommand(
                messageServiceProvider = {
                    FakeMessageService(
                        typingResult = SendTypingResult.Success,
                        capturedStatuses = capturedStatuses,
                    )
                },
                sleep = {},
            )

        val result = execute(command, listOf("conv-123", "--state", "started", "--auto-stop-seconds", "0"))

        assertEquals(0, result.exitCode)
        assertEquals(listOf(TypingStatus.STARTED), capturedStatuses)
    }

    @Test
    fun `typing stopped sends stopped status`() {
        val capturedStatuses = mutableListOf<TypingStatus>()
        val command =
            MessageTypingCommand(
                messageServiceProvider = {
                    FakeMessageService(
                        typingResult = SendTypingResult.Success,
                        capturedStatuses = capturedStatuses,
                    )
                },
                sleep = {},
            )

        val result = execute(command, listOf("conv-123", "--state", "stopped"))

        assertEquals(0, result.exitCode)
        assertEquals(listOf(TypingStatus.STOPPED), capturedStatuses)
    }

    @Test
    fun `typing started with short auto-stop does not send heartbeat`() {
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
            )

        val result = execute(command, listOf("conv-123", "--state", "started", "--auto-stop-seconds", "3"))

        assertEquals(0, result.exitCode)
        assertEquals(listOf(TypingStatus.STARTED, TypingStatus.STOPPED), capturedStatuses)
        assertEquals(listOf(3_000L), sleptDurationsMs)
    }

    @Test
    fun `typing started heartbeat repeats until auto-stop`() {
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
            )

        val result = execute(command, listOf("conv-123", "--state", "started", "--auto-stop-seconds", "7"))

        assertEquals(0, result.exitCode)
        assertEquals(
            listOf(TypingStatus.STARTED, TypingStatus.STARTED, TypingStatus.STARTED, TypingStatus.STOPPED),
            capturedStatuses,
        )
        assertEquals(listOf(3_000L, 3_000L, 1_000L), sleptDurationsMs)
    }

    @Test
    fun `typing command validates negative auto-stop`() {
        val command =
            MessageTypingCommand(
                messageServiceProvider = { FakeMessageService(typingResult = SendTypingResult.Success) },
                sleep = {},
            )

        val result = execute(command, listOf("conv-123", "--auto-stop-seconds", "-1"))

        assertEquals(14, result.exitCode)
        assertEquals("validation error: auto-stop-seconds must be >= 0", result.stderr.trim())
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
            )

        val result = execute(command, listOf("conv-123", "--state", "stopped"))

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
    }
}

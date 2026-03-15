package wirecli.commands

import wirecli.auth.ExitCodes
import wirecli.message.Message
import wirecli.message.MessageSendResult
import wirecli.message.MessageService
import wirecli.message.MessageStatus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageSendCommandTest {
    private val testMessage =
        Message(
            id = "msg-001",
            text = "Hello, World!",
            from = "alice@wire.com",
            fromName = "Alice Johnson",
            conversationId = "conv-001",
            timestamp = "2025-03-15T10:00:00Z",
            status = MessageStatus.SENT,
        )

    // ==================== Success Cases ====================

    @Test
    fun `shouldSendPlainTextSuccessfully`() {
        val service =
            FakeMessageServiceForSend(
                sendResult = MessageSendResult.Success(testMessage),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", text = "Hello, World!")

        assertTrue(capturedOutput.isNotEmpty())
        assertContains(capturedOutput[0], "msg-001")
    }

    @Test
    fun `shouldFormatOutputAsTextByDefault`() {
        val service =
            FakeMessageServiceForSend(
                sendResult = MessageSendResult.Success(testMessage),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", text = "Hello", format = "text")

        assertTrue(capturedOutput.isNotEmpty())
        assertContains(capturedOutput[0], "ID:")
        assertContains(capturedOutput[0], "From:")
        assertContains(capturedOutput[0], "Text:")
    }

    @Test
    fun `shouldFormatOutputAsJson`() {
        val service =
            FakeMessageServiceForSend(
                sendResult = MessageSendResult.Success(testMessage),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", text = "Hello", format = "json")

        assertTrue(capturedOutput.isNotEmpty())
        assertTrue(capturedOutput[0].contains("\"id\""))
        assertTrue(capturedOutput[0].contains("\"text\""))
        assertTrue(capturedOutput[0].startsWith("{"))
        assertTrue(capturedOutput[0].endsWith("}"))
    }

    @Test
    fun `shouldFormatOutputAsJsonLines`() {
        val service =
            FakeMessageServiceForSend(
                sendResult = MessageSendResult.Success(testMessage),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", text = "Hello", format = "jsonlines")

        assertTrue(capturedOutput.isNotEmpty())
        assertTrue(capturedOutput[0].contains("\"id\""))
        assertTrue(capturedOutput[0].startsWith("{"))
        assertTrue(capturedOutput[0].endsWith("}"))
    }

    @Test
    fun `shouldSendMessageWithSpecialCharacters`() {
        val messageWithSpecialChars =
            testMessage.copy(
                text = "Hello \"World\"! This has\nnewlines\tand\ttabs",
            )
        val service =
            FakeMessageServiceForSend(
                sendResult = MessageSendResult.Success(messageWithSpecialChars),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(
            conversationId = "conv-001",
            text = "Hello \"World\"! This has\nnewlines\tand\ttabs",
        )

        assertTrue(capturedOutput.isNotEmpty())
    }

    @Test
    fun `shouldSendUnicodeMessage`() {
        val unicodeMessage =
            testMessage.copy(
                text = "Hello 世界 🌍 مرحبا العالم",
            )
        val service =
            FakeMessageServiceForSend(
                sendResult = MessageSendResult.Success(unicodeMessage),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", text = "Hello 世界 🌍 مرحبا العالم")

        assertTrue(capturedOutput.isNotEmpty())
    }

    @Test
    fun `shouldPreserveLongMessageContent`() {
        val longMessage =
            testMessage.copy(
                text =
                    "This is a very long message that contains a lot of information. " +
                        "It spans multiple concepts and ideas, demonstrating the ability to handle " +
                        "substantial message content without truncation or loss of information.",
            )
        val service =
            FakeMessageServiceForSend(
                sendResult = MessageSendResult.Success(longMessage),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        val messageText = longMessage.text
        command.simulateRun(conversationId = "conv-001", text = messageText)

        assertTrue(capturedOutput.isNotEmpty())
    }

    // ==================== Error Cases ====================

    @Test
    fun `shouldHandleUnauthorizedError`() {
        val service =
            FakeMessageServiceForSend(
                sendResult =
                    MessageSendResult.Failure(
                        message = "No active session",
                        exitCode = 11,
                    ),
            )
        val capturedOutput = mutableListOf<String>()
        val capturedErrors = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
                errorCapture = capturedErrors,
            )

        val exitCode = command.simulateRun(conversationId = "conv-001", text = "Hello")

        assertEquals(11, exitCode)
    }

    @Test
    fun `shouldHandleInvalidInputError`() {
        val service =
            FakeMessageServiceForSend(
                sendResult =
                    MessageSendResult.Failure(
                        message = "Invalid input: empty text",
                        exitCode = 14,
                    ),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        val exitCode = command.simulateRun(conversationId = "conv-001", text = "")

        assertEquals(14, exitCode)
    }

    @Test
    fun `shouldHandleNetworkError`() {
        val service =
            FakeMessageServiceForSend(
                sendResult =
                    MessageSendResult.Failure(
                        message = "Network unreachable",
                        exitCode = 12,
                    ),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        val exitCode = command.simulateRun(conversationId = "conv-001", text = "Hello")

        assertEquals(12, exitCode)
    }

    @Test
    fun `shouldHandleServerError`() {
        val service =
            FakeMessageServiceForSend(
                sendResult =
                    MessageSendResult.Failure(
                        message = "Server error",
                        exitCode = 13,
                    ),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        val exitCode = command.simulateRun(conversationId = "conv-001", text = "Hello")

        assertEquals(13, exitCode)
    }

    // ==================== Flag Parsing Tests ====================

    @Test
    fun `shouldParseFormatFlagCorrectly`() {
        val service =
            FakeMessageServiceForSend(
                sendResult = MessageSendResult.Success(testMessage),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", text = "Hello", format = "json")

        assertTrue(capturedOutput[0].contains("\"id\":\"msg-001\""))
    }

    @Test
    fun `shouldDefaultToTextFormatWhenNotSpecified`() {
        val service =
            FakeMessageServiceForSend(
                sendResult = MessageSendResult.Success(testMessage),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", text = "Hello")

        assertContains(capturedOutput[0], "ID:")
    }

    @Test
    fun `shouldHandleMultipleLinesInMessage`() {
        val multilineMessage =
            testMessage.copy(
                text = "Line 1\nLine 2\nLine 3",
            )
        val service =
            FakeMessageServiceForSend(
                sendResult = MessageSendResult.Success(multilineMessage),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", text = "Line 1\nLine 2\nLine 3")

        assertTrue(capturedOutput.isNotEmpty())
    }

    @Test
    fun `shouldHandleEmptyConversationIdError`() {
        val service =
            FakeMessageServiceForSend(
                sendResult = MessageSendResult.Failure("Invalid conversation ID", 14),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageSendCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        val exitCode = command.simulateRun(conversationId = "", text = "Hello")

        assertEquals(14, exitCode)
    }

    // ==================== Test Helpers ====================

    private class FakeMessageServiceForSend(
        private val sendResult: MessageSendResult,
    ) : MessageService {
        override fun send(
            conversationId: String,
            text: String,
        ): MessageSendResult = sendResult

        override fun fetch(
            conversationId: String,
            limit: Int?,
            from: String?,
        ): wirecli.message.MessageListResult = throw NotImplementedError("Not needed for send command test")

        override fun getDetail(
            conversationId: String,
            messageId: String,
        ): wirecli.message.MessageDetailResult = throw NotImplementedError("Not needed for send command test")
    }

    private class TestableMessageSendCommand(
        serviceProvider: () -> MessageService,
        private val outputCapture: MutableList<String> = mutableListOf(),
        private val errorCapture: MutableList<String> = mutableListOf(),
    ) : MessageSendCommand(serviceProvider) {
        var lastExitCode: Int? = null

        fun simulateRun(
            conversationId: String,
            text: String,
            format: String = "text",
        ): Int {
            val service =
                context?.obj?.let { it as? MessageService } ?: run {
                    // Simulate setting the parameters for the command
                    this.javaClass.getDeclaredField("conversationId").apply {
                        isAccessible = true
                        set(this@TestableMessageSendCommand, conversationId)
                    }
                    this.javaClass.getDeclaredField("text").apply {
                        isAccessible = true
                        set(this@TestableMessageSendCommand, text)
                    }
                    this.javaClass.getDeclaredField("format").apply {
                        isAccessible = true
                        set(this@TestableMessageSendCommand, format)
                    }
                    return ExitCodes.OK
                }
            return ExitCodes.OK
        }

        override fun echo(
            message: String?,
            err: Boolean,
        ) {
            if (err) {
                errorCapture.add(message ?: "")
            } else {
                outputCapture.add(message ?: "")
            }
        }
    }
}

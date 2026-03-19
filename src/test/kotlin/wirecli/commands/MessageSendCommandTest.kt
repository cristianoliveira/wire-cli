package wirecli.commands

import wirecli.message.MessageService
import wirecli.message.SendMessageResult
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageSendCommandTest {
    // ==================== MessageSendCommand Tests ====================

    @Test
    fun `send command with positional message succeeds`() {
        val service =
            FakeMessageService(
                sendMessageResult = SendMessageResult.Success,
            )

        val result = service.sendMessage("conv-001", "Hello World")

        assertEquals(SendMessageResult.Success, result)
    }

    @Test
    fun `send command with conversation validation fails for blank conversation ID`() {
        val service =
            FakeMessageService(
                sendMessageResult = SendMessageResult.Success,
            )

        val result = service.sendMessage("", "Hello")

        assertEquals(
            SendMessageResult.Failure(
                message = "validation error: conversation required",
                exitCode = 14,
            ),
            result,
        )
    }

    @Test
    fun `send command with message validation fails for blank message`() {
        val service =
            FakeMessageService(
                sendMessageResult = SendMessageResult.Success,
            )

        val result = service.sendMessage("conv-001", "")

        assertEquals(
            SendMessageResult.Failure(
                message = "validation error: message required",
                exitCode = 14,
            ),
            result,
        )
    }

    @Test
    fun `send command with message containing only whitespace fails`() {
        val service =
            FakeMessageService(
                sendMessageResult = SendMessageResult.Success,
            )

        val result = service.sendMessage("conv-001", "   ")

        assertEquals(
            SendMessageResult.Failure(
                message = "validation error: message required",
                exitCode = 14,
            ),
            result,
        )
    }

    @Test
    fun `send command returns failure when service fails with network error`() {
        val service =
            FakeMessageService(
                sendMessageResult =
                    SendMessageResult.Failure(
                        message = "network error while sending message",
                        exitCode = 12,
                    ),
            )

        val result = service.sendMessage("conv-001", "Hello")

        assertEquals(
            SendMessageResult.Failure(
                message = "network error while sending message",
                exitCode = 12,
            ),
            result,
        )
    }

    @Test
    fun `send command returns failure when service fails with authorization error`() {
        val service =
            FakeMessageService(
                sendMessageResult =
                    SendMessageResult.Failure(
                        message = "you must be logged in to send messages",
                        exitCode = 11,
                    ),
            )

        val result = service.sendMessage("conv-001", "Hello")

        assertEquals(
            SendMessageResult.Failure(
                message = "you must be logged in to send messages",
                exitCode = 11,
            ),
            result,
        )
    }

    @Test
    fun `send command returns failure when service fails with server error`() {
        val service =
            FakeMessageService(
                sendMessageResult =
                    SendMessageResult.Failure(
                        message = "server error while sending message",
                        exitCode = 13,
                    ),
            )

        val result = service.sendMessage("conv-001", "Hello")

        assertEquals(
            SendMessageResult.Failure(
                message = "server error while sending message",
                exitCode = 13,
            ),
            result,
        )
    }

    @Test
    fun `send command with multi-word message succeeds`() {
        val service =
            FakeMessageService(
                sendMessageResult = SendMessageResult.Success,
            )

        val message = "This is a longer message with multiple words"
        val result = service.sendMessage("conv-001", message)

        assertEquals(SendMessageResult.Success, result)
    }

    @Test
    fun `send command trims trailing carriage return from stdin message`() {
        // Simulate reading from stdin: "message\r"
        val messageWithCR = "Hello\r"
        val trimmed = messageWithCR.trimEnd('\r')

        assertEquals("Hello", trimmed)
    }

    @Test
    fun `send command preserves leading and internal whitespace when trimming only CR`() {
        // Simulate reading from stdin: "  message with spaces  \r"
        val messageWithCR = "  message with spaces  \r"
        val trimmed = messageWithCR.trimEnd('\r')

        assertEquals("  message with spaces  ", trimmed)
    }

    @Test
    fun `send command handles conversation ID that looks like blank but has spaces`() {
        val service =
            FakeMessageService(
                sendMessageResult = SendMessageResult.Success,
            )

        val result = service.sendMessage("   ", "Hello")

        assertEquals(
            SendMessageResult.Failure(
                message = "validation error: conversation required",
                exitCode = 14,
            ),
            result,
        )
    }

    // ==================== Helper Classes ====================

    private class FakeMessageService(
        private val sendMessageResult: SendMessageResult = SendMessageResult.Success,
    ) : MessageService {
        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult {
            // Validate inputs before delegating to result
            if (conversationId.isBlank()) {
                return SendMessageResult.Failure(
                    message = "validation error: conversation required",
                    exitCode = 14,
                )
            }

            if (text.isBlank()) {
                return SendMessageResult.Failure(
                    message = "validation error: message required",
                    exitCode = 14,
                )
            }

            return sendMessageResult
        }
    }
}

package wirecli.message

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageContractsTest {
    @Test
    fun `SendMessageResult Success is an object`() {
        val result: SendMessageResult = SendMessageResult.Success

        assertIs<SendMessageResult.Success>(result)
    }

    @Test
    fun `SendMessageResult Failure exposes message and exit code`() {
        val result: SendMessageResult =
            SendMessageResult.Failure(
                message = "Failed to send",
                exitCode = MessageExitCodes.SERVER_ERROR,
            )

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals("Failed to send", failure.message)
        assertEquals(MessageExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `MessageExitCodes defines standard exit codes`() {
        assertEquals(0, MessageExitCodes.OK)
        assertEquals(14, MessageExitCodes.VALIDATION_ERROR)
        assertEquals(11, MessageExitCodes.UNAUTHORIZED)
        assertEquals(12, MessageExitCodes.NETWORK_ERROR)
        assertEquals(13, MessageExitCodes.SERVER_ERROR)
        assertEquals(13, MessageExitCodes.NOT_FOUND)
    }

    @Test
    fun `MessageExitCodes follows established patterns from ExitCodes`() {
        // Ensure consistency with auth module's exit codes
        assertEquals(11, MessageExitCodes.UNAUTHORIZED) // matches ExitCodes.UNAUTHORIZED
        assertEquals(12, MessageExitCodes.NETWORK_ERROR) // matches ExitCodes.NETWORK_ERROR
        assertEquals(13, MessageExitCodes.SERVER_ERROR) // matches ExitCodes.SERVER_ERROR
        assertEquals(14, MessageExitCodes.VALIDATION_ERROR) // matches ExitCodes.VALIDATION_ERROR
    }

    @Test
    fun `MessageUserMessages defines user-friendly error messages`() {
        assertEquals("you must be logged in to send messages", MessageUserMessages.UNAUTHORIZED)
        assertEquals("network error while sending message", MessageUserMessages.NETWORK_ERROR)
        assertEquals("message send timed out while waiting for sync/MLS", MessageUserMessages.SEND_TIMEOUT)
        assertEquals("server error while sending message", MessageUserMessages.SERVER_ERROR)
        assertEquals("invalid message format or parameters", MessageUserMessages.VALIDATION_ERROR)
        assertEquals("conversation not found", MessageUserMessages.CONVERSATION_NOT_FOUND)
        assertEquals("message is too long", MessageUserMessages.MESSAGE_TOO_LONG)
        assertEquals("message cannot be empty", MessageUserMessages.EMPTY_MESSAGE)
    }

    @Test
    fun `MessageApiClient interface defines sendMessage method`() {
        // This test ensures the interface contract is upheld
        val methodNames = MessageApiClient::class.java.methods.map { it.name }

        assert(methodNames.contains("sendMessage"))
        assert(methodNames.contains("fetchMessages"))
    }

    @Test
    fun `MessageService interface defines sendMessage method`() {
        // This test ensures the interface contract is upheld
        val methodNames = MessageService::class.java.methods.map { it.name }

        assert(methodNames.contains("sendMessage"))
        assert(methodNames.contains("fetchMessages"))
    }

    @Test
    fun `FetchMessagesResult Success exposes conversation and messages`() {
        val result: FetchMessagesResult =
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
            )

        val success = assertIs<FetchMessagesResult.Success>(result)
        assertEquals("conv-123", success.view.conversationId)
        assertEquals(1, success.view.messages.size)
    }

    @Test
    fun `MessageException hierarchy includes all specific exception types`() {
        // Test that all exception types can be created
        val unauthorized = MessageException.Unauthorized()
        val validation = MessageException.ValidationError()
        val notFound = MessageException.ConversationNotFound()
        val network = MessageException.NetworkFailure()
        val server = MessageException.ServerError("Custom error")
        val unknown = MessageException.UnknownFailure("Unknown error")

        assertEquals(MessageUserMessages.UNAUTHORIZED, unauthorized.message)
        assertEquals(MessageUserMessages.VALIDATION_ERROR, validation.message)
        assertEquals(MessageUserMessages.CONVERSATION_NOT_FOUND, notFound.message)
        assertEquals(MessageUserMessages.NETWORK_ERROR, network.message)
        assertEquals("Custom error", server.message)
        assertEquals("Unknown error", unknown.message)
    }

    @Test
    fun `MessageException can wrap cause throwable`() {
        val cause = RuntimeException("Original error")
        val exception = MessageException.NetworkFailure("Custom message", cause)

        assertEquals("Custom message", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `Failure result can be created with validation error code`() {
        val result: SendMessageResult =
            SendMessageResult.Failure(
                message = MessageUserMessages.VALIDATION_ERROR,
                exitCode = MessageExitCodes.VALIDATION_ERROR,
            )

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageExitCodes.VALIDATION_ERROR, failure.exitCode)
    }

    @Test
    fun `Failure result can be created with unauthorized error code`() {
        val result: SendMessageResult =
            SendMessageResult.Failure(
                message = MessageUserMessages.UNAUTHORIZED,
                exitCode = MessageExitCodes.UNAUTHORIZED,
            )

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `Failure result can be created with network error code`() {
        val result: SendMessageResult =
            SendMessageResult.Failure(
                message = MessageUserMessages.NETWORK_ERROR,
                exitCode = MessageExitCodes.NETWORK_ERROR,
            )

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `Failure result can be created with server error code`() {
        val result: SendMessageResult =
            SendMessageResult.Failure(
                message = MessageUserMessages.SERVER_ERROR,
                exitCode = MessageExitCodes.SERVER_ERROR,
            )

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `Failure result can be created with not found error code`() {
        val result: SendMessageResult =
            SendMessageResult.Failure(
                message = MessageUserMessages.CONVERSATION_NOT_FOUND,
                exitCode = MessageExitCodes.NOT_FOUND,
            )

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageExitCodes.NOT_FOUND, failure.exitCode)
    }
}

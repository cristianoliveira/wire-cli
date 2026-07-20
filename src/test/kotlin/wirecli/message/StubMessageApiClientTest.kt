package wirecli.message

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StubMessageApiClientTest {
    private val testSession =
        AuthSession(
            userId = "alice@example.com",
            accessToken = "token123",
            server = "https://wire.example.com",
        )

    @Test
    fun `SUCCESS mode returns Success result`() {
        val client = StubMessageApiClient(StubMode.SUCCESS)

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        assertIs<SendMessageResult.Success>(result)
    }

    @Test
    fun `UNAUTHORIZED mode returns Failure with unauthorized error code`() {
        val client = StubMessageApiClient(StubMode.UNAUTHORIZED)

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `NETWORK_ERROR mode returns Failure with network error code`() {
        val client = StubMessageApiClient(StubMode.NETWORK_ERROR)

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageUserMessages.NETWORK_ERROR, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `SERVER_ERROR mode returns Failure with server error code`() {
        val client = StubMessageApiClient(StubMode.SERVER_ERROR)

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageUserMessages.SERVER_ERROR, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `VALIDATION_ERROR mode returns Failure with validation error code`() {
        val client = StubMessageApiClient(StubMode.VALIDATION_ERROR)

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageUserMessages.VALIDATION_ERROR, failure.message)
        assertEquals(MessageExitCodes.VALIDATION_ERROR, failure.exitCode)
    }

    @Test
    fun `CONVERSATION_NOT_FOUND mode returns Failure with not found error code`() {
        val client = StubMessageApiClient(StubMode.CONVERSATION_NOT_FOUND)

        val result = client.sendMessage(testSession, "conv-invalid", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageUserMessages.CONVERSATION_NOT_FOUND, failure.message)
        assertEquals(MessageExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `setMessageRead succeeds in success mode`() {
        val client = StubMessageApiClient(StubMode.SUCCESS)

        val result = client.setMessageRead(testSession, "conv-1", "msg-1")

        assertIs<SetMessageReadResult.Success>(result)
    }

    @Test
    fun `setMessageRead reports already read in deterministic no-op mode`() {
        val client = StubMessageApiClient(mapOf("WIRE_STUB_MODE" to "already_read"))

        val result = client.setMessageRead(testSession, "conv-1", "msg-1")

        assertIs<SetMessageReadResult.AlreadyRead>(result)
    }

    @Test
    fun `setMessageRead maps network failure`() {
        val client = StubMessageApiClient(StubMode.NETWORK_ERROR)

        val result = client.setMessageRead(testSession, "conv-1", "msg-1")

        val failure = assertIs<SetMessageReadResult.Failure>(result)
        assertEquals(MessageUserMessages.SET_READ_NETWORK_ERROR, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `SUCCESS mode works with different parameters`() {
        val client = StubMessageApiClient(StubMode.SUCCESS)

        val result1 = client.sendMessage(testSession, "conv-abc", "Message 1")
        val result2 = client.sendMessage(testSession, "conv-def", "Message 2")

        assertIs<SendMessageResult.Success>(result1)
        assertIs<SendMessageResult.Success>(result2)
    }

    @Test
    fun `NETWORK_ERROR mode is consistent across calls`() {
        val client = StubMessageApiClient(StubMode.NETWORK_ERROR)

        val result1 = client.sendMessage(testSession, "conv-123", "Hello")
        val result2 = client.sendMessage(testSession, "conv-456", "World")

        val failure1 = assertIs<SendMessageResult.Failure>(result1)
        val failure2 = assertIs<SendMessageResult.Failure>(result2)

        assertEquals(failure1.message, failure2.message)
        assertEquals(failure1.exitCode, failure2.exitCode)
    }

    @Test
    fun `UNAUTHORIZED mode returns standard unauthorized message`() {
        val client = StubMessageApiClient(StubMode.UNAUTHORIZED)

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
    }

    @Test
    fun `SERVER_ERROR mode returns server error message`() {
        val client = StubMessageApiClient(StubMode.SERVER_ERROR)

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageUserMessages.SERVER_ERROR, failure.message)
    }

    @Test
    fun `VALIDATION_ERROR mode returns validation error message`() {
        val client = StubMessageApiClient(StubMode.VALIDATION_ERROR)

        val result = client.sendMessage(testSession, "conv-123", "")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageUserMessages.VALIDATION_ERROR, failure.message)
    }

    @Test
    fun `CONVERSATION_NOT_FOUND mode returns not found message`() {
        val client = StubMessageApiClient(StubMode.CONVERSATION_NOT_FOUND)

        val result = client.sendMessage(testSession, "conv-nonexistent", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageUserMessages.CONVERSATION_NOT_FOUND, failure.message)
    }

    @Test
    fun `different modes return different exit codes`() {
        val successClient = StubMessageApiClient(StubMode.SUCCESS)
        val unauthorizedClient = StubMessageApiClient(StubMode.UNAUTHORIZED)
        val networkErrorClient = StubMessageApiClient(StubMode.NETWORK_ERROR)
        val serverErrorClient = StubMessageApiClient(StubMode.SERVER_ERROR)
        val validationErrorClient = StubMessageApiClient(StubMode.VALIDATION_ERROR)
        val notFoundClient = StubMessageApiClient(StubMode.CONVERSATION_NOT_FOUND)

        val successResult = successClient.sendMessage(testSession, "conv", "msg")
        val unauthorizedResult = unauthorizedClient.sendMessage(testSession, "conv", "msg")
        val networkErrorResult = networkErrorClient.sendMessage(testSession, "conv", "msg")
        val serverErrorResult = serverErrorClient.sendMessage(testSession, "conv", "msg")
        val validationErrorResult = validationErrorClient.sendMessage(testSession, "conv", "msg")
        val notFoundResult = notFoundClient.sendMessage(testSession, "conv", "msg")

        assertIs<SendMessageResult.Success>(successResult)

        val unauthorizedFailure = assertIs<SendMessageResult.Failure>(unauthorizedResult)
        assertEquals(ExitCodes.UNAUTHORIZED, unauthorizedFailure.exitCode)

        val networkErrorFailure = assertIs<SendMessageResult.Failure>(networkErrorResult)
        assertEquals(ExitCodes.NETWORK_ERROR, networkErrorFailure.exitCode)

        val serverErrorFailure = assertIs<SendMessageResult.Failure>(serverErrorResult)
        assertEquals(ExitCodes.SERVER_ERROR, serverErrorFailure.exitCode)

        val validationErrorFailure = assertIs<SendMessageResult.Failure>(validationErrorResult)
        assertEquals(MessageExitCodes.VALIDATION_ERROR, validationErrorFailure.exitCode)

        val notFoundFailure = assertIs<SendMessageResult.Failure>(notFoundResult)
        assertEquals(MessageExitCodes.NOT_FOUND, notFoundFailure.exitCode)
    }

    @Test
    fun `fetchMessages SUCCESS mode returns deterministic messages`() {
        val client = StubMessageApiClient(StubMode.SUCCESS)

        val result = client.fetchMessages(testSession, "conv-123")

        val success = assertIs<FetchMessagesResult.Success>(result)
        assertEquals("conv-123", success.view.conversationId)
        assertEquals(2, success.view.messages.size)
        assertEquals("msg-001", success.view.messages.first().id)
    }

    @Test
    fun `fetchMessages NETWORK_ERROR mode returns fetch network message`() {
        val client = StubMessageApiClient(StubMode.NETWORK_ERROR)

        val result = client.fetchMessages(testSession, "conv-123")

        val failure = assertIs<FetchMessagesResult.Failure>(result)
        assertEquals(MessageUserMessages.FETCH_NETWORK_ERROR, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `sendTypingStatus SUCCESS mode returns success`() {
        val client = StubMessageApiClient(StubMode.SUCCESS)

        val result = client.sendTypingStatus(testSession, "conv-123", TypingStatus.STARTED)

        assertIs<SendTypingResult.Success>(result)
    }

    @Test
    fun `sendTypingStatus NETWORK_ERROR mode returns typing network message`() {
        val client = StubMessageApiClient(StubMode.NETWORK_ERROR)

        val result = client.sendTypingStatus(testSession, "conv-123", TypingStatus.STOPPED)

        val failure = assertIs<SendTypingResult.Failure>(result)
        assertEquals(MessageUserMessages.TYPING_NETWORK_ERROR, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    // --- Search stub tests ---

    @Test
    fun `search SUCCESS mode returns deterministic results with query snippet`() {
        val client = StubMessageApiClient(StubMode.SUCCESS)

        val result = client.searchMessages(testSession, "hello", null, 10)

        val success = assertIs<SearchMessagesResult.Success>(result)
        assertEquals(2, success.results.size)
        assertTrue(success.results[0].content.contains("hello"))
        assertTrue(success.results[0].matchSnippet.contains("hello"))
    }

    @Test
    fun `search UNAUTHORIZED mode returns failure`() {
        val client = StubMessageApiClient(StubMode.UNAUTHORIZED)

        val result = client.searchMessages(testSession, "hello", null, 10)

        val failure = assertIs<SearchMessagesResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `search NETWORK_ERROR mode returns search network error`() {
        val client = StubMessageApiClient(StubMode.NETWORK_ERROR)

        val result = client.searchMessages(testSession, "hello", null, 10)

        val failure = assertIs<SearchMessagesResult.Failure>(result)
        assertEquals(MessageUserMessages.SEARCH_NETWORK_ERROR, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `search SERVER_ERROR mode returns search server error`() {
        val client = StubMessageApiClient(StubMode.SERVER_ERROR)

        val result = client.searchMessages(testSession, "hello", null, 10)

        val failure = assertIs<SearchMessagesResult.Failure>(result)
        assertEquals(MessageUserMessages.SEARCH_SERVER_ERROR, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `search VALIDATION_ERROR mode returns empty query error`() {
        val client = StubMessageApiClient(StubMode.VALIDATION_ERROR)

        val result = client.searchMessages(testSession, "hello", null, 10)

        val failure = assertIs<SearchMessagesResult.Failure>(result)
        assertEquals(MessageUserMessages.SEARCH_EMPTY_QUERY, failure.message)
        assertEquals(MessageExitCodes.VALIDATION_ERROR, failure.exitCode)
    }

    @Test
    fun `search CONVERSATION_NOT_FOUND mode returns not found error`() {
        val client = StubMessageApiClient(StubMode.CONVERSATION_NOT_FOUND)

        val result = client.searchMessages(testSession, "hello", null, 10)

        val failure = assertIs<SearchMessagesResult.Failure>(result)
        assertEquals(MessageUserMessages.CONVERSATION_NOT_FOUND, failure.message)
        assertEquals(MessageExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `search passes conversation-id to results in stub mode`() {
        val client = StubMessageApiClient(StubMode.SUCCESS)

        val result = client.searchMessages(testSession, "hello", "conv-xyz", 5)

        val success = assertIs<SearchMessagesResult.Success>(result)
        assertEquals(2, success.results.size)
        assertEquals("conv-xyz", success.results[0].conversationId)
        assertEquals("conv-xyz", success.results[1].conversationId)
    }
}

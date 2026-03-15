package wirecli.message

import wirecli.auth.AuthMessages
import wirecli.auth.AuthResult
import wirecli.auth.AuthSession
import wirecli.auth.AuthSessionService
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes
import wirecli.auth.SessionInventory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionBackedMessageServiceTest {
    private val testSession =
        AuthSession(
            userId = "user-123",
            accessToken = "token-abc",
            server = "https://wire.example.com",
        )

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

    @Test
    fun `shouldSendMessageSuccessWhenSessionExists`() {
        val apiClient =
            FakeMessageApiClient(
                sendResult = MessageSendResult.Success(testMessage),
            )
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
            )

        val result = service.send("conv-001", "Hello, World!")

        assertIs<MessageSendResult.Success>(result)
        assertEquals("msg-001", result.message.id)
        assertEquals("Hello, World!", result.message.text)
    }

    @Test
    fun `shouldReturnUnauthorizedWhenNoSessionForSend`() {
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(null),
                apiClient = FakeMessageApiClient(MessageSendResult.Success(testMessage)),
            )

        val result = service.send("conv-001", "Hello")

        val failure = assertIs<MessageSendResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `shouldFetchMessagesSuccessWhenSessionExists`() {
        val messages = listOf(testMessage)
        val view = MessageListView(messages, hasMore = false)
        val apiClient =
            FakeMessageApiClient(
                fetchResult = MessageListResult.Success(view),
            )
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
            )

        val result = service.fetch("conv-001")

        val success = assertIs<MessageListResult.Success>(result)
        assertEquals(1, success.view.messages.size)
        assertEquals("msg-001", success.view.messages[0].id)
    }

    @Test
    fun `shouldReturnUnauthorizedWhenNoSessionForFetch`() {
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(null),
                apiClient = FakeMessageApiClient(MessageListResult.Success(MessageListView(emptyList()))),
            )

        val result = service.fetch("conv-001")

        val failure = assertIs<MessageListResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `shouldFetchWithLimitParameter`() {
        val messages = listOf(testMessage)
        val view = MessageListView(messages, hasMore = true, nextCursor = "msg-002")
        val apiClient =
            FakeMessageApiClient(
                fetchResult = MessageListResult.Success(view),
            )
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
            )

        val result = service.fetch("conv-001", limit = 25)

        val success = assertIs<MessageListResult.Success>(result)
        assertTrue(success.view.hasMore)
        assertEquals("msg-002", success.view.nextCursor)
    }

    @Test
    fun `shouldFetchWithFromParameter`() {
        val messages = listOf(testMessage)
        val view = MessageListView(messages, hasMore = false)
        val apiClient =
            FakeMessageApiClient(
                fetchResult = MessageListResult.Success(view),
            )
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
            )

        val result = service.fetch("conv-001", from = "msg-000")

        assertIs<MessageListResult.Success>(result)
    }

    @Test
    fun `shouldGetDetailSuccessWhenSessionExists`() {
        val detailView = MessageDetailView(testMessage)
        val apiClient =
            FakeMessageApiClient(
                detailResult = MessageDetailResult.Success(detailView),
            )
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
            )

        val result = service.getDetail("conv-001", "msg-001")

        val success = assertIs<MessageDetailResult.Success>(result)
        assertEquals("msg-001", success.view.message.id)
    }

    @Test
    fun `shouldReturnUnauthorizedWhenNoSessionForDetail`() {
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(null),
                apiClient = FakeMessageApiClient(MessageDetailResult.Success(MessageDetailView(testMessage))),
            )

        val result = service.getDetail("conv-001", "msg-001")

        val failure = assertIs<MessageDetailResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    // Fake implementations for testing
    private class FakeSessionStore(private val activeSession: AuthSession?) : AuthSessionStore {
        override fun readActiveSession(): AuthSession? = activeSession

        override fun readSessionInventory(): SessionInventory =
            SessionInventory(
                activeSession = activeSession,
                validSessions = if (activeSession == null) 0 else 1,
                invalidSessions = 0,
            )
    }

    private class FakeMessageApiClient(
        private val sendResult: MessageSendResult? = null,
        private val fetchResult: MessageListResult? = null,
        private val detailResult: MessageDetailResult? = null,
    ) : MessageApiClient {
        override fun sendMessage(
            session: AuthSession,
            view: MessageSendView,
        ): MessageSendResult = sendResult ?: MessageSendResult.Failure("Not configured", ExitCodes.UNKNOWN_ERROR)

        override fun fetchMessages(
            session: AuthSession,
            conversationId: String,
            limit: Int?,
            from: String?,
        ): MessageListResult = fetchResult ?: MessageListResult.Failure("Not configured", ExitCodes.UNKNOWN_ERROR)

        override fun fetchMessage(
            session: AuthSession,
            conversationId: String,
            messageId: String,
        ): MessageDetailResult = detailResult ?: MessageDetailResult.Failure("Not configured", ExitCodes.UNKNOWN_ERROR)
    }
}

class AuthGuardedMessageServiceTest {
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

    @Test
    fun `shouldRejectSendWithoutAuth`() {
        val authService =
            FakeAuthSessionService(
                AuthResult.Failure("No session", ExitCodes.UNAUTHORIZED),
            )
        val delegate =
            FakeMessageService(
                sendResult = MessageSendResult.Success(testMessage),
            )
        val service = AuthGuardedMessageService(authService, delegate)

        val result = service.send("conv-001", "Hello")

        val failure = assertIs<MessageSendResult.Failure>(result)
        assertEquals("No session", failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `shouldAcceptSendWithValidAuth`() {
        val authService = FakeAuthSessionService(AuthResult.Success)
        val delegate =
            FakeMessageService(
                sendResult = MessageSendResult.Success(testMessage),
            )
        val service = AuthGuardedMessageService(authService, delegate)

        val result = service.send("conv-001", "Hello")

        assertIs<MessageSendResult.Success>(result)
        assertEquals("msg-001", result.message.id)
    }

    @Test
    fun `shouldRejectFetchWithoutAuth`() {
        val authService =
            FakeAuthSessionService(
                AuthResult.Failure("Unauthorized", ExitCodes.UNAUTHORIZED),
            )
        val delegate =
            FakeMessageService(
                fetchResult = MessageListResult.Success(MessageListView(emptyList())),
            )
        val service = AuthGuardedMessageService(authService, delegate)

        val result = service.fetch("conv-001")

        val failure = assertIs<MessageListResult.Failure>(result)
        assertEquals("Unauthorized", failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `shouldAcceptFetchWithValidAuth`() {
        val messages = listOf(testMessage)
        val authService = FakeAuthSessionService(AuthResult.Success)
        val delegate =
            FakeMessageService(
                fetchResult = MessageListResult.Success(MessageListView(messages)),
            )
        val service = AuthGuardedMessageService(authService, delegate)

        val result = service.fetch("conv-001")

        val success = assertIs<MessageListResult.Success>(result)
        assertEquals(1, success.view.messages.size)
    }

    @Test
    fun `shouldRejectDetailWithoutAuth`() {
        val authService =
            FakeAuthSessionService(
                AuthResult.Failure("Not authenticated", ExitCodes.UNAUTHORIZED),
            )
        val delegate =
            FakeMessageService(
                detailResult = MessageDetailResult.Success(MessageDetailView(testMessage)),
            )
        val service = AuthGuardedMessageService(authService, delegate)

        val result = service.getDetail("conv-001", "msg-001")

        val failure = assertIs<MessageDetailResult.Failure>(result)
        assertEquals("Not authenticated", failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `shouldAcceptDetailWithValidAuth`() {
        val authService = FakeAuthSessionService(AuthResult.Success)
        val delegate =
            FakeMessageService(
                detailResult = MessageDetailResult.Success(MessageDetailView(testMessage)),
            )
        val service = AuthGuardedMessageService(authService, delegate)

        val result = service.getDetail("conv-001", "msg-001")

        val success = assertIs<MessageDetailResult.Success>(result)
        assertEquals("msg-001", success.view.message.id)
    }

    // Fake implementations for testing
    private class FakeAuthSessionService(
        private val result: AuthResult,
    ) : AuthSessionService {
        override fun login(input: Any): AuthResult = result

        override fun logout(): AuthResult = AuthResult.Success

        override fun requireActiveSession(): AuthResult = result
    }

    private class FakeMessageService(
        private val sendResult: MessageSendResult? = null,
        private val fetchResult: MessageListResult? = null,
        private val detailResult: MessageDetailResult? = null,
    ) : MessageService {
        override fun send(
            conversationId: String,
            text: String,
        ): MessageSendResult = sendResult ?: MessageSendResult.Failure("Not configured", ExitCodes.UNKNOWN_ERROR)

        override fun fetch(
            conversationId: String,
            limit: Int?,
            from: String?,
        ): MessageListResult = fetchResult ?: MessageListResult.Failure("Not configured", ExitCodes.UNKNOWN_ERROR)

        override fun getDetail(
            conversationId: String,
            messageId: String,
        ): MessageDetailResult = detailResult ?: MessageDetailResult.Failure("Not configured", ExitCodes.UNKNOWN_ERROR)
    }
}

class MessageServiceErrorHandlingTest {
    private val testSession =
        AuthSession(
            userId = "user-123",
            accessToken = "token-abc",
            server = "https://wire.example.com",
        )

    @Test
    fun `shouldHandleNetworkErrorOnSend`() {
        val apiClient =
            FakeMessageApiClientWithError(
                sendError = MessageSendResult.Failure("Network error", ExitCodes.NETWORK_ERROR),
            )
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
            )

        val result = service.send("conv-001", "Hello")

        val failure = assertIs<MessageSendResult.Failure>(result)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `shouldHandleNetworkErrorOnFetch`() {
        val apiClient =
            FakeMessageApiClientWithError(
                fetchError = MessageListResult.Failure("Network error", ExitCodes.NETWORK_ERROR),
            )
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
            )

        val result = service.fetch("conv-001")

        val failure = assertIs<MessageListResult.Failure>(result)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `shouldHandleConversationNotFoundError`() {
        val apiClient =
            FakeMessageApiClientWithError(
                fetchError = MessageListResult.Failure("Conversation not found", 16),
            )
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
            )

        val result = service.fetch("conv-nonexistent")

        val failure = assertIs<MessageListResult.Failure>(result)
        assertEquals(16, failure.exitCode)
    }

    @Test
    fun `shouldHandleInvalidInput`() {
        val apiClient =
            FakeMessageApiClientWithError(
                sendError = MessageSendResult.Failure("Invalid input", ExitCodes.VALIDATION_ERROR),
            )
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
            )

        val result = service.send("", "")

        val failure = assertIs<MessageSendResult.Failure>(result)
        assertEquals(ExitCodes.VALIDATION_ERROR, failure.exitCode)
    }

    // Fake implementations
    private class FakeSessionStore(private val activeSession: AuthSession?) : AuthSessionStore {
        override fun readActiveSession(): AuthSession? = activeSession

        override fun readSessionInventory(): SessionInventory =
            SessionInventory(
                activeSession = activeSession,
                validSessions = if (activeSession == null) 0 else 1,
                invalidSessions = 0,
            )
    }

    private class FakeMessageApiClientWithError(
        private val sendError: MessageSendResult? = null,
        private val fetchError: MessageListResult? = null,
        private val detailError: MessageDetailResult? = null,
    ) : MessageApiClient {
        override fun sendMessage(
            session: AuthSession,
            view: MessageSendView,
        ): MessageSendResult = sendError ?: MessageSendResult.Failure("Not configured", ExitCodes.UNKNOWN_ERROR)

        override fun fetchMessages(
            session: AuthSession,
            conversationId: String,
            limit: Int?,
            from: String?,
        ): MessageListResult = fetchError ?: MessageListResult.Failure("Not configured", ExitCodes.UNKNOWN_ERROR)

        override fun fetchMessage(
            session: AuthSession,
            conversationId: String,
            messageId: String,
        ): MessageDetailResult = detailError ?: MessageDetailResult.Failure("Not configured", ExitCodes.UNKNOWN_ERROR)
    }
}

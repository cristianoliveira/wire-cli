package wirecli.message

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes
import wirecli.auth.SessionInventory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionBackedMessageServiceTest {
    @Test
    fun `returns unauthorized when no session is persisted`() {
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient =
                    FakeMessageApiClient(
                        result = SendMessageResult.Success,
                    ),
            )

        val result = service.sendMessage("conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns backend success result for persisted session`() {
        val service =
            SessionBackedMessageService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = FakeMessageApiClient(result = SendMessageResult.Success),
            )

        val result = service.sendMessage("conv-123", "Hello")

        assertIs<SendMessageResult.Success>(result)
    }

    @Test
    fun `returns backend failure result for persisted session`() {
        val expected: SendMessageResult =
            SendMessageResult.Failure(
                message = MessageUserMessages.CONVERSATION_NOT_FOUND,
                exitCode = MessageExitCodes.NOT_FOUND,
            )
        val service =
            SessionBackedMessageService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = FakeMessageApiClient(result = expected),
            )

        val result = service.sendMessage("conv-invalid", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageUserMessages.CONVERSATION_NOT_FOUND, failure.message)
        assertEquals(MessageExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `delegates to API client with correct parameters`() {
        val capturedRequests = mutableListOf<Triple<AuthSession, String, String>>()
        val service =
            SessionBackedMessageService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token123",
                                server = "https://wire.example.com",
                            ),
                    ),
                apiClient =
                    FakeMessageApiClient(
                        result = SendMessageResult.Success,
                        captureRequests = capturedRequests,
                    ),
            )

        val result = service.sendMessage("conv-456", "Test message")

        assertIs<SendMessageResult.Success>(result)
        assertEquals(1, capturedRequests.size)
        val (session, conversationId, text) = capturedRequests[0]
        assertEquals("alice@example.com", session.userId)
        assertEquals("token123", session.accessToken)
        assertEquals("https://wire.example.com", session.server)
        assertEquals("conv-456", conversationId)
        assertEquals("Test message", text)
    }

    @Test
    fun `passes through network errors from API client`() {
        val service =
            SessionBackedMessageService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient =
                    FakeMessageApiClient(
                        result =
                            SendMessageResult.Failure(
                                message = MessageUserMessages.NETWORK_ERROR,
                                exitCode = ExitCodes.NETWORK_ERROR,
                            ),
                    ),
            )

        val result = service.sendMessage("conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageUserMessages.NETWORK_ERROR, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `passes through server errors from API client`() {
        val service =
            SessionBackedMessageService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient =
                    FakeMessageApiClient(
                        result =
                            SendMessageResult.Failure(
                                message = MessageUserMessages.SERVER_ERROR,
                                exitCode = ExitCodes.SERVER_ERROR,
                            ),
                    ),
            )

        val result = service.sendMessage("conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageUserMessages.SERVER_ERROR, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `fetchMessages returns unauthorized when no session is persisted`() {
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient = FakeMessageApiClient(result = SendMessageResult.Success),
            )

        val result = service.fetchMessages("conv-123")

        val failure = assertIs<FetchMessagesResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `fetchMessages returns backend success result for persisted session`() {
        val service =
            SessionBackedMessageService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient =
                    FakeMessageApiClient(
                        result = SendMessageResult.Success,
                        fetchResult =
                            FetchMessagesResult.Success(
                                FetchMessagesView(
                                    conversationId = "conv-123",
                                    messages = emptyList(),
                                ),
                            ),
                    ),
            )

        val result = service.fetchMessages("conv-123")

        assertIs<FetchMessagesResult.Success>(result)
    }

    @Test
    fun `sendTypingStatus returns unauthorized when no session is persisted`() {
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient = FakeMessageApiClient(result = SendMessageResult.Success),
            )

        val result = service.sendTypingStatus("conv-123", TypingStatus.STARTED)

        val failure = assertIs<SendTypingResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `sendTypingStatus delegates to API client with persisted session`() {
        val service =
            SessionBackedMessageService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token",
                                server = null,
                            ),
                    ),
                apiClient = FakeMessageApiClient(result = SendMessageResult.Success, typingResult = SendTypingResult.Success),
            )

        val result = service.sendTypingStatus("conv-123", TypingStatus.STOPPED)

        assertIs<SendTypingResult.Success>(result)
    }

    private class FakeSessionStore(private val activeSession: AuthSession?) : AuthSessionStore {
        override fun readActiveSession(): AuthSession? = activeSession

        override fun readSessionInventory(): SessionInventory =
            SessionInventory(
                activeSession = activeSession,
                validSessions = if (activeSession != null) 1 else 0,
                invalidSessions = 0,
            )

        override fun writeActiveSession(session: AuthSession) {}

        override fun clearActiveSession() {}
    }

    private class FakeMessageApiClient(
        private val result: SendMessageResult,
        private val fetchResult: FetchMessagesResult =
            FetchMessagesResult.Success(FetchMessagesView(conversationId = "conv", messages = emptyList())),
        private val typingResult: SendTypingResult = SendTypingResult.Success,
        private val captureRequests: MutableList<Triple<AuthSession, String, String>>? = null,
    ) : MessageApiClient {
        override fun sendMessage(
            session: AuthSession,
            conversationId: String,
            text: String,
        ): SendMessageResult {
            captureRequests?.add(Triple(session, conversationId, text))
            return result
        }

        override fun fetchMessages(
            session: AuthSession,
            conversationId: String,
        ): FetchMessagesResult {
            return fetchResult
        }

        override fun sendTypingStatus(
            session: AuthSession,
            conversationId: String,
            status: TypingStatus,
        ): SendTypingResult {
            return typingResult
        }
    }
}

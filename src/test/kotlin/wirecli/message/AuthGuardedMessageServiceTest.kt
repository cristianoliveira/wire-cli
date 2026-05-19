package wirecli.message

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthGuardedMessageServiceTest {
    @Test
    fun `returns unauthorized when requireActiveSession fails`() {
        val service =
            AuthGuardedMessageService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Failure("Not logged in", ExitCodes.UNAUTHORIZED),
                    ),
                delegate = FakeMessageService(result = SendMessageResult.Success),
            )

        val result = service.sendMessage("conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals("Not logged in", failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates to wrapped service when authorized`() {
        val service =
            AuthGuardedMessageService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Success("Authorized"),
                    ),
                delegate = FakeMessageService(result = SendMessageResult.Success),
            )

        val result = service.sendMessage("conv-123", "Hello")

        assertIs<SendMessageResult.Success>(result)
    }

    @Test
    fun `returns delegate failure when authorized but delegate fails`() {
        val delegateFailure: SendMessageResult =
            SendMessageResult.Failure(
                message = MessageUserMessages.CONVERSATION_NOT_FOUND,
                exitCode = MessageExitCodes.NOT_FOUND,
            )
        val service =
            AuthGuardedMessageService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Success("Authorized"),
                    ),
                delegate = FakeMessageService(result = delegateFailure),
            )

        val result = service.sendMessage("conv-invalid", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageUserMessages.CONVERSATION_NOT_FOUND, failure.message)
        assertEquals(MessageExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `calls requireActiveSession on auth service`() {
        var authCheckCalled = false
        val service =
            AuthGuardedMessageService(
                authSessionService =
                    object : AuthSessionService {
                        override fun login(input: wirecli.auth.LoginInput): AuthResult {
                            return AuthResult.Success("Logged in")
                        }

                        override fun logout(): AuthResult {
                            return AuthResult.Success("Logged out")
                        }

                        override fun requireActiveSession(): AuthResult {
                            authCheckCalled = true
                            return AuthResult.Success("Authorized")
                        }
                    },
                delegate = FakeMessageService(result = SendMessageResult.Success),
            )

        service.sendMessage("conv-123", "Hello")

        assert(authCheckCalled) { "requireActiveSession should have been called" }
    }

    @Test
    fun `delegates parameters correctly to wrapped service`() {
        val capturedRequests = mutableListOf<Pair<String, String>>()
        val service =
            AuthGuardedMessageService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Success("Authorized"),
                    ),
                delegate =
                    FakeMessageService(
                        result = SendMessageResult.Success,
                        captureRequests = capturedRequests,
                    ),
            )

        val result = service.sendMessage("conv-789", "My message")

        assertIs<SendMessageResult.Success>(result)
        assertEquals(1, capturedRequests.size)
        val (conversationId, text) = capturedRequests[0]
        assertEquals("conv-789", conversationId)
        assertEquals("My message", text)
    }

    @Test
    fun `returns proper error message when auth fails with specific code`() {
        val service =
            AuthGuardedMessageService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult =
                            AuthResult.Failure(
                                "your session has expired",
                                ExitCodes.UNAUTHORIZED,
                            ),
                    ),
                delegate = FakeMessageService(result = SendMessageResult.Success),
            )

        val result = service.sendMessage("conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals("your session has expired", failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `fetchMessages returns unauthorized when requireActiveSession fails`() {
        val service =
            AuthGuardedMessageService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Failure("Not logged in", ExitCodes.UNAUTHORIZED),
                    ),
                delegate = FakeMessageService(result = SendMessageResult.Success),
            )

        val result = service.fetchMessages("conv-123")

        val failure = assertIs<FetchMessagesResult.Failure>(result)
        assertEquals("Not logged in", failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `fetchMessages delegates to wrapped service when authorized`() {
        val service =
            AuthGuardedMessageService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Success("Authorized"),
                    ),
                delegate = FakeMessageService(result = SendMessageResult.Success),
            )

        val result = service.fetchMessages("conv-123")

        assertIs<FetchMessagesResult.Success>(result)
    }

    @Test
    fun `sendTypingStatus returns unauthorized when requireActiveSession fails`() {
        val service =
            AuthGuardedMessageService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Failure("Not logged in", ExitCodes.UNAUTHORIZED),
                    ),
                delegate = FakeMessageService(result = SendMessageResult.Success),
            )

        val result = service.sendTypingStatus("conv-123", TypingStatus.STARTED)

        val failure = assertIs<SendTypingResult.Failure>(result)
        assertEquals("Not logged in", failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    private class FakeAuthSessionService(private val authResult: AuthResult) : AuthSessionService {
        override fun login(input: wirecli.auth.LoginInput): AuthResult {
            return AuthResult.Success("Logged in")
        }

        override fun logout(): AuthResult {
            return AuthResult.Success("Logged out")
        }

        override fun requireActiveSession(): AuthResult = authResult
    }

    private class FakeMessageService(
        private val result: SendMessageResult,
        private val captureRequests: MutableList<Pair<String, String>>? = null,
        private val typingResult: SendTypingResult = SendTypingResult.Success,
    ) : MessageService {
        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult {
            captureRequests?.add(Pair(conversationId, text))
            return result
        }

        override fun fetchMessages(conversationId: String): FetchMessagesResult {
            return FetchMessagesResult.Success(
                FetchMessagesView(
                    conversationId = conversationId,
                    messages = emptyList(),
                ),
            )
        }

        override fun sendTypingStatus(
            conversationId: String,
            status: TypingStatus,
        ): SendTypingResult = typingResult

        override fun searchMessages(
            query: String,
            conversationId: String?,
            limit: Int,
        ): SearchMessagesResult = SearchMessagesResult.Success(emptyList())
    }
}

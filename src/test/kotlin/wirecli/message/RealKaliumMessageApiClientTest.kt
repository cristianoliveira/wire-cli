package wirecli.message

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for RealKaliumMessageApiClient
 *
 * Tests verify that the API client correctly:
 * - Maps all MessageFailureCategory values to appropriate exit codes
 * - Returns success results without modification
 * - Provides user-friendly error messages
 * - Delegates to MessageRuntime
 */
class RealKaliumMessageApiClientTest {
    private val testSession =
        AuthSession(
            userId = "alice@example.com",
            accessToken = "token123",
            server = "https://wire.example.com",
        )

    @Test
    fun `sendMessage returns Success when runtime succeeds`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Success(Unit),
                    ),
            )

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        assertIs<SendMessageResult.Success>(result)
    }

    @Test
    fun `sendMessage maps VALIDATION failure to exit code 14`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Failure(MessageFailureCategory.VALIDATION),
                    ),
            )

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageExitCodes.VALIDATION_ERROR, failure.exitCode)
        assertEquals(14, failure.exitCode)
        assertEquals(MessageUserMessages.VALIDATION_ERROR, failure.message)
    }

    @Test
    fun `sendMessage maps UNAUTHORIZED failure to exit code 11`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED),
                    ),
            )

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
        assertEquals(11, failure.exitCode)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
    }

    @Test
    fun `sendMessage maps NETWORK failure to exit code 12`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Failure(MessageFailureCategory.NETWORK),
                    ),
            )

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
        assertEquals(12, failure.exitCode)
        assertEquals(MessageUserMessages.NETWORK_ERROR, failure.message)
    }

    @Test
    fun `sendMessage maps TIMEOUT failure to timeout message and exit code 12`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Failure(MessageFailureCategory.TIMEOUT),
                    ),
            )

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
        assertEquals(12, failure.exitCode)
        assertEquals(MessageUserMessages.SEND_TIMEOUT, failure.message)
    }

    @Test
    fun `sendMessage maps SERVER failure to exit code 13`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Failure(MessageFailureCategory.SERVER),
                    ),
            )

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
        assertEquals(13, failure.exitCode)
        assertEquals(MessageUserMessages.SERVER_ERROR, failure.message)
    }

    @Test
    fun `sendMessage maps NOT_FOUND failure to exit code 13`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Failure(MessageFailureCategory.NOT_FOUND),
                    ),
            )

        val result = client.sendMessage(testSession, "conv-invalid", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(MessageExitCodes.NOT_FOUND, failure.exitCode)
        assertEquals(13, failure.exitCode)
        assertEquals(MessageUserMessages.CONVERSATION_NOT_FOUND, failure.message)
    }

    @Test
    fun `sendMessage maps UNKNOWN failure to exit code 1`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Failure(MessageFailureCategory.UNKNOWN),
                    ),
            )

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        assertEquals(ExitCodes.UNKNOWN_ERROR, failure.exitCode)
        assertEquals(1, failure.exitCode)
    }

    @Test
    fun `sendMessage delegates all parameters to runtime`() {
        val capturedCalls = mutableListOf<Triple<AuthSession, String, String>>()
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Success(Unit),
                        captureCalls = capturedCalls,
                    ),
            )

        val session =
            AuthSession(
                userId = "bob@wire.com",
                accessToken = "token456",
                server = "https://other.example.com",
            )
        client.sendMessage(session, "conv-789", "Test message")

        assertEquals(1, capturedCalls.size)
        val (capturedSession, capturedConvId, capturedText) = capturedCalls[0]
        assertEquals("bob@wire.com", capturedSession.userId)
        assertEquals("token456", capturedSession.accessToken)
        assertEquals("https://other.example.com", capturedSession.server)
        assertEquals("conv-789", capturedConvId)
        assertEquals("Test message", capturedText)
    }

    @Test
    fun `sendMessage handles all failure categories consistently`() {
        val categories =
            listOf(
                MessageFailureCategory.VALIDATION to MessageExitCodes.VALIDATION_ERROR,
                MessageFailureCategory.UNAUTHORIZED to ExitCodes.UNAUTHORIZED,
                MessageFailureCategory.TIMEOUT to ExitCodes.NETWORK_ERROR,
                MessageFailureCategory.NETWORK to ExitCodes.NETWORK_ERROR,
                MessageFailureCategory.SERVER to ExitCodes.SERVER_ERROR,
                MessageFailureCategory.NOT_FOUND to MessageExitCodes.NOT_FOUND,
                MessageFailureCategory.UNKNOWN to ExitCodes.UNKNOWN_ERROR,
            )

        for ((category, expectedExitCode) in categories) {
            val client =
                RealKaliumMessageApiClient(
                    runtime =
                        FakeKaliumMessageRuntime(
                            result = MessageStepResult.Failure(category),
                        ),
                )

            val result = client.sendMessage(testSession, "conv-123", "Hello")

            val failure = assertIs<SendMessageResult.Failure>(result)
            assertEquals(
                expectedExitCode,
                failure.exitCode,
                "Exit code mismatch for category: $category",
            )
        }
    }

    @Test
    fun `sendMessage returns different error messages for different failure categories`() {
        val client1 =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Failure(MessageFailureCategory.VALIDATION),
                    ),
            )
        val client2 =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Failure(MessageFailureCategory.NETWORK),
                    ),
            )
        val client3 =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Failure(MessageFailureCategory.SERVER),
                    ),
            )

        val result1 = client1.sendMessage(testSession, "conv-123", "Hello")
        val result2 = client2.sendMessage(testSession, "conv-123", "Hello")
        val result3 = client3.sendMessage(testSession, "conv-123", "Hello")

        val failure1 = assertIs<SendMessageResult.Failure>(result1)
        val failure2 = assertIs<SendMessageResult.Failure>(result2)
        val failure3 = assertIs<SendMessageResult.Failure>(result3)

        assert(failure1.message != failure2.message) { "Different categories should have different messages" }
        assert(failure2.message != failure3.message) { "Different categories should have different messages" }
        assert(failure1.message != failure3.message) { "Different categories should have different messages" }
    }

    @Test
    fun `sendMessage provides user-friendly error messages for all failure types`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Failure(MessageFailureCategory.NETWORK),
                    ),
            )

        val result = client.sendMessage(testSession, "conv-123", "Hello")

        val failure = assertIs<SendMessageResult.Failure>(result)
        // Verify message is not a technical error but user-friendly
        assert(!failure.message.contains("NullPointer")) { "Error message should be user-friendly" }
        assert(!failure.message.contains("Exception")) { "Error message should be user-friendly" }
        assert(!failure.message.isEmpty()) { "Error message should not be empty" }
    }

    @Test
    fun `sendMessage success message is different from all failure messages`() {
        val successClient =
            RealKaliumMessageApiClient(
                runtime = FakeKaliumMessageRuntime(result = MessageStepResult.Success(Unit)),
            )
        val failureClients =
            listOf(
                FakeKaliumMessageRuntime(
                    result = MessageStepResult.Failure(MessageFailureCategory.VALIDATION),
                ),
                FakeKaliumMessageRuntime(
                    result = MessageStepResult.Failure(MessageFailureCategory.NETWORK),
                ),
                FakeKaliumMessageRuntime(
                    result = MessageStepResult.Failure(MessageFailureCategory.SERVER),
                ),
            )

        val successResult = successClient.sendMessage(testSession, "conv-123", "Hello")
        assertIs<SendMessageResult.Success>(successResult)

        for (failureRuntime in failureClients) {
            val client = RealKaliumMessageApiClient(runtime = failureRuntime)
            val result = client.sendMessage(testSession, "conv-123", "Hello")
            assertIs<SendMessageResult.Failure>(result)
        }
    }

    @Test
    fun `sendMessage works with multiple calls`() {
        val client =
            RealKaliumMessageApiClient(
                runtime = FakeKaliumMessageRuntime(result = MessageStepResult.Success(Unit)),
            )

        val result1 = client.sendMessage(testSession, "conv-123", "Message 1")
        val result2 = client.sendMessage(testSession, "conv-456", "Message 2")
        val result3 = client.sendMessage(testSession, "conv-789", "Message 3")

        assertIs<SendMessageResult.Success>(result1)
        assertIs<SendMessageResult.Success>(result2)
        assertIs<SendMessageResult.Success>(result3)
    }

    @Test
    fun `sendMessage has no timeout guard when runtime never returns`() {
        val releaseLatch = CountDownLatch(1)
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    HangingKaliumMessageRuntime(
                        releaseLatch = releaseLatch,
                    ),
            )

        val worker =
            Thread {
                client.sendMessage(testSession, "conv-123", "Hello")
            }.apply {
                isDaemon = true
                start()
            }

        worker.join(200)
        assertTrue(worker.isAlive, "sendMessage unexpectedly returned; expected to remain blocked")

        releaseLatch.countDown()
        worker.join(1000)
    }

    @Test
    fun `fetchMessages returns Success with mapped view when runtime succeeds`() {
        val messages =
            listOf(
                ConversationMessage(
                    id = "msg-1",
                    senderId = "alice@example.com",
                    senderName = "Alice",
                    timestamp = "2026-03-20T10:00:00Z",
                    content = "Hello",
                ),
            )
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Success(Unit),
                        fetchResult = MessageStepResult.Success(messages),
                    ),
            )

        val result = client.fetchMessages(testSession, "conv-123")

        val success = assertIs<FetchMessagesResult.Success>(result)
        assertEquals("conv-123", success.view.conversationId)
        assertEquals(1, success.view.messages.size)
    }

    @Test
    fun `fetchMessages maps NOT_FOUND failure to exit code 13`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Success(Unit),
                        fetchResult = MessageStepResult.Failure(MessageFailureCategory.NOT_FOUND),
                    ),
            )

        val result = client.fetchMessages(testSession, "conv-missing")

        val failure = assertIs<FetchMessagesResult.Failure>(result)
        assertEquals(MessageUserMessages.CONVERSATION_NOT_FOUND, failure.message)
        assertEquals(MessageExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `fetchMessages maps core failure categories to expected exit codes`() {
        val categories =
            listOf(
                MessageFailureCategory.VALIDATION to MessageExitCodes.VALIDATION_ERROR,
                MessageFailureCategory.UNAUTHORIZED to ExitCodes.UNAUTHORIZED,
                MessageFailureCategory.NETWORK to ExitCodes.NETWORK_ERROR,
                MessageFailureCategory.TIMEOUT to ExitCodes.NETWORK_ERROR,
                MessageFailureCategory.SERVER to ExitCodes.SERVER_ERROR,
                MessageFailureCategory.UNKNOWN to ExitCodes.UNKNOWN_ERROR,
            )

        for ((category, expectedExitCode) in categories) {
            val client =
                RealKaliumMessageApiClient(
                    runtime =
                        FakeKaliumMessageRuntime(
                            result = MessageStepResult.Success(Unit),
                            fetchResult = MessageStepResult.Failure(category),
                        ),
                )

            val result = client.fetchMessages(testSession, "conv-123")

            val failure = assertIs<FetchMessagesResult.Failure>(result)
            assertEquals(expectedExitCode, failure.exitCode)
        }
    }

    @Test
    fun `fetchMessages maps TIMEOUT failure to timeout message and exit code 12`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Success(Unit),
                        fetchResult = MessageStepResult.Failure(MessageFailureCategory.TIMEOUT),
                    ),
            )

        val result = client.fetchMessages(testSession, "conv-123")

        val failure = assertIs<FetchMessagesResult.Failure>(result)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
        assertEquals(12, failure.exitCode)
    }

    @Test
    fun `fetchMessages returns empty messages list when runtime succeeds with empty result`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntime(
                        result = MessageStepResult.Success(Unit),
                        fetchResult = MessageStepResult.Success(emptyList()),
                    ),
            )

        val result = client.fetchMessages(testSession, "conv-123")

        val success = assertIs<FetchMessagesResult.Success>(result)
        assertEquals(0, success.view.messages.size)
    }

    @Test
    fun `fetchMessages delegates all parameters to runtime correctly`() {
        val capturedFetchCalls = mutableListOf<Pair<AuthSession, String>>()
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    FakeKaliumMessageRuntimeWithFetchCapture(
                        fetchResult = MessageStepResult.Success(emptyList()),
                        captureFetchCalls = capturedFetchCalls,
                    ),
            )

        val session =
            AuthSession(
                userId = "bob@wire.com",
                accessToken = "token456",
                server = "https://other.example.com",
            )
        client.fetchMessages(session, "conv-789")

        assertEquals(1, capturedFetchCalls.size)
        val (capturedSession, capturedConvId) = capturedFetchCalls[0]
        assertEquals("bob@wire.com", capturedSession.userId)
        assertEquals("token456", capturedSession.accessToken)
        assertEquals("https://other.example.com", capturedSession.server)
        assertEquals("conv-789", capturedConvId)
    }

    @Test
    fun `sendTypingStatus returns success when runtime succeeds`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    object : MessageRuntime {
                        override fun sendMessage(
                            session: AuthSession,
                            conversationId: String,
                            text: String,
                        ): MessageStepResult<Unit> = MessageStepResult.Success(Unit)

                        override fun fetchMessages(
                            session: AuthSession,
                            conversationId: String,
                        ): MessageStepResult<List<ConversationMessage>> = MessageStepResult.Success(emptyList())

                        override fun sendTypingStatus(
                            session: AuthSession,
                            conversationId: String,
                            status: TypingStatus,
                        ): MessageStepResult<Unit> = MessageStepResult.Success(Unit)

                        override fun searchMessages(
                            session: AuthSession,
                            query: String,
                            conversationId: String?,
                            limit: Int,
                        ): MessageStepResult<List<MessageSearchResult>> = MessageStepResult.Success(emptyList())

                        override fun shutdown() {
                            // No-op for test stub
                        }
                    },
            )

        val result = client.sendTypingStatus(testSession, "conv-123", TypingStatus.STARTED)

        assertIs<SendTypingResult.Success>(result)
    }

    @Test
    fun `sendTypingStatus maps timeout to exit code 12`() {
        val client =
            RealKaliumMessageApiClient(
                runtime =
                    object : MessageRuntime {
                        override fun sendMessage(
                            session: AuthSession,
                            conversationId: String,
                            text: String,
                        ): MessageStepResult<Unit> = MessageStepResult.Success(Unit)

                        override fun fetchMessages(
                            session: AuthSession,
                            conversationId: String,
                        ): MessageStepResult<List<ConversationMessage>> = MessageStepResult.Success(emptyList())

                        override fun sendTypingStatus(
                            session: AuthSession,
                            conversationId: String,
                            status: TypingStatus,
                        ): MessageStepResult<Unit> = MessageStepResult.Failure(MessageFailureCategory.TIMEOUT)

                        override fun searchMessages(
                            session: AuthSession,
                            query: String,
                            conversationId: String?,
                            limit: Int,
                        ): MessageStepResult<List<MessageSearchResult>> = MessageStepResult.Success(emptyList())

                        override fun shutdown() {
                            // No-op for test stub
                        }
                    },
            )

        val result = client.sendTypingStatus(testSession, "conv-123", TypingStatus.STOPPED)

        val failure = assertIs<SendTypingResult.Failure>(result)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
        assertEquals(MessageUserMessages.TYPING_TIMEOUT, failure.message)
    }

    @Test
    fun `fetchLocalMessages delegates cache-only read to runtime`() {
        val localFetchCalls = mutableListOf<Pair<AuthSession, String>>()
        val client =
            RealKaliumMessageApiClient(
                FakeKaliumMessageRuntime(
                    result = MessageStepResult.Success(Unit),
                    localFetchCalls = localFetchCalls,
                ),
            )

        val result = client.fetchLocalMessages(testSession, "conv-local")

        assertIs<FetchMessagesResult.Success>(result)
        assertEquals(listOf(testSession to "conv-local"), localFetchCalls)
    }

    @Test
    fun `setMessageRead delegates coordinates and returns success`() {
        val calls = mutableListOf<Triple<AuthSession, String, String>>()
        val client =
            RealKaliumMessageApiClient(
                FakeKaliumMessageRuntime(
                    result = MessageStepResult.Success(Unit),
                    setReadCalls = calls,
                ),
            )

        val result = client.setMessageRead(testSession, "conv-1", "msg-1")

        assertIs<SetMessageReadResult.Success>(result)
        assertEquals(listOf(Triple(testSession, "conv-1", "msg-1")), calls)
    }

    @Test
    fun `setMessageRead preserves already read outcome`() {
        val client =
            RealKaliumMessageApiClient(
                FakeKaliumMessageRuntime(
                    result = MessageStepResult.Success(Unit),
                    setReadResult = MessageStepResult.Success(SetMessageReadOutcome.ALREADY_READ),
                ),
            )

        val result = client.setMessageRead(testSession, "conv-1", "msg-1")

        assertIs<SetMessageReadResult.AlreadyRead>(result)
    }

    @Test
    fun `setMessageRead maps missing message failure`() {
        val client =
            RealKaliumMessageApiClient(
                FakeKaliumMessageRuntime(
                    result = MessageStepResult.Success(Unit),
                    setReadResult = MessageStepResult.Failure(MessageFailureCategory.NOT_FOUND),
                ),
            )

        val result = client.setMessageRead(testSession, "conv-1", "missing")

        val failure = assertIs<SetMessageReadResult.Failure>(result)
        assertEquals(MessageUserMessages.MESSAGE_NOT_FOUND, failure.message)
        assertEquals(MessageExitCodes.NOT_FOUND, failure.exitCode)
    }

    // ==================== Helper Classes ====================

    private class FakeKaliumMessageRuntime(
        private val result: MessageStepResult<Unit>,
        private val fetchResult: MessageStepResult<List<ConversationMessage>> = MessageStepResult.Success(emptyList()),
        private val captureCalls: MutableList<Triple<AuthSession, String, String>>? = null,
        private val localFetchCalls: MutableList<Pair<AuthSession, String>>? = null,
        private val setReadResult: MessageStepResult<SetMessageReadOutcome> =
            MessageStepResult.Success(SetMessageReadOutcome.APPLIED),
        private val setReadCalls: MutableList<Triple<AuthSession, String, String>>? = null,
    ) : MessageRuntime {
        override fun sendMessage(
            session: AuthSession,
            conversationId: String,
            text: String,
        ): MessageStepResult<Unit> {
            captureCalls?.add(Triple(session, conversationId, text))
            return result
        }

        override fun fetchMessages(
            session: AuthSession,
            conversationId: String,
        ): MessageStepResult<List<ConversationMessage>> {
            return fetchResult
        }

        override fun fetchLocalMessages(
            session: AuthSession,
            conversationId: String,
        ): MessageStepResult<List<ConversationMessage>> {
            localFetchCalls?.add(session to conversationId)
            return fetchResult
        }

        override fun setMessageRead(
            session: AuthSession,
            conversationId: String,
            messageId: String,
        ): MessageStepResult<SetMessageReadOutcome> {
            setReadCalls?.add(Triple(session, conversationId, messageId))
            return setReadResult
        }

        override fun shutdown() {
            // No-op for test stub
        }
    }

    private class HangingKaliumMessageRuntime(
        private val releaseLatch: CountDownLatch,
    ) : MessageRuntime {
        override fun sendMessage(
            session: AuthSession,
            conversationId: String,
            text: String,
        ): MessageStepResult<Unit> {
            releaseLatch.await(5, TimeUnit.SECONDS)
            return MessageStepResult.Failure(MessageFailureCategory.UNKNOWN)
        }

        override fun fetchMessages(
            session: AuthSession,
            conversationId: String,
        ): MessageStepResult<List<ConversationMessage>> {
            releaseLatch.await(5, TimeUnit.SECONDS)
            return MessageStepResult.Failure(MessageFailureCategory.UNKNOWN)
        }

        override fun shutdown() {
            // No-op for test stub
        }
    }

    private class FakeKaliumMessageRuntimeWithFetchCapture(
        private val fetchResult: MessageStepResult<List<ConversationMessage>>,
        private val captureFetchCalls: MutableList<Pair<AuthSession, String>>? = null,
    ) : MessageRuntime {
        override fun sendMessage(
            session: AuthSession,
            conversationId: String,
            text: String,
        ): MessageStepResult<Unit> = MessageStepResult.Success(Unit)

        override fun fetchMessages(
            session: AuthSession,
            conversationId: String,
        ): MessageStepResult<List<ConversationMessage>> {
            captureFetchCalls?.add(Pair(session, conversationId))
            return fetchResult
        }

        override fun shutdown() {
            // No-op for test stub
        }
    }
}

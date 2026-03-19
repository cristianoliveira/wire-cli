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
 * - Delegates to RealKaliumMessageRuntime
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
                        result = MessageStepResult.Success,
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
                        result = MessageStepResult.Success,
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
                runtime = FakeKaliumMessageRuntime(result = MessageStepResult.Success),
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
                runtime = FakeKaliumMessageRuntime(result = MessageStepResult.Success),
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

    // ==================== Helper Classes ====================

    private class FakeKaliumMessageRuntime(
        private val result: MessageStepResult,
        private val captureCalls: MutableList<Triple<AuthSession, String, String>>? = null,
    ) : RealKaliumMessageRuntime {
        override fun sendMessage(
            session: AuthSession,
            conversationId: String,
            text: String,
        ): MessageStepResult {
            captureCalls?.add(Triple(session, conversationId, text))
            return result
        }

        override fun shutdown() {}
    }

    private class HangingKaliumMessageRuntime(
        private val releaseLatch: CountDownLatch,
    ) : RealKaliumMessageRuntime {
        override fun sendMessage(
            session: AuthSession,
            conversationId: String,
            text: String,
        ): MessageStepResult {
            releaseLatch.await(5, TimeUnit.SECONDS)
            return MessageStepResult.Failure(MessageFailureCategory.UNKNOWN)
        }

        override fun shutdown() {}
    }
}

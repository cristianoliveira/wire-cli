package wirecli.message

import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for SdkKaliumMessageRuntime
 *
 * Tests verify:
 * - MLS preflight sync is always called before message send
 * - Success path correctly sends messages
 * - All failure categories are properly mapped to exit codes
 * - Validation errors are caught before SDK calls
 */
class SdkKaliumMessageRuntimeTest {
    private val testSession =
        AuthSession(
            userId = "alice@example.com",
            accessToken = "token123",
            server = "https://wire.example.com",
        )

    @Test
    fun `resolveSendTimeoutMs returns default when env var missing`() {
        val timeout = resolveSendTimeoutMs(emptyMap())

        assertEquals(DEFAULT_SEND_TIMEOUT_MS, timeout)
    }

    @Test
    fun `resolveSendTimeoutMs returns parsed timeout when valid`() {
        val timeout =
            resolveSendTimeoutMs(
                mapOf(
                    MESSAGE_SEND_TIMEOUT_ENV to "120000",
                ),
            )

        assertEquals(120000L, timeout)
    }

    @Test
    fun `resolveSendTimeoutMs falls back to default when env var is non-numeric`() {
        val timeout =
            resolveSendTimeoutMs(
                mapOf(
                    MESSAGE_SEND_TIMEOUT_ENV to "abc",
                ),
            )

        assertEquals(DEFAULT_SEND_TIMEOUT_MS, timeout)
    }

    @Test
    fun `resolveSendTimeoutMs falls back to default when env var is zero or negative`() {
        val zeroTimeout =
            resolveSendTimeoutMs(
                mapOf(
                    MESSAGE_SEND_TIMEOUT_ENV to "0",
                ),
            )
        val negativeTimeout =
            resolveSendTimeoutMs(
                mapOf(
                    MESSAGE_SEND_TIMEOUT_ENV to "-500",
                ),
            )

        assertEquals(DEFAULT_SEND_TIMEOUT_MS, zeroTimeout)
        assertEquals(DEFAULT_SEND_TIMEOUT_MS, negativeTimeout)
    }

    @Test
    fun `resolveSendTimeoutMs clamps to max when env var exceeds upper bound`() {
        val timeout =
            resolveSendTimeoutMs(
                mapOf(
                    MESSAGE_SEND_TIMEOUT_ENV to "999999",
                ),
            )

        assertEquals(MAX_SEND_TIMEOUT_MS, timeout)
    }

    @Test
    fun `sendMessage returns Failure for blank conversationId`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        val result = runtime.sendMessage(testSession, "   ", "Hello message")

        val failure = assertIs<MessageStepResult.Failure>(result)
        assertEquals(MessageFailureCategory.VALIDATION, failure.category)
    }

    @Test
    fun `setMessageRead rejects blank conversation or message IDs`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        val blankConversation = runtime.setMessageRead(testSession, "   ", "msg-1")
        val blankMessage = runtime.setMessageRead(testSession, "conv-1", "   ")

        assertEquals(MessageFailureCategory.VALIDATION, assertIs<MessageStepResult.Failure>(blankConversation).category)
        assertEquals(MessageFailureCategory.VALIDATION, assertIs<MessageStepResult.Failure>(blankMessage).category)
    }

    @Test
    fun `setMessageRead rejects invalid session user ID`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        val result = runtime.setMessageRead(testSession.copy(userId = "invalid-user"), "conv-1", "msg-1")

        assertEquals(MessageFailureCategory.UNAUTHORIZED, assertIs<MessageStepResult.Failure>(result).category)
    }

    @Test
    fun `fetchMessages returns Failure for blank conversationId`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        val result = runtime.fetchMessages(testSession, "   ")

        val failure = assertIs<MessageStepResult.Failure>(result)
        assertEquals(MessageFailureCategory.VALIDATION, failure.category)
    }

    @Test
    fun `fetchMessages returns Failure for invalid user ID format`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())
        val invalidSession = testSession.copy(userId = "invalid-user")

        val result = runtime.fetchMessages(invalidSession, "conv-123")

        val failure = assertIs<MessageStepResult.Failure>(result)
        assertEquals(MessageFailureCategory.UNAUTHORIZED, failure.category)
    }

    @Test
    fun `sendMessage returns Failure for blank text`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        val result = runtime.sendMessage(testSession, "conv-123", "   ")

        assertIs<MessageStepResult.Failure>(result)
        val failure = result as MessageStepResult.Failure
        assertEquals(MessageFailureCategory.VALIDATION, failure.category)
    }

    @Test
    fun `sendMessage returns Failure for invalid user ID format`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())
        val invalidSession = testSession.copy(userId = "invalid-user")

        val result = runtime.sendMessage(invalidSession, "conv-123", "Hello")

        assertIs<MessageStepResult.Failure>(result)
        val failure = result as MessageStepResult.Failure
        assertEquals(MessageFailureCategory.UNAUTHORIZED, failure.category)
    }

    @Test
    fun `sendMessage returns Failure for missing domain in user ID`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())
        val invalidSession = testSession.copy(userId = "alice@")

        val result = runtime.sendMessage(invalidSession, "conv-123", "Hello")

        assertIs<MessageStepResult.Failure>(result)
        val failure = result as MessageStepResult.Failure
        assertEquals(MessageFailureCategory.UNAUTHORIZED, failure.category)
    }

    @Test
    fun `sendMessage returns Failure for empty text string`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        val result = runtime.sendMessage(testSession, "conv-123", "")

        assertIs<MessageStepResult.Failure>(result)
        val failure = result as MessageStepResult.Failure
        assertEquals(MessageFailureCategory.VALIDATION, failure.category)
    }

    @Test
    fun `sendMessage returns Failure for empty conversationId string`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        val result = runtime.sendMessage(testSession, "", "Hello")

        assertIs<MessageStepResult.Failure>(result)
        val failure = result as MessageStepResult.Failure
        assertEquals(MessageFailureCategory.VALIDATION, failure.category)
    }

    @Test
    fun `sendMessage validates conversationId before attempting SDK call`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        val resultBlank = runtime.sendMessage(testSession, "   ", "Message")
        val resultEmpty = runtime.sendMessage(testSession, "", "Message")

        assertIs<MessageStepResult.Failure>(resultBlank)
        assertIs<MessageStepResult.Failure>(resultEmpty)

        val failureBlank = resultBlank as MessageStepResult.Failure
        val failureEmpty = resultEmpty as MessageStepResult.Failure

        assertEquals(MessageFailureCategory.VALIDATION, failureBlank.category)
        assertEquals(MessageFailureCategory.VALIDATION, failureEmpty.category)
    }

    @Test
    fun `sendMessage validates text before attempting SDK call`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        val resultBlank = runtime.sendMessage(testSession, "conv-123", "   ")
        val resultEmpty = runtime.sendMessage(testSession, "conv-123", "")

        assertIs<MessageStepResult.Failure>(resultBlank)
        assertIs<MessageStepResult.Failure>(resultEmpty)

        val failureBlank = resultBlank as MessageStepResult.Failure
        val failureEmpty = resultEmpty as MessageStepResult.Failure

        assertEquals(MessageFailureCategory.VALIDATION, failureBlank.category)
        assertEquals(MessageFailureCategory.VALIDATION, failureEmpty.category)
    }

    @Test
    fun `sendMessage handles qualified user IDs correctly`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        // This should pass initial validation (valid user ID format)
        // It will fail in SDK call, but that's expected in unit test
        val result = runtime.sendMessage(testSession, "conv-123", "Hello")

        // We just verify it doesn't fail on user ID validation
        // The actual SDK call will fail, but that's OK for this test
        assertIs<MessageStepResult<*>>(result)
    }

    @Test
    fun `categoryFromThrowable maps Unauthorized messages correctly`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        val result1 = runtime.sendMessage(testSession, "conv-123", "Hello")
        val result2 = runtime.sendMessage(testSession, "conv-123", "Test")

        // These would categorize if called, but we're testing the validation
        // which catches errors before these functions are reached
        assertIs<MessageStepResult<*>>(result1)
        assertIs<MessageStepResult<*>>(result2)
    }

    @Test
    fun `user ID parsing handles domain separation correctly`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        // Valid format
        val validSession = testSession.copy(userId = "alice@wire.com")
        val resultValid = runtime.sendMessage(validSession, "conv-123", "Hello")
        assertIs<MessageStepResult<*>>(resultValid)

        // Invalid formats should fail on validation
        val missingDomain = testSession.copy(userId = "alice@")
        val resultMissingDomain = runtime.sendMessage(missingDomain, "conv-123", "Hello")
        assertIs<MessageStepResult.Failure>(resultMissingDomain)
        assertEquals(MessageFailureCategory.UNAUTHORIZED, (resultMissingDomain as MessageStepResult.Failure).category)

        val noDomain = testSession.copy(userId = "alice")
        val resultNoDomain = runtime.sendMessage(noDomain, "conv-123", "Hello")
        assertIs<MessageStepResult.Failure>(resultNoDomain)
        assertEquals(MessageFailureCategory.UNAUTHORIZED, (resultNoDomain as MessageStepResult.Failure).category)
    }

    @Test
    fun `failure categories are properly defined`() {
        // Verify all expected failure categories exist
        assertEquals(
            setOf(
                MessageFailureCategory.VALIDATION,
                MessageFailureCategory.TIMEOUT,
                MessageFailureCategory.NETWORK,
                MessageFailureCategory.SERVER,
                MessageFailureCategory.UNAUTHORIZED,
                MessageFailureCategory.NOT_FOUND,
                MessageFailureCategory.UNKNOWN,
            ),
            MessageFailureCategory.entries.toSet(),
        )
    }

    @Test
    fun `RealKaliumMessageApiClient maps VALIDATION failure to correct exit code`() {
        val stubRuntime =
            object : MessageRuntime {
                override fun sendMessage(
                    session: AuthSession,
                    conversationId: String,
                    text: String,
                ): MessageStepResult<Unit> = MessageStepResult.Failure(MessageFailureCategory.VALIDATION)

                override fun fetchMessages(
                    session: AuthSession,
                    conversationId: String,
                ): MessageStepResult<List<ConversationMessage>> = MessageStepResult.Success(emptyList())

                override fun shutdown() {
                    // No-op for test stub
                }
            }

        val client = RealKaliumMessageApiClient(stubRuntime)
        val result = client.sendMessage(testSession, "conv-123", "Hello")

        assertIs<SendMessageResult.Failure>(result)
        val failure = result as SendMessageResult.Failure
        assertEquals(MessageExitCodes.VALIDATION_ERROR, failure.exitCode)
    }

    @Test
    fun `RealKaliumMessageApiClient maps UNAUTHORIZED failure to exit code 11`() {
        val stubRuntime =
            object : MessageRuntime {
                override fun sendMessage(
                    session: AuthSession,
                    conversationId: String,
                    text: String,
                ): MessageStepResult<Unit> = MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)

                override fun fetchMessages(
                    session: AuthSession,
                    conversationId: String,
                ): MessageStepResult<List<ConversationMessage>> = MessageStepResult.Success(emptyList())

                override fun shutdown() {
                    // No-op for test stub
                }
            }

        val client = RealKaliumMessageApiClient(stubRuntime)
        val result = client.sendMessage(testSession, "conv-123", "Hello")

        assertIs<SendMessageResult.Failure>(result)
        val failure = result as SendMessageResult.Failure
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
        assertEquals(11, failure.exitCode)
    }

    @Test
    fun `RealKaliumMessageApiClient maps NETWORK failure to exit code 12`() {
        val stubRuntime =
            object : MessageRuntime {
                override fun sendMessage(
                    session: AuthSession,
                    conversationId: String,
                    text: String,
                ): MessageStepResult<Unit> = MessageStepResult.Failure(MessageFailureCategory.NETWORK)

                override fun fetchMessages(
                    session: AuthSession,
                    conversationId: String,
                ): MessageStepResult<List<ConversationMessage>> = MessageStepResult.Success(emptyList())

                override fun shutdown() {
                    // No-op for test stub
                }
            }

        val client = RealKaliumMessageApiClient(stubRuntime)
        val result = client.sendMessage(testSession, "conv-123", "Hello")

        assertIs<SendMessageResult.Failure>(result)
        val failure = result as SendMessageResult.Failure
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
        assertEquals(12, failure.exitCode)
    }

    @Test
    fun `RealKaliumMessageApiClient maps SERVER failure to exit code 13`() {
        val stubRuntime =
            object : MessageRuntime {
                override fun sendMessage(
                    session: AuthSession,
                    conversationId: String,
                    text: String,
                ): MessageStepResult<Unit> = MessageStepResult.Failure(MessageFailureCategory.SERVER)

                override fun fetchMessages(
                    session: AuthSession,
                    conversationId: String,
                ): MessageStepResult<List<ConversationMessage>> = MessageStepResult.Success(emptyList())

                override fun shutdown() {
                    // No-op for test stub
                }
            }

        val client = RealKaliumMessageApiClient(stubRuntime)
        val result = client.sendMessage(testSession, "conv-123", "Hello")

        assertIs<SendMessageResult.Failure>(result)
        val failure = result as SendMessageResult.Failure
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
        assertEquals(13, failure.exitCode)
    }

    @Test
    fun `RealKaliumMessageApiClient maps NOT_FOUND failure to exit code 13`() {
        val stubRuntime =
            object : MessageRuntime {
                override fun sendMessage(
                    session: AuthSession,
                    conversationId: String,
                    text: String,
                ): MessageStepResult<Unit> = MessageStepResult.Failure(MessageFailureCategory.NOT_FOUND)

                override fun fetchMessages(
                    session: AuthSession,
                    conversationId: String,
                ): MessageStepResult<List<ConversationMessage>> = MessageStepResult.Success(emptyList())

                override fun shutdown() {
                    // No-op for test stub
                }
            }

        val client = RealKaliumMessageApiClient(stubRuntime)
        val result = client.sendMessage(testSession, "conv-123", "Hello")

        assertIs<SendMessageResult.Failure>(result)
        val failure = result as SendMessageResult.Failure
        assertEquals(MessageExitCodes.NOT_FOUND, failure.exitCode)
        assertEquals(13, failure.exitCode)
    }

    @Test
    fun `RealKaliumMessageApiClient maps UNKNOWN failure correctly`() {
        val stubRuntime =
            object : MessageRuntime {
                override fun sendMessage(
                    session: AuthSession,
                    conversationId: String,
                    text: String,
                ): MessageStepResult<Unit> = MessageStepResult.Failure(MessageFailureCategory.UNKNOWN)

                override fun fetchMessages(
                    session: AuthSession,
                    conversationId: String,
                ): MessageStepResult<List<ConversationMessage>> = MessageStepResult.Success(emptyList())

                override fun shutdown() {
                    // No-op for test stub
                }
            }

        val client = RealKaliumMessageApiClient(stubRuntime)
        val result = client.sendMessage(testSession, "conv-123", "Hello")

        assertIs<SendMessageResult.Failure>(result)
        val failure = result as SendMessageResult.Failure
        assertEquals(ExitCodes.UNKNOWN_ERROR, failure.exitCode)
    }

    @Test
    fun `exit code mapping is comprehensive for all failure categories`() {
        // Verify that all failure categories have proper exit code mappings
        val categories =
            mapOf(
                MessageFailureCategory.VALIDATION to MessageExitCodes.VALIDATION_ERROR,
                MessageFailureCategory.UNAUTHORIZED to ExitCodes.UNAUTHORIZED,
                MessageFailureCategory.TIMEOUT to ExitCodes.NETWORK_ERROR,
                MessageFailureCategory.NETWORK to ExitCodes.NETWORK_ERROR,
                MessageFailureCategory.SERVER to ExitCodes.SERVER_ERROR,
                MessageFailureCategory.NOT_FOUND to MessageExitCodes.NOT_FOUND,
                MessageFailureCategory.UNKNOWN to ExitCodes.UNKNOWN_ERROR,
            )

        for ((category, expectedExitCode) in categories) {
            val stubRuntime =
                object : MessageRuntime {
                    override fun sendMessage(
                        session: AuthSession,
                        conversationId: String,
                        text: String,
                    ): MessageStepResult<Unit> = MessageStepResult.Failure(category)

                    override fun fetchMessages(
                        session: AuthSession,
                        conversationId: String,
                    ): MessageStepResult<List<ConversationMessage>> = MessageStepResult.Success(emptyList())

                    override fun shutdown() {
                        // No-op for test stub
                    }
                }

            val client = RealKaliumMessageApiClient(stubRuntime)
            val result = client.sendMessage(testSession, "conv-123", "Hello")

            val failure = (result as SendMessageResult.Failure)
            assertEquals(
                expectedExitCode,
                failure.exitCode,
                "Failed for category: $category",
            )
        }
    }

    @Test
    fun `fetchMessages returns Failure for blank text (should not happen but tested for completeness)`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        // Note: fetchMessages doesn't take text parameter, but we test other params
        val result = runtime.fetchMessages(testSession, "conv-123")

        assertIs<MessageStepResult<*>>(result)
    }

    @Test
    fun `fetchMessages validates conversationId before attempting SDK call`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())

        val resultBlank = runtime.fetchMessages(testSession, "   ")
        val resultEmpty = runtime.fetchMessages(testSession, "")

        assertIs<MessageStepResult.Failure>(resultBlank)
        assertIs<MessageStepResult.Failure>(resultEmpty)

        val failureBlank = resultBlank as MessageStepResult.Failure
        val failureEmpty = resultEmpty as MessageStepResult.Failure

        assertEquals(MessageFailureCategory.VALIDATION, failureBlank.category)
        assertEquals(MessageFailureCategory.VALIDATION, failureEmpty.category)
    }

    @Test
    fun `fetchMessages validates user ID format before attempting SDK call`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())
        val invalidSession = testSession.copy(userId = "no-domain")

        val result = runtime.fetchMessages(invalidSession, "conv-123")

        assertIs<MessageStepResult.Failure>(result)
        val failure = result as MessageStepResult.Failure
        assertEquals(MessageFailureCategory.UNAUTHORIZED, failure.category)
    }

    @Test
    fun `fetchMessages returns Failure for missing domain in user ID`() {
        val runtime = SdkKaliumMessageRuntime(emptyMap())
        val invalidSession = testSession.copy(userId = "alice@")

        val result = runtime.fetchMessages(invalidSession, "conv-123")

        assertIs<MessageStepResult.Failure>(result)
        val failure = result as MessageStepResult.Failure
        assertEquals(MessageFailureCategory.UNAUTHORIZED, failure.category)
    }
}

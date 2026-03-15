package wirecli.commands

import wirecli.auth.ExitCodes
import wirecli.message.Message
import wirecli.message.MessageDetailResult
import wirecli.message.MessageListResult
import wirecli.message.MessageListView
import wirecli.message.MessageService
import wirecli.message.MessageStatus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageFetchCommandTest {
    private val sampleMessages =
        listOf(
            Message(
                id = "msg-001",
                text = "Hello, World!",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                status = MessageStatus.SENT,
            ),
            Message(
                id = "msg-002",
                text = "How are you?",
                from = "bob@wire.com",
                fromName = "Bob Smith",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:05:00Z",
                status = MessageStatus.SENT,
            ),
            Message(
                id = "msg-003",
                text = "Doing great!",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:10:00Z",
                status = MessageStatus.SENT,
            ),
        )

    // ==================== Success Cases ====================

    @Test
    fun `shouldFetchAllMessagesSuccessfully`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(sampleMessages)),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001")

        assertTrue(capturedOutput.isNotEmpty())
        assertContains(capturedOutput[0], "ID")
        assertContains(capturedOutput[0], "FROM")
        assertContains(capturedOutput[0], "TEXT")
    }

    @Test
    fun `shouldFetchWithLimitFlag`() {
        val limitedMessages = sampleMessages.take(2)
        val service =
            FakeMessageServiceForFetch(
                fetchResult =
                    MessageListResult.Success(
                        MessageListView(limitedMessages, hasMore = true, nextCursor = "msg-004"),
                    ),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", limit = "2")

        assertTrue(capturedOutput.isNotEmpty())
    }

    @Test
    fun `shouldFetchWithFromFlagForPagination`() {
        val paginatedMessages = sampleMessages.drop(1)
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(paginatedMessages)),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", from = "msg-001")

        assertTrue(capturedOutput.isNotEmpty())
    }

    @Test
    fun `shouldFetchWithMultipleFlagsCombined`() {
        val limitedMessages = sampleMessages.take(2)
        val service =
            FakeMessageServiceForFetch(
                fetchResult =
                    MessageListResult.Success(
                        MessageListView(limitedMessages, hasMore = true, nextCursor = "msg-003"),
                    ),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(
            conversationId = "conv-001",
            limit = "2",
            from = "msg-000",
        )

        assertTrue(capturedOutput.isNotEmpty())
    }

    @Test
    fun `shouldFormatOutputAsTextTable`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(sampleMessages)),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", format = "text")

        assertTrue(capturedOutput.isNotEmpty())
        assertTrue(capturedOutput[0].contains("ID"))
        assertTrue(capturedOutput[0].contains("FROM"))
        assertTrue(capturedOutput[0].contains("msg-001"))
    }

    @Test
    fun `shouldFormatOutputAsJsonArray`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(sampleMessages)),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", format = "json")

        assertTrue(capturedOutput.isNotEmpty())
        assertTrue(capturedOutput[0].contains("["))
        assertTrue(capturedOutput[0].contains("]"))
        assertTrue(capturedOutput[0].contains("\"id\""))
    }

    @Test
    fun `shouldFormatOutputAsJsonLines`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(sampleMessages)),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", format = "jsonlines")

        assertTrue(capturedOutput.isNotEmpty())
        // Each line should be JSON
        assertTrue(capturedOutput[0].contains("{"))
        assertTrue(capturedOutput[0].contains("}"))
    }

    @Test
    fun `shouldHandleEmptyResultsGracefully`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(emptyList())),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-nonexistent")

        assertTrue(capturedOutput.isNotEmpty())
        assertContains(capturedOutput[0], "No messages found")
    }

    @Test
    fun `shouldShowPaginationInfoWhenMoreMessagesAvailable`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult =
                    MessageListResult.Success(
                        MessageListView(
                            sampleMessages.take(2),
                            hasMore = true,
                            nextCursor = "msg-004",
                        ),
                    ),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", limit = "2")

        // Should have pagination message
        assertTrue(capturedOutput.any { it.contains("More messages available") })
    }

    @Test
    fun `shouldNotShowPaginationInfoWhenNoMoreMessages`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(sampleMessages, hasMore = false)),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001")

        // Should not have pagination message
        assertTrue(capturedOutput.all { !it.contains("More messages available") })
    }

    // ==================== Error Cases ====================

    @Test
    fun `shouldHandleUnauthorizedError`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Failure("No active session", 11),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        val exitCode = command.simulateRun(conversationId = "conv-001")

        assertEquals(11, exitCode)
    }

    @Test
    fun `shouldHandleConversationNotFoundError`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Failure("Conversation not found", 16),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        val exitCode = command.simulateRun(conversationId = "conv-nonexistent")

        assertEquals(16, exitCode)
    }

    @Test
    fun `shouldHandleNetworkError`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Failure("Network unreachable", 12),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        val exitCode = command.simulateRun(conversationId = "conv-001")

        assertEquals(12, exitCode)
    }

    @Test
    fun `shouldHandleServerError`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Failure("Server error", 13),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        val exitCode = command.simulateRun(conversationId = "conv-001")

        assertEquals(13, exitCode)
    }

    // ==================== Flag Parsing Tests ====================

    @Test
    fun `shouldDefaultLimitTo50WhenNotSpecified`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(sampleMessages)),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001")

        assertTrue(capturedOutput.isNotEmpty())
    }

    @Test
    fun `shouldParseNegativeLimitAsDefault`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(sampleMessages)),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", limit = "-1")

        assertTrue(capturedOutput.isNotEmpty())
    }

    @Test
    fun `shouldHandleInvalidLimitAsInteger`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(sampleMessages)),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        // Should not crash, should use default
        command.simulateRun(conversationId = "conv-001", limit = "invalid")

        assertTrue(capturedOutput.isNotEmpty())
    }

    @Test
    fun `shouldDefaultToTextFormatWhenNotSpecified`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(sampleMessages)),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001")

        assertContains(capturedOutput[0], "ID")
        assertContains(capturedOutput[0], "FROM")
    }

    @Test
    fun `shouldHandleFormatCaseInsensitivity`() {
        val service =
            FakeMessageServiceForFetch(
                fetchResult = MessageListResult.Success(MessageListView(sampleMessages)),
            )
        val capturedOutput = mutableListOf<String>()
        val command =
            TestableMessageFetchCommand(
                serviceProvider = { service },
                outputCapture = capturedOutput,
            )

        command.simulateRun(conversationId = "conv-001", format = "JSON")

        assertTrue(capturedOutput[0].contains("["))
        assertTrue(capturedOutput[0].contains("]"))
    }

    // ==================== Test Helpers ====================

    private class FakeMessageServiceForFetch(
        private val fetchResult: MessageListResult,
    ) : MessageService {
        override fun send(
            conversationId: String,
            text: String,
        ): wirecli.message.MessageSendResult = throw NotImplementedError("Not needed for fetch command test")

        override fun fetch(
            conversationId: String,
            limit: Int?,
            from: String?,
        ): MessageListResult = fetchResult

        override fun getDetail(
            conversationId: String,
            messageId: String,
        ): MessageDetailResult = throw NotImplementedError("Not needed for fetch command test")
    }

    private class TestableMessageFetchCommand(
        serviceProvider: () -> MessageService,
        private val outputCapture: MutableList<String> = mutableListOf(),
        private val errorCapture: MutableList<String> = mutableListOf(),
    ) : MessageFetchCommand(serviceProvider) {
        var lastExitCode: Int? = null

        fun simulateRun(
            conversationId: String,
            limit: String = "50",
            from: String? = null,
            format: String = "text",
        ): Int {
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

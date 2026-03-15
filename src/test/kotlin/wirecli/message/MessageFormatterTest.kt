package wirecli.message

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageFormatterTest {
    private val formatter = MessageFormatter()

    private val sampleMessage =
        Message(
            id = "msg-001",
            text = "Hello, World!",
            from = "alice@wire.com",
            fromName = "Alice Johnson",
            conversationId = "conv-001",
            timestamp = "2025-03-15T10:00:00Z",
            status = MessageStatus.SENT,
        )

    private val messageWithSpecialChars =
        Message(
            id = "msg-002",
            text = "This has \"quotes\" and\nnewlines\ttabs",
            from = "bob@wire.com",
            fromName = "Bob \"The Dev\" Smith",
            conversationId = "conv-001",
            timestamp = "2025-03-15T10:05:00Z",
            status = MessageStatus.SENT,
        )

    private val messageWithReactionsAndMentions =
        Message(
            id = "msg-003",
            text = "Great work, @alice! @bob, please review",
            from = "charlie@wire.com",
            fromName = "Charlie Davis",
            conversationId = "conv-001",
            timestamp = "2025-03-15T10:10:00Z",
            status = MessageStatus.SENT,
            reactions = mapOf("👍" to 3, "❤️" to 1),
            mentions = listOf("alice@wire.com", "bob@wire.com"),
        )

    private val editedMessage =
        Message(
            id = "msg-004",
            text = "Updated message content",
            from = "alice@wire.com",
            fromName = "Alice Johnson",
            conversationId = "conv-001",
            timestamp = "2025-03-15T10:15:00Z",
            editedTimestamp = "2025-03-15T10:20:00Z",
            status = MessageStatus.SENT,
        )

    // ==================== Format Single Message Tests ====================

    @Test
    fun `shouldFormatSingleMessageAsTextByDefault`() {
        val output = formatter.formatSendResult(sampleMessage, "text")

        assertTrue(output.contains("ID:"))
        assertTrue(output.contains("From:"))
        assertTrue(output.contains("Text:"))
        assertTrue(output.contains("Status:"))
        assertTrue(output.contains("Timestamp:"))
        assertTrue(output.contains("Hello, World!"))
        assertTrue(output.contains("Alice Johnson"))
    }

    @Test
    fun `shouldFormatSingleMessageAsJson`() {
        val output = formatter.formatSendResult(sampleMessage, "json")

        assertTrue(output.contains("\"id\":\"msg-001\""))
        assertTrue(output.contains("\"text\":\"Hello, World!\""))
        assertTrue(output.contains("\"from\":\"alice@wire.com\""))
        assertTrue(output.contains("\"fromName\":\"Alice Johnson\""))
        assertTrue(output.contains("\"status\":\"sent\""))
        assertTrue(output.contains("\"timestamp\":\"2025-03-15T10:00:00Z\""))
    }

    @Test
    fun `shouldFormatSingleMessageAsJsonLines`() {
        val output = formatter.formatSendResult(sampleMessage, "jsonlines")

        assertTrue(output.contains("\"id\":\"msg-001\""))
        assertTrue(output.contains("\"text\":\"Hello, World!\""))
        assertFalse(output.contains("["))
        assertFalse(output.contains("]"))
    }

    @Test
    fun `shouldFormatTextWithSpecialCharactersEscapedInJson`() {
        val output = formatter.formatSendResult(messageWithSpecialChars, "json")

        assertTrue(output.contains("\\\"quotes\\\""))
        assertTrue(output.contains("newlines\\ttabs"))
    }

    @Test
    fun `shouldIncludeEditedTimestampWhenPresent`() {
        val output = formatter.formatSendResult(editedMessage, "text")

        assertTrue(output.contains("Edited:"))
        assertTrue(output.contains("2025-03-15T10:20:00Z"))
    }

    @Test
    fun `shouldOmitEditedTimestampWhenNotPresent`() {
        val output = formatter.formatSendResult(sampleMessage, "text")

        assertFalse(output.contains("Edited:"))
    }

    @Test
    fun `shouldIncludeReactionsInTextFormat`() {
        val output = formatter.formatSendResult(messageWithReactionsAndMentions, "text")

        assertTrue(output.contains("Reactions:"))
        assertTrue(output.contains("👍"))
        assertTrue(output.contains("❤️"))
    }

    @Test
    fun `shouldIncludeMentionsInTextFormat`() {
        val output = formatter.formatSendResult(messageWithReactionsAndMentions, "text")

        assertTrue(output.contains("Mentions:"))
        assertTrue(output.contains("alice@wire.com"))
        assertTrue(output.contains("bob@wire.com"))
    }

    @Test
    fun `shouldIncludeReactionsInJsonFormat`() {
        val output = formatter.formatSendResult(messageWithReactionsAndMentions, "json")

        assertTrue(output.contains("\"reactions\":{"))
        assertTrue(output.contains("\"👍\":3"))
        assertTrue(output.contains("\"❤️\":1"))
    }

    @Test
    fun `shouldIncludeMentionsInJsonFormat`() {
        val output = formatter.formatSendResult(messageWithReactionsAndMentions, "json")

        assertTrue(output.contains("\"mentions\":["))
        assertTrue(output.contains("\"alice@wire.com\""))
        assertTrue(output.contains("\"bob@wire.com\""))
    }

    // ==================== Format Message List Tests ====================

    @Test
    fun `shouldFormatMessageListAsTable`() {
        val messages = listOf(sampleMessage, messageWithSpecialChars)
        val output = formatter.formatMessageList(messages, "text")

        assertTrue(output.contains("ID"))
        assertTrue(output.contains("FROM"))
        assertTrue(output.contains("TEXT"))
        assertTrue(output.contains("TIMESTAMP"))
        assertTrue(output.contains("STATUS"))
        assertTrue(output.contains("-".repeat(10)))
        assertTrue(output.contains("msg-001"))
        assertTrue(output.contains("msg-002"))
    }

    @Test
    fun `shouldFormatEmptyListAsTable`() {
        val output = formatter.formatMessageList(emptyList(), "text")

        assertEquals("No messages found.", output)
    }

    @Test
    fun `shouldTruncateTextInTableFormatIntelligently`() {
        val longTextMessage =
            Message(
                id = "msg-long",
                text = "This is a very long message that should be truncated in the table format to avoid making the table too wide",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                status = MessageStatus.SENT,
            )
        val output = formatter.formatMessageList(listOf(longTextMessage), "text")

        assertTrue(output.contains(".."))
    }

    @Test
    fun `shouldFormatMessageListAsJson`() {
        val messages = listOf(sampleMessage, messageWithSpecialChars)
        val output = formatter.formatMessageList(messages, "json")

        assertTrue(output.startsWith("["))
        assertTrue(output.endsWith("]"))
        assertTrue(output.contains("\"id\":\"msg-001\""))
        assertTrue(output.contains("\"id\":\"msg-002\""))
        assertTrue(output.contains(","))
    }

    @Test
    fun `shouldFormatEmptyListAsJsonArray`() {
        val output = formatter.formatMessageList(emptyList(), "json")

        assertEquals("[]", output)
    }

    @Test
    fun `shouldFormatMessageListAsJsonLines`() {
        val messages = listOf(sampleMessage, messageWithSpecialChars)
        val output = formatter.formatMessageList(messages, "jsonlines")

        val lines = output.split("\n")
        assertEquals(2, lines.size)

        assertTrue(lines[0].contains("\"id\":\"msg-001\""))
        assertTrue(lines[1].contains("\"id\":\"msg-002\""))

        // Each line should be valid JSON object
        assertTrue(lines[0].startsWith("{"))
        assertTrue(lines[0].endsWith("}"))
        assertTrue(lines[1].startsWith("{"))
        assertTrue(lines[1].endsWith("}"))
    }

    @Test
    fun `shouldFormatEmptyListAsJsonLinesEmpty`() {
        val output = formatter.formatMessageList(emptyList(), "jsonlines")

        assertEquals("", output)
    }

    // ==================== Special Character Handling Tests ====================

    @Test
    fun `shouldEscapeBackslashesInJson`() {
        val messageWithBackslash =
            Message(
                id = "msg-bs",
                text = "Path: C:\\Users\\Alice",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                status = MessageStatus.SENT,
            )
        val output = formatter.formatSendResult(messageWithBackslash, "json")

        assertTrue(output.contains("C:\\\\Users\\\\Alice"))
    }

    @Test
    fun `shouldEscapeNewlinesInJson`() {
        val messageWithNewline =
            Message(
                id = "msg-nl",
                text = "Line 1\nLine 2",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                status = MessageStatus.SENT,
            )
        val output = formatter.formatSendResult(messageWithNewline, "json")

        assertTrue(output.contains("Line 1\\nLine 2"))
    }

    @Test
    fun `shouldEscapeTabsInJson`() {
        val messageWithTab =
            Message(
                id = "msg-tab",
                text = "Col1\tCol2",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                status = MessageStatus.SENT,
            )
        val output = formatter.formatSendResult(messageWithTab, "json")

        assertTrue(output.contains("Col1\\tCol2"))
    }

    @Test
    fun `shouldEscapeCarriageReturnsInJson`() {
        val messageWithCR =
            Message(
                id = "msg-cr",
                text = "Line1\rLine2",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                status = MessageStatus.SENT,
            )
        val output = formatter.formatSendResult(messageWithCR, "json")

        assertTrue(output.contains("Line1\\rLine2"))
    }

    @Test
    fun `shouldHandleUnicodeCharactersInJson`() {
        val messageWithUnicode =
            Message(
                id = "msg-unicode",
                text = "Hello 世界 🌍 مرحبا العالم",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                status = MessageStatus.SENT,
            )
        val output = formatter.formatSendResult(messageWithUnicode, "json")

        assertTrue(output.contains("Hello 世界 🌍 مرحبا العالم"))
    }

    @Test
    fun `shouldHandleEmptyReactionsMap`() {
        val messageNoReactions =
            Message(
                id = "msg-noreact",
                text = "No reactions",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                status = MessageStatus.SENT,
                reactions = emptyMap(),
            )
        val output = formatter.formatSendResult(messageNoReactions, "json")

        assertFalse(output.contains("\"reactions\""))
    }

    @Test
    fun `shouldHandleEmptyMentionsList`() {
        val messageNoMentions =
            Message(
                id = "msg-nomention",
                text = "No mentions",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                status = MessageStatus.SENT,
                mentions = emptyList(),
            )
        val output = formatter.formatSendResult(messageNoMentions, "json")

        assertFalse(output.contains("\"mentions\""))
    }

    // ==================== Format Case Insensitivity Tests ====================

    @Test
    fun `shouldHandleFormatInMixedCase`() {
        val outputJson = formatter.formatSendResult(sampleMessage, "JSON")
        val outputJsonLines = formatter.formatSendResult(sampleMessage, "JSONLINES")

        assertTrue(outputJson.contains("\"id\":\"msg-001\""))
        assertTrue(outputJsonLines.contains("\"id\":\"msg-001\""))
    }

    @Test
    fun `shouldDefaultToTextForUnknownFormat`() {
        val output = formatter.formatSendResult(sampleMessage, "unknown")

        assertTrue(output.contains("ID:"))
        assertTrue(output.contains("Hello, World!"))
    }

    // ==================== Timestamp Consistency Tests ====================

    @Test
    fun `shouldFormatTimestampsConsistently`() {
        val messages = listOf(sampleMessage, messageWithSpecialChars)
        val output = formatter.formatMessageList(messages, "text")

        assertTrue(output.contains("2025-03-15T10:00:00Z"))
        assertTrue(output.contains("2025-03-15T10:05:00Z"))
    }

    @Test
    fun `shouldPreserveTimestampFormatInJson`() {
        val output = formatter.formatSendResult(sampleMessage, "json")

        assertTrue(output.contains("\"timestamp\":\"2025-03-15T10:00:00Z\""))
    }

    // ==================== Message Status Tests ====================

    @Test
    fun `shouldFormatDifferentMessageStatuses`() {
        val sentMessage = sampleMessage.copy(status = MessageStatus.SENT)
        val pendingMessage = sampleMessage.copy(id = "msg-pend", status = MessageStatus.PENDING)
        val failedMessage = sampleMessage.copy(id = "msg-fail", status = MessageStatus.FAILED)

        val output = formatter.formatMessageList(listOf(sentMessage, pendingMessage, failedMessage), "text")

        assertTrue(output.contains("sent"))
        assertTrue(output.contains("pending"))
        assertTrue(output.contains("failed"))
    }

    @Test
    fun `shouldFormatMessageStatusAsStringInJson`() {
        val output = formatter.formatSendResult(sampleMessage, "json")

        assertTrue(output.contains("\"status\":\"sent\""))
    }
}

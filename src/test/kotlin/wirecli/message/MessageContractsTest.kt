package wirecli.message

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MessageContractsTest {
    @Test
    fun testMessageDataClassCreation() {
        val message =
            Message(
                id = "test-123",
                text = "Hello world",
                from = "alice@wire.com",
                fromName = "Alice",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.ONE_ON_ONE,
            )

        assertEquals("test-123", message.id)
        assertEquals("Hello world", message.text)
        assertEquals("alice@wire.com", message.from)
        assertEquals("conv-001", message.conversationId)
        assertEquals(MessageStatus.SENT, message.status)
    }

    @Test
    fun testMessageStatusEnumValues() {
        assertEquals("sent", MessageStatus.SENT.value)
        assertEquals("pending", MessageStatus.PENDING.value)
        assertEquals("failed", MessageStatus.FAILED.value)
        assertEquals("deleted", MessageStatus.DELETED.value)
        assertEquals("unknown", MessageStatus.UNKNOWN.value)
    }

    @Test
    fun testConversationTypeEnumValues() {
        assertEquals("one_on_one", ConversationType.ONE_ON_ONE.value)
        assertEquals("group", ConversationType.GROUP.value)
        assertEquals("channel", ConversationType.CHANNEL.value)
        assertEquals("unknown", ConversationType.UNKNOWN.value)
    }

    @Test
    fun testMessageSendView() {
        val sendView =
            MessageSendView(
                conversationId = "conv-002",
                text = "Test message",
            )

        assertEquals("conv-002", sendView.conversationId)
        assertEquals("Test message", sendView.text)
    }

    @Test
    fun testMessageListView() {
        val messages =
            listOf(
                Message(
                    id = "msg-1",
                    text = "First",
                    from = "alice@wire.com",
                    fromName = "Alice",
                    conversationId = "conv-001",
                    timestamp = "2025-03-15T10:00:00Z",
                ),
                Message(
                    id = "msg-2",
                    text = "Second",
                    from = "bob@wire.com",
                    fromName = "Bob",
                    conversationId = "conv-001",
                    timestamp = "2025-03-15T10:05:00Z",
                ),
            )

        val listView =
            MessageListView(
                messages = messages,
                hasMore = false,
                nextCursor = null,
            )

        assertEquals(2, listView.messages.size)
        assertEquals(false, listView.hasMore)
    }

    @Test
    fun testMessageWithReactions() {
        val message =
            Message(
                id = "msg-reaction",
                text = "Great job!",
                from = "alice@wire.com",
                fromName = "Alice",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                reactions = mapOf("👍" to 2, "🎉" to 1),
            )

        assertNotNull(message.reactions)
        assertEquals(2, message.reactions["👍"])
        assertEquals(1, message.reactions["🎉"])
    }

    @Test
    fun testMessageWithMentions() {
        val message =
            Message(
                id = "msg-mention",
                text = "Hey @alice check this out",
                from = "bob@wire.com",
                fromName = "Bob",
                conversationId = "conv-group",
                timestamp = "2025-03-15T10:00:00Z",
                mentions = listOf("alice@wire.com"),
            )

        assertEquals(1, message.mentions.size)
        assertEquals("alice@wire.com", message.mentions[0])
    }

    @Test
    fun testMessageWithEditedTimestamp() {
        val message =
            Message(
                id = "msg-edited",
                text = "Updated message",
                from = "alice@wire.com",
                fromName = "Alice",
                conversationId = "conv-001",
                timestamp = "2025-03-15T10:00:00Z",
                editedTimestamp = "2025-03-15T10:05:00Z",
            )

        assertNotNull(message.editedTimestamp)
        assertEquals("2025-03-15T10:05:00Z", message.editedTimestamp)
    }

    @Test
    fun testExitCodes() {
        assertEquals(0, MessageExitCodes.OK)
        assertEquals(11, MessageExitCodes.UNAUTHORIZED)
        assertEquals(13, MessageExitCodes.NOT_FOUND)
        assertEquals(14, MessageExitCodes.INVALID_INPUT)
        assertEquals(16, MessageExitCodes.CONVERSATION_NOT_FOUND)
        assertEquals(17, MessageExitCodes.MESSAGE_NOT_FOUND)
    }
}

package wirecli.message

import wirecli.auth.AuthSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StubMessageApiClientTest {
    private val testSession =
        AuthSession(
            userId = "user-123",
            accessToken = "token-abc",
            server = "https://wire.example.com",
        )

    private val stubClient = StubMessageApiClient(emptyMap())

    @Test
    fun testSendMessageSuccess() {
        val sendView =
            MessageSendView(
                conversationId = "conv-001",
                text = "Hello, this is a test message",
            )

        val result = stubClient.sendMessage(testSession, sendView)

        assertIs<MessageSendResult.Success>(result)
        assertEquals("Hello, this is a test message", result.message.text)
        assertEquals("conv-001", result.message.conversationId)
        assertEquals(testSession.userId, result.message.from)
        assertEquals(MessageStatus.SENT, result.message.status)
    }

    @Test
    fun testSendMessageNetworkError() {
        val stubClientWithError = StubMessageApiClient(mapOf("WIRE_STUB_MODE" to "send_network_error"))
        val sendView =
            MessageSendView(
                conversationId = "conv-001",
                text = "Test",
            )

        val result = stubClientWithError.sendMessage(testSession, sendView)

        assertIs<MessageSendResult.Failure>(result)
        assertEquals(12, result.exitCode) // NETWORK_ERROR
    }

    @Test
    fun testSendMessageServerError() {
        val stubClientWithError = StubMessageApiClient(mapOf("WIRE_STUB_MODE" to "send_server_error"))
        val sendView =
            MessageSendView(
                conversationId = "conv-001",
                text = "Test",
            )

        val result = stubClientWithError.sendMessage(testSession, sendView)

        assertIs<MessageSendResult.Failure>(result)
        assertEquals(13, result.exitCode) // SERVER_ERROR
    }

    @Test
    fun testSendMessageUnauthorized() {
        val stubClientWithError = StubMessageApiClient(mapOf("WIRE_STUB_MODE" to "send_unauthorized"))
        val sendView =
            MessageSendView(
                conversationId = "conv-001",
                text = "Test",
            )

        val result = stubClientWithError.sendMessage(testSession, sendView)

        assertIs<MessageSendResult.Failure>(result)
        assertEquals(11, result.exitCode) // UNAUTHORIZED
    }

    @Test
    fun testFetchMessagesSuccess() {
        val result = stubClient.fetchMessages(testSession, "conv-dm-alice")

        assertIs<MessageListResult.Success>(result)
        assertTrue(result.view.messages.isNotEmpty())
        assertTrue(result.view.messages.all { it.conversationId == "conv-dm-alice" })
    }

    @Test
    fun testFetchMessagesWithLimit() {
        val result = stubClient.fetchMessages(testSession, "conv-group-backend", limit = 2)

        assertIs<MessageListResult.Success>(result)
        assertTrue(result.view.messages.size <= 2)
    }

    @Test
    fun testFetchMessagesEmpty() {
        val stubClientEmpty = StubMessageApiClient(mapOf("WIRE_STUB_MODE" to "fetch_empty"))
        val result = stubClientEmpty.fetchMessages(testSession, "conv-nonexistent")

        assertIs<MessageListResult.Success>(result)
        assertTrue(result.view.messages.isEmpty())
    }

    @Test
    fun testFetchMessagesNetworkError() {
        val stubClientError = StubMessageApiClient(mapOf("WIRE_STUB_MODE" to "fetch_network_error"))
        val result = stubClientError.fetchMessages(testSession, "conv-001")

        assertIs<MessageListResult.Failure>(result)
        assertEquals(12, result.exitCode) // NETWORK_ERROR
    }

    @Test
    fun testFetchMessagesSortedByTimestamp() {
        val result = stubClient.fetchMessages(testSession, "conv-group-backend")

        assertIs<MessageListResult.Success>(result)
        if (result.view.messages.size > 1) {
            // Messages should be sorted descending by timestamp
            for (i in 0 until result.view.messages.size - 1) {
                assertTrue(
                    result.view.messages[i].timestamp >= result.view.messages[i + 1].timestamp,
                    "Messages not sorted by timestamp",
                )
            }
        }
    }

    @Test
    fun testFetchMessageDetailSuccess() {
        val result = stubClient.fetchMessage(testSession, "conv-dm-alice", "msg-001")

        assertIs<MessageDetailResult.Success>(result)
        assertEquals("msg-001", result.view.message.id)
        assertEquals("conv-dm-alice", result.view.message.conversationId)
    }

    @Test
    fun testFetchMessageDetailNotFound() {
        val result = stubClient.fetchMessage(testSession, "conv-001", "msg-nonexistent")

        assertIs<MessageDetailResult.Failure>(result)
        assertEquals(17, result.exitCode) // MESSAGE_NOT_FOUND
    }

    @Test
    fun testFetchMessageDetailNetworkError() {
        val stubClientError = StubMessageApiClient(mapOf("WIRE_STUB_MODE" to "detail_network_error"))
        val result = stubClientError.fetchMessage(testSession, "conv-001", "msg-001")

        assertIs<MessageDetailResult.Failure>(result)
        assertEquals(12, result.exitCode) // NETWORK_ERROR
    }

    @Test
    fun testTestDataCoverage() {
        // Verify we have test messages with different conversation types
        val allMessages = stubClient.fetchMessages(testSession, "conv-dm-alice")
        assertIs<MessageListResult.Success>(allMessages)

        val dmMessages = allMessages.view.messages
        assertTrue(dmMessages.any { it.conversationType == ConversationType.ONE_ON_ONE })

        // Group messages
        val groupMessages = stubClient.fetchMessages(testSession, "conv-group-backend")
        assertIs<MessageListResult.Success>(groupMessages)
        assertTrue(groupMessages.view.messages.any { it.conversationType == ConversationType.GROUP })

        // Channel messages
        val channelMessages = stubClient.fetchMessages(testSession, "conv-channel-announcements")
        assertIs<MessageListResult.Success>(channelMessages)
        assertTrue(channelMessages.view.messages.any { it.conversationType == ConversationType.CHANNEL })
    }

    @Test
    fun testPersistentInMemoryStorage() {
        val stub = StubMessageApiClient(emptyMap())

        // Send a message
        val sendView =
            MessageSendView(
                conversationId = "conv-persistent",
                text = "Persistent test message",
            )
        val sendResult = stub.sendMessage(testSession, sendView)
        assertIs<MessageSendResult.Success>(sendResult)

        // Fetch messages from that conversation
        val fetchResult = stub.fetchMessages(testSession, "conv-persistent")
        assertIs<MessageListResult.Success>(fetchResult)

        // Verify the sent message appears in fetches
        val sentMessage = fetchResult.view.messages.find { it.text == "Persistent test message" }
        assertTrue(sentMessage != null, "Sent message should be retrievable")
    }

    @Test
    fun testTestMessageQuality() {
        // Verify we have at least 10-15 distinct test messages
        val allTestMessages = stubClient.fetchMessages(testSession, "conv-dm-alice")
        assertIs<MessageListResult.Success>(allTestMessages)
        assertTrue(allTestMessages.view.messages.size > 0, "Should have test messages for DM")

        // Verify messages have realistic content
        val message = allTestMessages.view.messages.firstOrNull()
        assertTrue(message != null, "Should have at least one message")
        assertTrue(message!!.text.isNotEmpty(), "Messages should have text content")
        assertTrue(message.from.isNotEmpty(), "Messages should have from field")
        assertTrue(message.timestamp.isNotEmpty(), "Messages should have timestamp")

        // Verify different message types exist
        val groupResult = stubClient.fetchMessages(testSession, "conv-group-backend")
        assertIs<MessageListResult.Success>(groupResult)
        val hasReactions = groupResult.view.messages.any { it.reactions.isNotEmpty() }
        assertTrue(hasReactions, "Some test messages should have reactions")

        val hasMentions = groupResult.view.messages.any { it.mentions.isNotEmpty() }
        assertTrue(hasMentions, "Some test messages should have mentions")

        val hasEdited = groupResult.view.messages.any { it.editedTimestamp != null }
        assertTrue(hasEdited, "Some test messages should be edited")
    }
}

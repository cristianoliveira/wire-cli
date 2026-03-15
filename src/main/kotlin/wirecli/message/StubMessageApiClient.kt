package wirecli.message

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

class StubMessageApiClient(
    private val environment: Map<String, String>,
) : MessageApiClient {
    // In-memory storage for sent messages (simulates API persistence during test)
    private val sentMessages = mutableListOf<Message>()

    // Pre-defined test messages covering different conversation types and scenarios
    private val defaultMessages =
        listOf(
            // DM messages with Alice
            Message(
                id = "msg-001",
                text = "Hey! How are you doing?",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-dm-alice",
                timestamp = "2025-03-15T09:30:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.ONE_ON_ONE,
                reactions = mapOf("👋" to 1),
            ),
            Message(
                id = "msg-002",
                text = "I'm doing great! Just finished the feature. Want to review it?",
                from = "user@wire.com",
                fromName = "You",
                conversationId = "conv-dm-alice",
                timestamp = "2025-03-15T09:35:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.ONE_ON_ONE,
            ),
            Message(
                id = "msg-003",
                text = "Sure! I'll take a look right now. Running tests first?",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-dm-alice",
                timestamp = "2025-03-15T09:36:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.ONE_ON_ONE,
            ),
            // DM messages with Bob
            Message(
                id = "msg-004",
                text = "Meeting at 3 PM today?",
                from = "bob@wire.com",
                fromName = "Bob Smith",
                conversationId = "conv-dm-bob",
                timestamp = "2025-03-15T10:00:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.ONE_ON_ONE,
            ),
            Message(
                id = "msg-005",
                text = "Yes, conference room B. See you there!",
                from = "user@wire.com",
                fromName = "You",
                conversationId = "conv-dm-bob",
                timestamp = "2025-03-15T10:02:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.ONE_ON_ONE,
            ),
            // Group chat messages
            Message(
                id = "msg-006",
                text = "Morning team! Here's the status update:",
                from = "charlie@wire.com",
                fromName = "Charlie Davis",
                conversationId = "conv-group-backend",
                timestamp = "2025-03-15T08:00:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.GROUP,
                mentions = listOf("alice@wire.com", "bob@wire.com"),
            ),
            Message(
                id = "msg-007",
                text = "API endpoints are deployed and tests passing ✅",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-group-backend",
                timestamp = "2025-03-15T08:15:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.GROUP,
            ),
            Message(
                id = "msg-008",
                text = "Database migration completed without issues",
                from = "bob@wire.com",
                fromName = "Bob Smith",
                conversationId = "conv-group-backend",
                timestamp = "2025-03-15T08:20:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.GROUP,
            ),
            Message(
                id = "msg-009",
                text = "Frontend integration ready for testing @alice",
                from = "user@wire.com",
                fromName = "You",
                conversationId = "conv-group-backend",
                timestamp = "2025-03-15T08:45:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.GROUP,
                mentions = listOf("alice@wire.com"),
                reactions = mapOf("👍" to 2, "🚀" to 1),
            ),
            Message(
                id = "msg-010",
                text = "Great! Let's schedule testing for tomorrow afternoon",
                from = "charlie@wire.com",
                fromName = "Charlie Davis",
                conversationId = "conv-group-backend",
                timestamp = "2025-03-15T09:00:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.GROUP,
            ),
            // Channel messages
            Message(
                id = "msg-011",
                text = "📣 Product release v2.0 is now available for download",
                from = "admin@wire.com",
                fromName = "Admin",
                conversationId = "conv-channel-announcements",
                timestamp = "2025-03-14T16:00:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.CHANNEL,
                reactions = mapOf("🎉" to 5, "👏" to 3),
            ),
            Message(
                id = "msg-012",
                text = "Check the changelog at https://example.com/changelog",
                from = "admin@wire.com",
                fromName = "Admin",
                conversationId = "conv-channel-announcements",
                timestamp = "2025-03-14T16:05:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.CHANNEL,
            ),
            Message(
                id = "msg-013",
                text = "Feedback thread: What features would you like to see next?",
                from = "admin@wire.com",
                fromName = "Admin",
                conversationId = "conv-channel-announcements",
                timestamp = "2025-03-14T17:00:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.CHANNEL,
            ),
            // Additional varied messages
            Message(
                id = "msg-014",
                text = "This message was edited",
                from = "user@wire.com",
                fromName = "You",
                conversationId = "conv-dm-alice",
                timestamp = "2025-03-15T07:30:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.ONE_ON_ONE,
                editedTimestamp = "2025-03-15T07:35:00Z",
            ),
            Message(
                id = "msg-015",
                text = "Document review: Check section 3.2 for details",
                from = "alice@wire.com",
                fromName = "Alice Johnson",
                conversationId = "conv-group-backend",
                timestamp = "2025-03-15T09:15:00Z",
                status = MessageStatus.SENT,
                conversationType = ConversationType.GROUP,
                mentions = listOf("user@wire.com", "bob@wire.com"),
            ),
        )

    override fun sendMessage(
        session: AuthSession,
        view: MessageSendView,
    ): MessageSendResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "send_network_error" ->
                MessageSendResult.Failure(
                    message = MessageMessages.SEND_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "send_server_error" ->
                MessageSendResult.Failure(
                    message = MessageMessages.SEND_SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "send_unauthorized" ->
                MessageSendResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            "send_conversation_not_found" ->
                MessageSendResult.Failure(
                    message = MessageMessages.CONVERSATION_NOT_FOUND,
                    exitCode = MessageExitCodes.CONVERSATION_NOT_FOUND,
                )

            "send_invalid_input" ->
                MessageSendResult.Failure(
                    message = MessageMessages.INVALID_INPUT,
                    exitCode = MessageExitCodes.INVALID_INPUT,
                )

            else -> {
                // Create and store new message
                val newMessage =
                    Message(
                        id = "msg-generated-${System.nanoTime()}",
                        text = view.text,
                        from = session.userId,
                        fromName = "You",
                        conversationId = view.conversationId,
                        timestamp =
                            System.currentTimeMillis().let {
                                java.time.Instant.ofEpochMilli(it).toString()
                            },
                        status = MessageStatus.SENT,
                        conversationType = ConversationType.ONE_ON_ONE,
                    )
                sentMessages.add(newMessage)
                MessageSendResult.Success(message = newMessage)
            }
        }
    }

    override fun fetchMessages(
        session: AuthSession,
        conversationId: String,
        limit: Int?,
        from: String?,
    ): MessageListResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "fetch_network_error" ->
                MessageListResult.Failure(
                    message = MessageMessages.NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "fetch_server_error" ->
                MessageListResult.Failure(
                    message = MessageMessages.SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "fetch_unauthorized" ->
                MessageListResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            "fetch_conversation_not_found" ->
                MessageListResult.Failure(
                    message = MessageMessages.CONVERSATION_NOT_FOUND,
                    exitCode = MessageExitCodes.CONVERSATION_NOT_FOUND,
                )

            "fetch_empty" ->
                MessageListResult.Success(
                    view = MessageListView(messages = emptyList()),
                )

            else -> {
                // Filter messages by conversationId
                var filtered =
                    (defaultMessages + sentMessages)
                        .filter { it.conversationId == conversationId }
                        .sortedByDescending { it.timestamp }

                // Apply pagination
                val effectiveLimit = limit ?: 50
                val messages = filtered.take(effectiveLimit)
                val hasMore = filtered.size > effectiveLimit

                MessageListResult.Success(
                    view =
                        MessageListView(
                            messages = messages,
                            hasMore = hasMore,
                            nextCursor = if (hasMore) messages.lastOrNull()?.id else null,
                        ),
                )
            }
        }
    }

    override fun fetchMessage(
        session: AuthSession,
        conversationId: String,
        messageId: String,
    ): MessageDetailResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "detail_network_error" ->
                MessageDetailResult.Failure(
                    message = MessageMessages.NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "detail_server_error" ->
                MessageDetailResult.Failure(
                    message = MessageMessages.SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "detail_unauthorized" ->
                MessageDetailResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            "detail_not_found" ->
                MessageDetailResult.Failure(
                    message = MessageMessages.MESSAGE_NOT_FOUND,
                    exitCode = MessageExitCodes.MESSAGE_NOT_FOUND,
                )

            else -> {
                val message =
                    (defaultMessages + sentMessages)
                        .find { it.id == messageId && it.conversationId == conversationId }
                        ?: return MessageDetailResult.Failure(
                            message = MessageMessages.MESSAGE_NOT_FOUND,
                            exitCode = MessageExitCodes.MESSAGE_NOT_FOUND,
                        )

                MessageDetailResult.Success(
                    view = MessageDetailView(message = message),
                )
            }
        }
    }
}

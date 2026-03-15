package wirecli.message

import kotlinx.datetime.Instant
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

/**
 * Real API client implementation using Kalium SDK for message operations.
 *
 * Delegates message operations to the Kalium backend through [RealKaliumMessageRuntime],
 * handling error mapping and result transformation to wire-cli domain models.
 *
 * @invariant runtime is never null and properly initialized
 * @invariant All public methods return non-null Result types
 * @invariant Error handling converts Kalium exceptions to appropriate exit codes
 */
internal class RealKaliumMessageApiClient(
    private val runtime: RealKaliumMessageRuntime,
) : MessageApiClient {
    /**
     * Sends a text message to a conversation.
     *
     * @param session The authenticated session for the current user
     * @param view The message send request containing conversation ID and text
     * @return MessageSendResult with sent message or error details
     * @throws Nothing - All errors wrapped in MessageSendResult
     *
     * @pre session must be valid and authenticated
     * @pre view.text must not be empty
     * @pre view.conversationId must reference a valid conversation
     * @post Result is either Success with created Message or Failure with error code
     */
    override fun sendMessage(
        session: AuthSession,
        view: MessageSendView,
    ): MessageSendResult {
        // Validate input
        if (view.text.isBlank()) {
            return MessageSendResult.Failure(
                message = MessageMessages.INVALID_INPUT,
                exitCode = MessageExitCodes.INVALID_INPUT,
            )
        }

        return when (val result = runtime.sendMessage(session, view.conversationId, view.text)) {
            is MessageStepResult.Success -> {
                MessageSendResult.Success(
                    message = mapKaliumMessageToDomain(result.value),
                )
            }

            is MessageStepResult.Failure -> result.toMessageSendFailure()
        }
    }

    /**
     * Fetches messages from a conversation with pagination support.
     *
     * @param session The authenticated session for the current user
     * @param conversationId The ID of the conversation to fetch messages from
     * @param limit The maximum number of messages to fetch (default: 50)
     * @param from Optional cursor for pagination (not currently used in implementation)
     * @return MessageListResult with list of messages or error details
     * @throws Nothing - All errors wrapped in MessageListResult
     *
     * @pre session must be valid and authenticated
     * @pre conversationId must reference a valid conversation
     * @pre limit must be positive if provided
     * @post Result is either Success with Message list or Failure with error code
     * @post Success result contains zero or more Message objects
     */
    override fun fetchMessages(
        session: AuthSession,
        conversationId: String,
        limit: Int?,
        from: String?,
    ): MessageListResult {
        val effectiveLimit = limit ?: 50
        if (effectiveLimit <= 0) {
            return MessageListResult.Failure(
                message = MessageMessages.INVALID_INPUT,
                exitCode = MessageExitCodes.INVALID_INPUT,
            )
        }

        return when (val result = runtime.fetchMessages(session, conversationId, effectiveLimit, from)) {
            is MessageStepResult.Success -> {
                val messages = result.value.map { mapKaliumMessageToDomain(it) }
                MessageListResult.Success(
                    view =
                        MessageListView(
                            messages = messages,
                            hasMore = false, // Pagination info not available from current API
                            nextCursor = null,
                        ),
                )
            }

            is MessageStepResult.Failure -> result.toMessageListFailure()
        }
    }

    /**
     * Fetches a single message from a conversation.
     *
     * @param session The authenticated session for the current user
     * @param conversationId The ID of the conversation containing the message
     * @param messageId The ID of the message to fetch
     * @return MessageDetailResult with message details or error information
     * @throws Nothing - All errors wrapped in MessageDetailResult
     *
     * @pre session must be valid and authenticated
     * @pre conversationId and messageId must reference valid objects
     * @post Result is either Success with Message details or Failure with error code
     * @post Success result contains a single Message object
     */
    override fun fetchMessage(
        session: AuthSession,
        conversationId: String,
        messageId: String,
    ): MessageDetailResult {
        return when (val result = runtime.fetchMessage(session, conversationId, messageId)) {
            is MessageStepResult.Success -> {
                MessageDetailResult.Success(
                    view =
                        MessageDetailView(
                            message = mapKaliumMessageToDomain(result.value),
                        ),
                )
            }

            is MessageStepResult.Failure -> result.toMessageDetailFailure()
        }
    }

    // ============ Helper Functions ============

    /**
     * Maps Kalium Message to wire-cli domain Message model.
     * Extracts essential message properties for CLI presentation.
     */
    private fun mapKaliumMessageToDomain(kaliumMessage: com.wire.kalium.logic.data.message.Message): Message {
        return Message(
            id = kaliumMessage.id,
            text = mapKaliumMessageContent(kaliumMessage.content),
            from = kaliumMessage.senderUserId.value,
            fromName = kaliumMessage.senderName ?: kaliumMessage.senderUserId.value,
            conversationId = kaliumMessage.conversationId.value,
            timestamp = mapInstantToString(kaliumMessage.date),
            status = mapKaliumMessageStatus(kaliumMessage.status),
            conversationType = ConversationType.ONE_ON_ONE, // Simplified; could be enhanced
            editedTimestamp = kaliumMessage.editTimestamp?.let { mapInstantToString(it) },
            reactions = emptyMap(), // Reactions not included in basic message view
            mentions = emptyList(), // Mentions could be extracted from content if needed
        )
    }

    /**
     * Extracts text content from Kalium message content types.
     * Handles Text and TextEdited content types; returns empty for other types.
     */
    private fun mapKaliumMessageContent(content: com.wire.kalium.logic.data.message.MessageContent): String {
        return when (content) {
            is com.wire.kalium.logic.data.message.MessageContent.Text -> content.value
            is com.wire.kalium.logic.data.message.MessageContent.TextEdited -> content.newContent
            else -> "[${content::class.simpleName}]" // Non-text content indicator
        }
    }

    /**
     * Maps Kalium message status to wire-cli MessageStatus.
     */
    private fun mapKaliumMessageStatus(status: com.wire.kalium.logic.data.message.Message.Status): MessageStatus {
        return when (status) {
            com.wire.kalium.logic.data.message.Message.Status.SENT -> MessageStatus.SENT
            com.wire.kalium.logic.data.message.Message.Status.PENDING -> MessageStatus.PENDING
            com.wire.kalium.logic.data.message.Message.Status.FAILED -> MessageStatus.FAILED
            else -> MessageStatus.UNKNOWN
        }
    }

    /**
     * Converts Instant to ISO 8601 string for consistency with other services.
     */
    private fun mapInstantToString(instant: Instant): String {
        return instant.toString()
    }
}

// ============ Error Mapping Extensions ============

internal fun MessageStepResult.Failure.toMessageSendFailure(): MessageSendResult.Failure {
    return MessageSendResult.Failure(
        message = categoryToSendMessage(this.category),
        exitCode = categoryToExitCode(this.category),
    )
}

internal fun MessageStepResult.Failure.toMessageListFailure(): MessageListResult.Failure {
    return MessageListResult.Failure(
        message = categoryToFetchMessage(this.category),
        exitCode = categoryToExitCode(this.category),
    )
}

internal fun MessageStepResult.Failure.toMessageDetailFailure(): MessageDetailResult.Failure {
    return MessageDetailResult.Failure(
        message = categoryToFetchMessage(this.category),
        exitCode = categoryToExitCode(this.category),
    )
}

private fun categoryToExitCode(category: MessageFailureCategory): Int {
    return when (category) {
        MessageFailureCategory.UNAUTHORIZED -> MessageExitCodes.UNAUTHORIZED
        MessageFailureCategory.NOT_FOUND -> MessageExitCodes.MESSAGE_NOT_FOUND
        MessageFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
        MessageFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
        MessageFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
    }
}

private fun categoryToSendMessage(category: MessageFailureCategory): String {
    return when (category) {
        MessageFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
        MessageFailureCategory.NOT_FOUND -> MessageMessages.CONVERSATION_NOT_FOUND
        MessageFailureCategory.NETWORK -> MessageMessages.SEND_NETWORK_FAILURE
        MessageFailureCategory.SERVER -> MessageMessages.SEND_SERVER_FAILURE
        MessageFailureCategory.UNKNOWN -> MessageMessages.SEND_UNKNOWN_FAILURE
    }
}

private fun categoryToFetchMessage(category: MessageFailureCategory): String {
    return when (category) {
        MessageFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
        MessageFailureCategory.NOT_FOUND -> MessageMessages.MESSAGE_NOT_FOUND
        MessageFailureCategory.NETWORK -> MessageMessages.NETWORK_FAILURE
        MessageFailureCategory.SERVER -> MessageMessages.SERVER_FAILURE
        MessageFailureCategory.UNKNOWN -> MessageMessages.UNKNOWN_FAILURE
    }
}

package wirecli.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import kotlinx.datetime.Instant
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import com.wire.kalium.logic.data.conversation.Conversation.Type as KaliumConversationType

/**
 * Real API client implementation using Kalium SDK for conversation operations
 */
internal class RealKaliumConversationApiClient(
    private val runtime: RealKaliumConversationRuntime,
) : ConversationApiClient {
    override fun listConversations(session: AuthSession): ListConversationsResult {
        return when (val result = runtime.listConversations(session)) {
            is ConversationStepResult.Success ->
                ListConversationsResult.Success(
                    ConversationListView(
                        conversations = result.value.map { mapConversationDetailsToDomain(it) },
                    ),
                )

            is ConversationStepResult.Failure -> result.toListConversationsFailure()
        }
    }

    override fun getConversation(
        session: AuthSession,
        conversationId: String,
    ): GetConversationResult {
        return when (val result = runtime.getConversation(session, conversationId)) {
            is ConversationStepResult.Success ->
                GetConversationResult.Success(
                    ConversationDetailView(
                        mapConversationDetailsToDomain(result.value),
                    ),
                )

            is ConversationStepResult.Failure -> result.toGetConversationFailure()
        }
    }

    override fun createConversation(
        session: AuthSession,
        name: String,
        type: ConversationType,
    ): CreateConversationResult {
        // Note: Full creation requires more complex Kalium operations
        // For now, return not implemented
        return CreateConversationResult.Failure(
            message = "Conversation creation not yet implemented with real backend",
            exitCode = ExitCodes.SERVER_ERROR,
        )
    }

    override fun deleteConversation(
        session: AuthSession,
        conversationId: String,
    ): DeleteConversationResult {
        // Note: Full deletion requires more complex Kalium operations
        // For now, return not implemented
        return DeleteConversationResult.Failure(
            message = "Conversation deletion not yet implemented with real backend",
            exitCode = ExitCodes.SERVER_ERROR,
        )
    }

    override fun getMemberCount(
        session: AuthSession,
        conversationId: String,
    ): GetConversationResult {
        return getConversation(session, conversationId)
    }

    // ============ Helper Functions ============

    /**
     * Maps Kalium ConversationDetails to domain Conversation model.
     * Handles 1-on-1 conversations by extracting actual contact names
     * instead of using "[Direct Message]" placeholder.
     */
    private fun mapConversationDetailsToDomain(details: ConversationDetails): Conversation {
        return when (details) {
            // 1-on-1 conversations with actual contact info
            is ConversationDetails.OneOne -> {
                Conversation(
                    id = details.conversation.id.value,
                    name =
                        details.otherUser.name
                            ?: details.otherUser.handle
                            ?: details.otherUser.id.value, // Fallback: name → handle → user ID
                    type = ConversationType.ONE_TO_ONE,
                    status = mapKaliumConversationStatus(details.conversation.archived),
                    memberCount = 2,
                    createdAt = mapTimestamp(details.conversation.lastModifiedDate),
                    updatedAt = mapTimestamp(details.conversation.lastModifiedDate),
                )
            }

            // Pending connection requests
            is ConversationDetails.Connection -> {
                Conversation(
                    id = details.conversationId.value,
                    name =
                        details.otherUser?.name
                            ?: details.otherUser?.handle
                            ?: "[Connection Pending]",
                    type = ConversationType.ONE_TO_ONE,
                    status = ConversationStatus.ACTIVE,
                    memberCount = 2,
                    createdAt = mapTimestamp(details.lastModifiedDate),
                    updatedAt = mapTimestamp(details.lastModifiedDate),
                )
            }

            // Group conversations with their names
            is ConversationDetails.Group -> {
                Conversation(
                    id = details.conversation.id.value,
                    name = details.conversation.name ?: "[Group]",
                    type = mapKaliumConversationType(details.conversation.type),
                    status = mapKaliumConversationStatus(details.conversation.archived),
                    memberCount = 0, // Member count not available in ConversationDetails, can be improved later
                    createdAt = mapTimestamp(details.conversation.lastModifiedDate),
                    updatedAt = mapTimestamp(details.conversation.lastModifiedDate),
                )
            }

            // Team conversations
            is ConversationDetails.Team -> {
                Conversation(
                    id = details.conversation.id.value,
                    name = details.conversation.name ?: "[Team]",
                    type = mapKaliumConversationType(details.conversation.type),
                    status = mapKaliumConversationStatus(details.conversation.archived),
                    memberCount = 0,
                    createdAt = mapTimestamp(details.conversation.lastModifiedDate),
                    updatedAt = mapTimestamp(details.conversation.lastModifiedDate),
                )
            }

            // Self conversation
            is ConversationDetails.Self -> {
                Conversation(
                    id = details.conversation.id.value,
                    name = details.conversation.name ?: "[Saved Messages]",
                    type = mapKaliumConversationType(details.conversation.type),
                    status = mapKaliumConversationStatus(details.conversation.archived),
                    memberCount = 1,
                    createdAt = mapTimestamp(details.conversation.lastModifiedDate),
                    updatedAt = mapTimestamp(details.conversation.lastModifiedDate),
                )
            }
        }
    }

    private fun mapTimestamp(instant: Instant?): String {
        return if (instant != null) {
            instant.toString() // Already ISO 8601 format
        } else {
            "Unknown"
        }
    }

    private fun mapKaliumConversationType(type: KaliumConversationType): ConversationType {
        return when (type) {
            KaliumConversationType.Self -> ConversationType.ONE_TO_ONE
            KaliumConversationType.OneOnOne -> ConversationType.ONE_TO_ONE
            KaliumConversationType.ConnectionPending -> ConversationType.ONE_TO_ONE
            is KaliumConversationType.Group -> ConversationType.GROUP
        }
    }

    private fun mapKaliumConversationStatus(archived: Boolean): ConversationStatus {
        return if (archived) ConversationStatus.ARCHIVED else ConversationStatus.ACTIVE
    }
}

// Extension functions for error handling
private fun ConversationStepResult.Failure.toListConversationsFailure(): ListConversationsResult.Failure {
    return ListConversationsResult.Failure(
        message =
            when (this.category) {
                ConversationFailureCategory.UNAUTHORIZED ->
                    AuthMessages.invalidOrExpiredSession()

                ConversationFailureCategory.NETWORK ->
                    ConversationMessages.NETWORK_FAILURE

                ConversationFailureCategory.NOT_FOUND ->
                    ConversationMessages.CONVERSATION_NOT_FOUND

                else ->
                    ConversationMessages.SERVER_FAILURE
            },
        exitCode =
            when (this.category) {
                ConversationFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
                ConversationFailureCategory.NETWORK -> ConversationExitCodes.NETWORK_ERROR
                ConversationFailureCategory.NOT_FOUND -> ConversationExitCodes.NOT_FOUND
                else -> ConversationExitCodes.SERVER_ERROR
            },
    )
}

private fun ConversationStepResult.Failure.toGetConversationFailure(): GetConversationResult.Failure {
    return GetConversationResult.Failure(
        message =
            when (this.category) {
                ConversationFailureCategory.UNAUTHORIZED ->
                    AuthMessages.invalidOrExpiredSession()

                ConversationFailureCategory.NETWORK ->
                    ConversationMessages.NETWORK_FAILURE

                ConversationFailureCategory.NOT_FOUND ->
                    ConversationMessages.CONVERSATION_NOT_FOUND

                else ->
                    ConversationMessages.SERVER_FAILURE
            },
        exitCode =
            when (this.category) {
                ConversationFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
                ConversationFailureCategory.NETWORK -> ConversationExitCodes.NETWORK_ERROR
                ConversationFailureCategory.NOT_FOUND -> ConversationExitCodes.NOT_FOUND
                else -> ConversationExitCodes.SERVER_ERROR
            },
    )
}

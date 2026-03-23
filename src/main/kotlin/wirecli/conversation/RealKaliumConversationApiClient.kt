package wirecli.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.shared.ConversationError
import wirecli.shared.Result
import com.wire.kalium.logic.data.conversation.Conversation.Type as KaliumConversationType

private val logger = KotlinLogging.logger {}

/**
 * Real API client implementation using Kalium SDK for conversation operations
 */
internal class RealKaliumConversationApiClient(
    private val runtime: RealKaliumConversationRuntime,
) : ConversationApiClient {
    override fun listConversations(session: AuthSession): ListConversationsResult {
        logger.info { "Listing conversations for current user" }
        logger.debug { "API call: GET /conversations (list conversations)" }

        return when (val result = runtime.listConversations(session)) {
            is ConversationStepResult.Success -> {
                logger.info { "Successfully retrieved ${result.value.size} conversation(s)" }
                logger.debug { "Conversation types: ${result.value.map { it::class.simpleName }.groupingBy { it }.eachCount()}" }
                Result.Success(
                    value =
                        ConversationListView(
                            conversations = result.value.map { mapConversationDetailsToDomain(it) },
                        ),
                )
            }

            is ConversationStepResult.Failure -> {
                logger.warn { "Failed to list conversations: ${result.category}" }
                result.toListConversationsFailure()
            }
        }
    }

    override fun getConversation(
        session: AuthSession,
        conversationId: String,
    ): GetConversationResult {
        logger.info { "Retrieving conversation: $conversationId" }
        logger.debug { "API call: GET /conversations/$conversationId" }

        return when (val result = runtime.getConversation(session, conversationId)) {
            is ConversationStepResult.Success -> {
                logger.info { "Successfully retrieved conversation: $conversationId" }
                logger.debug { "Conversation type: ${result.value::class.simpleName}" }
                Result.Success(
                    value =
                        ConversationDetailView(
                            mapConversationDetailsToDomain(result.value),
                        ),
                )
            }

            is ConversationStepResult.Failure -> {
                logger.warn { "Failed to retrieve conversation $conversationId: ${result.category}" }
                result.toGetConversationFailure()
            }
        }
    }

    override fun createConversation(
        session: AuthSession,
        name: String,
        type: ConversationType,
    ): CreateConversationResult {
        logger.info { "Creating conversation: name=$name, type=$type" }
        logger.debug { "API call: POST /conversations (not yet implemented with real backend)" }

        // Note: Full creation requires more complex Kalium operations
        // For now, return not implemented
        return Result.Failure(
            error =
                ConversationError(
                    message = "Conversation creation not yet implemented with real backend",
                    exitCode = ExitCodes.SERVER_ERROR,
                ),
        ).also { logger.warn { "Conversation creation not yet implemented with real backend" } }
    }

    override fun deleteConversation(
        session: AuthSession,
        conversationId: String,
    ): DeleteConversationResult {
        logger.info { "Deleting conversation: $conversationId" }
        logger.debug { "API call: DELETE /conversations/$conversationId (not yet implemented with real backend)" }

        // Note: Full deletion requires more complex Kalium operations
        // For now, return not implemented
        return Result.Failure(
            error =
                ConversationError(
                    message = "Conversation deletion not yet implemented with real backend",
                    exitCode = ExitCodes.SERVER_ERROR,
                ),
        ).also { logger.warn { "Conversation deletion not yet implemented with real backend" } }
    }

    override fun getMemberCount(
        session: AuthSession,
        conversationId: String,
    ): GetConversationResult {
        logger.debug { "Getting member count for conversation: $conversationId" }
        logger.debug { "API call: GET /conversations/$conversationId (member count)" }
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
private fun ConversationStepResult.Failure.toListConversationsFailure(): Result.Failure<ConversationError> {
    return Result.Failure(
        error =
            ConversationError(
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
            ),
    )
}

private fun ConversationStepResult.Failure.toGetConversationFailure(): Result.Failure<ConversationError> {
    return Result.Failure(
        error =
            ConversationError(
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
            ),
    )
}

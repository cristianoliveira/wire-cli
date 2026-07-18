package wirecli.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.MemberDetails
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import com.wire.kalium.logic.data.conversation.Conversation.Type as KaliumConversationType

private val logger = KotlinLogging.logger {}

/**
 * Real API client implementation using Kalium SDK for conversation operations
 */
internal class RealKaliumConversationApiClient(
    private val runtime: ConversationRuntime,
) : ConversationApiClient {
    override fun listConversations(session: AuthSession): ListConversationsResult {
        logger.info { "Listing conversations for current user" }
        logger.debug { "API call: GET /conversations (list conversations)" }

        return when (val result = runtime.listConversations(session)) {
            is ConversationStepResult.Success -> {
                logger.info { "Successfully retrieved ${result.value.size} conversation(s)" }
                logger.debug { "Conversation types: ${result.value.map { it::class.simpleName }.groupingBy { it }.eachCount()}" }
                ListConversationsResult.Success(
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
                GetConversationResult.Success(
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
        return CreateConversationResult.Failure(
            message = "Conversation creation not yet implemented with real backend",
            exitCode = ExitCodes.SERVER_ERROR,
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
        return DeleteConversationResult.Failure(
            message = "Conversation deletion not yet implemented with real backend",
            exitCode = ExitCodes.SERVER_ERROR,
        ).also { logger.warn { "Conversation deletion not yet implemented with real backend" } }
    }

    override fun getMembers(
        session: AuthSession,
        conversationId: String,
    ): GetMembersResult {
        logger.info { "Getting members for conversation: $conversationId" }
        logger.debug { "API call: GET /conversations/$conversationId/members" }

        return when (val result = runtime.getMembers(session, conversationId)) {
            is ConversationStepResult.Success -> {
                val members = mapMembersInfoToDomain(result.value)
                logger.info { "Successfully retrieved ${members.size} member(s) for conversation: $conversationId" }
                GetMembersResult.Success(
                    MemberListView(members = members),
                )
            }

            is ConversationStepResult.Failure -> {
                logger.warn { "Failed to get members for conversation $conversationId: ${result.category}" }
                result.toGetMembersFailure()
            }
        }
    }

    override fun getMemberCount(
        session: AuthSession,
        conversationId: String,
    ): GetConversationResult {
        logger.debug { "Getting member count for conversation: $conversationId" }
        logger.debug { "API call: GET /conversations/$conversationId (member count)" }
        return getConversation(session, conversationId)
    }

    // ============ Domain Mapping ============

    /**
     * Maps Kalium MemberDetails list to domain Member list.
     */
    private fun mapMembersInfoToDomain(details: List<MemberDetails>): List<Member> =
        details.map { detail ->
            Member(
                id = detail.user.id.toLogString(),
                name = detail.user.name ?: detail.user.id.value,
                handle = detail.user.handle,
                role = mapKaliumMemberRole(detail.role),
            )
        }

    private fun mapKaliumMemberRole(role: com.wire.kalium.logic.data.conversation.Conversation.Member.Role): MemberRole =
        when (role) {
            is com.wire.kalium.logic.data.conversation.Conversation.Member.Role.Admin -> MemberRole.ADMIN
            is com.wire.kalium.logic.data.conversation.Conversation.Member.Role.Member -> MemberRole.MEMBER
            is com.wire.kalium.logic.data.conversation.Conversation.Member.Role.Unknown -> MemberRole.MEMBER
        }

    // ============ Helper Functions ============

    /**
     * Maps Kalium ConversationDetails to domain Conversation model.
     * Delegates to specialized mappers based on conversation type.
     */
    private fun mapConversationDetailsToDomain(details: ConversationDetails): Conversation =
        when (details) {
            is ConversationDetails.OneOne -> mapOneOnOneConversation(details)
            is ConversationDetails.Connection -> mapConnectionConversation(details)
            is ConversationDetails.Group -> mapGroupConversation(details)
            is ConversationDetails.Team -> mapTeamConversation(details)
            is ConversationDetails.Self -> mapSelfConversation(details)
        }

    /**
     * Maps 1-on-1 conversation details by extracting actual contact names
     * instead of using "[Direct Message]" placeholder.
     */
    private fun mapOneOnOneConversation(details: ConversationDetails.OneOne): Conversation =
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

    /**
     * Maps pending connection request details.
     */
    private fun mapConnectionConversation(details: ConversationDetails.Connection): Conversation =
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

    /**
     * Maps group conversation details.
     */
    private fun mapGroupConversation(details: ConversationDetails.Group): Conversation =
        Conversation(
            id = details.conversation.id.value,
            name = details.conversation.name ?: "[Group]",
            type = mapKaliumConversationType(details.conversation.type),
            status = mapKaliumConversationStatus(details.conversation.archived),
            memberCount = 0, // Member count not available in ConversationDetails, can be improved later
            createdAt = mapTimestamp(details.conversation.lastModifiedDate),
            updatedAt = mapTimestamp(details.conversation.lastModifiedDate),
        )

    /**
     * Maps team conversation details.
     */
    private fun mapTeamConversation(details: ConversationDetails.Team): Conversation =
        Conversation(
            id = details.conversation.id.value,
            name = details.conversation.name ?: "[Team]",
            type = mapKaliumConversationType(details.conversation.type),
            status = mapKaliumConversationStatus(details.conversation.archived),
            memberCount = 0,
            createdAt = mapTimestamp(details.conversation.lastModifiedDate),
            updatedAt = mapTimestamp(details.conversation.lastModifiedDate),
        )

    /**
     * Maps self conversation (saved messages) details.
     */
    private fun mapSelfConversation(details: ConversationDetails.Self): Conversation =
        Conversation(
            id = details.conversation.id.value,
            name = details.conversation.name ?: "[Saved Messages]",
            type = mapKaliumConversationType(details.conversation.type),
            status = mapKaliumConversationStatus(details.conversation.archived),
            memberCount = 1,
            createdAt = mapTimestamp(details.conversation.lastModifiedDate),
            updatedAt = mapTimestamp(details.conversation.lastModifiedDate),
        )

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

private fun ConversationStepResult.Failure.toGetMembersFailure(): GetMembersResult.Failure {
    return GetMembersResult.Failure(
        message =
            when (this.category) {
                ConversationFailureCategory.UNAUTHORIZED ->
                    AuthMessages.invalidOrExpiredSession()

                ConversationFailureCategory.NETWORK ->
                    ConversationMessages.MEMBERS_NETWORK_FAILURE

                ConversationFailureCategory.NOT_FOUND ->
                    ConversationMessages.CONVERSATION_NOT_FOUND

                else ->
                    ConversationMessages.MEMBERS_SERVER_FAILURE
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

package wirecli.conversation

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.shared.ConversationError
import wirecli.shared.Result

class StubConversationApiClient(
    private val environment: Map<String, String>,
) : ConversationApiClient {
    // Test data: 1:1 conversations (DMs)
    private val dmConversations =
        listOf(
            Conversation(
                id = "conv-dm-001",
                name = "alice@example.com",
                type = ConversationType.ONE_TO_ONE,
                status = ConversationStatus.ACTIVE,
                memberCount = 2,
                createdAt = "2025-01-10T08:00:00Z",
                updatedAt = "2025-03-13T15:30:00Z",
            ),
            Conversation(
                id = "conv-dm-002",
                name = "bob@example.com",
                type = ConversationType.ONE_TO_ONE,
                status = ConversationStatus.ACTIVE,
                memberCount = 2,
                createdAt = "2025-02-05T10:15:00Z",
                updatedAt = "2025-03-12T09:45:00Z",
            ),
        )

    // Test data: group conversations
    private val groupConversations =
        listOf(
            Conversation(
                id = "conv-group-001",
                name = "Team Collaboration",
                type = ConversationType.GROUP,
                status = ConversationStatus.ACTIVE,
                memberCount = 8,
                createdAt = "2024-12-20T14:00:00Z",
                updatedAt = "2025-03-13T16:20:00Z",
            ),
            Conversation(
                id = "conv-group-002",
                name = "Project Roadmap",
                type = ConversationType.GROUP,
                status = ConversationStatus.ACTIVE,
                memberCount = 5,
                createdAt = "2025-01-15T09:30:00Z",
                updatedAt = "2025-03-11T13:00:00Z",
            ),
            Conversation(
                id = "conv-group-003",
                name = "Design Reviews",
                type = ConversationType.GROUP,
                status = ConversationStatus.ARCHIVED,
                memberCount = 4,
                createdAt = "2024-11-01T11:00:00Z",
                updatedAt = "2025-02-28T17:45:00Z",
            ),
        )

    // Test data: team/channel conversations
    private val channelConversations =
        listOf(
            Conversation(
                id = "conv-channel-001",
                name = "engineering",
                type = ConversationType.TEAM_CHANNEL,
                status = ConversationStatus.ACTIVE,
                memberCount = 12,
                createdAt = "2024-09-15T08:00:00Z",
                updatedAt = "2025-03-13T14:15:00Z",
            ),
            Conversation(
                id = "conv-channel-002",
                name = "marketing",
                type = ConversationType.TEAM_CHANNEL,
                status = ConversationStatus.ACTIVE,
                memberCount = 7,
                createdAt = "2024-10-01T10:00:00Z",
                updatedAt = "2025-03-10T11:30:00Z",
            ),
            Conversation(
                id = "conv-channel-003",
                name = "general",
                type = ConversationType.TEAM_CHANNEL,
                status = ConversationStatus.ACTIVE,
                memberCount = 25,
                createdAt = "2024-09-01T09:00:00Z",
                updatedAt = "2025-03-13T15:00:00Z",
            ),
        )

    // All conversations combined for full list
    private val allConversations =
        dmConversations + groupConversations + channelConversations

    override fun listConversations(session: AuthSession): ListConversationsResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "list_empty" ->
                Result.Success(
                    value = ConversationListView(conversations = emptyList()),
                )

            "list_ok" ->
                Result.Success(
                    value = ConversationListView(conversations = allConversations),
                )

            "list_dm_only" ->
                Result.Success(
                    value = ConversationListView(conversations = dmConversations),
                )

            "list_groups_only" ->
                Result.Success(
                    value = ConversationListView(conversations = groupConversations),
                )

            "list_channels_only" ->
                Result.Success(
                    value = ConversationListView(conversations = channelConversations),
                )

            "not_found" ->
                Result.Failure(
                    error = ConversationError(
                        message = ConversationMessages.CONVERSATION_NOT_FOUND,
                        exitCode = ConversationExitCodes.NOT_FOUND,
                    ),
                )

            "server_error" ->
                Result.Failure(
                    error = ConversationError(
                        message = ConversationMessages.SERVER_FAILURE,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            "unauthorized" ->
                Result.Failure(
                    error = ConversationError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            else ->
                Result.Success(
                    value = ConversationListView(conversations = allConversations),
                )
        }
    }

    override fun getConversation(
        session: AuthSession,
        conversationId: String,
    ): GetConversationResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "not_found" ->
                Result.Failure(
                    error = ConversationError(
                        message = ConversationMessages.CONVERSATION_NOT_FOUND,
                        exitCode = ConversationExitCodes.NOT_FOUND,
                    ),
                )

            "server_error" ->
                Result.Failure(
                    error = ConversationError(
                        message = ConversationMessages.SERVER_FAILURE,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            "unauthorized" ->
                Result.Failure(
                    error = ConversationError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            else -> {
                val conversation =
                    allConversations.find { it.id == conversationId }
                        ?: return Result.Failure(
                            error = ConversationError(
                                message = ConversationMessages.CONVERSATION_NOT_FOUND,
                                exitCode = ConversationExitCodes.NOT_FOUND,
                            ),
                        )

                Result.Success(
                    view = ConversationDetailView(conversation = conversation),
                )
            }
        }
    }

    override fun createConversation(
        session: AuthSession,
        name: String,
        type: ConversationType,
    ): CreateConversationResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "invalid_input" ->
                Result.Failure(
                    error = ConversationError(
                        message = ConversationMessages.INVALID_INPUT,
                        exitCode = ConversationExitCodes.INVALID_INPUT,
                    ),
                )

            "server_error" ->
                Result.Failure(
                    error = ConversationError(
                        message = ConversationMessages.CREATE_SERVER_FAILURE,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            "unauthorized" ->
                Result.Failure(
                    error = ConversationError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            else -> {
                val newConversation =
                    Conversation(
                        id = "conv-new-${System.currentTimeMillis()}",
                        name = name,
                        type = type,
                        status = ConversationStatus.ACTIVE,
                        memberCount = 1,
                        createdAt = "2025-03-14T16:00:00Z",
                        updatedAt = "2025-03-14T16:00:00Z",
                    )

                Result.Success(
                    view = ConversationDetailView(conversation = newConversation),
                )
            }
        }
    }

    override fun deleteConversation(
        session: AuthSession,
        conversationId: String,
    ): DeleteConversationResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "not_found" ->
                Result.Failure(
                    error = ConversationError(
                        message = ConversationMessages.CONVERSATION_NOT_FOUND,
                        exitCode = ConversationExitCodes.NOT_FOUND,
                    ),
                )

            "server_error" ->
                Result.Failure(
                    error = ConversationError(
                        message = ConversationMessages.DELETE_SERVER_FAILURE,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            "unauthorized" ->
                Result.Failure(
                    error = ConversationError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            else ->
                Result.Success(
                    message = "Conversation deleted successfully.",
                )
        }
    }

    override fun getMemberCount(
        session: AuthSession,
        conversationId: String,
    ): GetConversationResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "not_found" ->
                Result.Failure(
                    error = ConversationError(
                        message = ConversationMessages.CONVERSATION_NOT_FOUND,
                        exitCode = ConversationExitCodes.NOT_FOUND,
                    ),
                )

            "server_error" ->
                Result.Failure(
                    error = ConversationError(
                        message = ConversationMessages.SERVER_FAILURE,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            "unauthorized" ->
                Result.Failure(
                    error = ConversationError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            else -> {
                val conversation =
                    allConversations.find { it.id == conversationId }
                        ?: return Result.Failure(
                            error = ConversationError(
                                message = ConversationMessages.CONVERSATION_NOT_FOUND,
                                exitCode = ConversationExitCodes.NOT_FOUND,
                            ),
                        )

                Result.Success(
                    view = ConversationDetailView(conversation = conversation),
                )
            }
        }
    }
}

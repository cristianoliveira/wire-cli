package wirecli.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConversationContractsTest {
    @Test
    fun `creates conversation with all properties`() {
        val conversation =
            Conversation(
                id = "conv-123",
                name = "Test Conversation",
                type = ConversationType.GROUP,
                status = ConversationStatus.ACTIVE,
                memberCount = 5,
                createdAt = "2025-03-13T10:00:00Z",
                updatedAt = "2025-03-13T15:00:00Z",
            )

        assertEquals("conv-123", conversation.id)
        assertEquals("Test Conversation", conversation.name)
        assertEquals(ConversationType.GROUP, conversation.type)
        assertEquals(ConversationStatus.ACTIVE, conversation.status)
        assertEquals(5, conversation.memberCount)
    }

    @Test
    fun `conversation equality based on properties`() {
        val conv1 =
            Conversation(
                id = "conv-123",
                name = "Test",
                type = ConversationType.ONE_TO_ONE,
                status = ConversationStatus.ACTIVE,
                memberCount = 2,
                createdAt = "2025-03-13T10:00:00Z",
                updatedAt = "2025-03-13T15:00:00Z",
            )

        val conv2 =
            Conversation(
                id = "conv-123",
                name = "Test",
                type = ConversationType.ONE_TO_ONE,
                status = ConversationStatus.ACTIVE,
                memberCount = 2,
                createdAt = "2025-03-13T10:00:00Z",
                updatedAt = "2025-03-13T15:00:00Z",
            )

        assertEquals(conv1, conv2)
    }

    @Test
    fun `conversation types have correct string values`() {
        assertEquals("one_to_one", ConversationType.ONE_TO_ONE.value)
        assertEquals("group", ConversationType.GROUP.value)
        assertEquals("team_channel", ConversationType.TEAM_CHANNEL.value)
    }

    @Test
    fun `conversation statuses have correct string values`() {
        assertEquals("active", ConversationStatus.ACTIVE.value)
        assertEquals("archived", ConversationStatus.ARCHIVED.value)
        assertEquals("deleted", ConversationStatus.DELETED.value)
    }

    @Test
    fun `creates list conversations success result`() {
        val conversations =
            listOf(
                Conversation(
                    id = "conv-1",
                    name = "Conv 1",
                    type = ConversationType.ONE_TO_ONE,
                    status = ConversationStatus.ACTIVE,
                    memberCount = 2,
                    createdAt = "2025-03-13T10:00:00Z",
                    updatedAt = "2025-03-13T15:00:00Z",
                ),
            )

        val result = ListConversationsResult.Success(view = ConversationListView(conversations = conversations))

        assertIs<ListConversationsResult.Success>(result)
        assertEquals(1, result.view.conversations.size)
        assertEquals("conv-1", result.view.conversations[0].id)
    }

    @Test
    fun `creates list conversations failure result`() {
        val result =
            ListConversationsResult.Failure(
                message = "Network error",
                exitCode = 12,
            )

        assertIs<ListConversationsResult.Failure>(result)
        assertEquals("Network error", result.message)
        assertEquals(12, result.exitCode)
    }

    @Test
    fun `creates get conversation success result`() {
        val conversation =
            Conversation(
                id = "conv-123",
                name = "Test",
                type = ConversationType.GROUP,
                status = ConversationStatus.ACTIVE,
                memberCount = 5,
                createdAt = "2025-03-13T10:00:00Z",
                updatedAt = "2025-03-13T15:00:00Z",
            )

        val result = GetConversationResult.Success(view = ConversationDetailView(conversation = conversation))

        assertIs<GetConversationResult.Success>(result)
        assertEquals("conv-123", result.view.conversation.id)
    }

    @Test
    fun `creates get conversation failure result`() {
        val result =
            GetConversationResult.Failure(
                message = "Conversation not found",
                exitCode = 13,
            )

        assertIs<GetConversationResult.Failure>(result)
        assertEquals("Conversation not found", result.message)
        assertEquals(13, result.exitCode)
    }

    @Test
    fun `creates create conversation success result`() {
        val conversation =
            Conversation(
                id = "conv-new",
                name = "New Conversation",
                type = ConversationType.GROUP,
                status = ConversationStatus.ACTIVE,
                memberCount = 1,
                createdAt = "2025-03-14T10:00:00Z",
                updatedAt = "2025-03-14T10:00:00Z",
            )

        val result = CreateConversationResult.Success(view = ConversationDetailView(conversation = conversation))

        assertIs<CreateConversationResult.Success>(result)
        assertEquals("New Conversation", result.view.conversation.name)
    }

    @Test
    fun `creates create conversation failure result`() {
        val result =
            CreateConversationResult.Failure(
                message = "Invalid input",
                exitCode = 14,
            )

        assertIs<CreateConversationResult.Failure>(result)
        assertEquals("Invalid input", result.message)
    }

    @Test
    fun `creates delete conversation success result`() {
        val result = DeleteConversationResult.Success(message = "Deleted successfully")

        assertIs<DeleteConversationResult.Success>(result)
        assertEquals("Deleted successfully", result.message)
    }

    @Test
    fun `creates delete conversation failure result`() {
        val result =
            DeleteConversationResult.Failure(
                message = "Cannot delete",
                exitCode = 13,
            )

        assertIs<DeleteConversationResult.Failure>(result)
        assertEquals("Cannot delete", result.message)
    }

    @Test
    fun `exit codes have correct values`() {
        assertEquals(0, ConversationExitCodes.OK)
        assertEquals(11, ConversationExitCodes.UNAUTHORIZED)
        assertEquals(13, ConversationExitCodes.NOT_FOUND)
        assertEquals(14, ConversationExitCodes.INVALID_INPUT)
        assertEquals(12, ConversationExitCodes.NETWORK_ERROR)
    }

    @Test
    fun `conversation messages are non-empty`() {
        assertEquals(
            "Conversation not found. Check conversation ID and try again.",
            ConversationMessages.CONVERSATION_NOT_FOUND,
        )
        assertEquals(
            "Conversation fetch failed: network is unreachable. Check your connection and retry.",
            ConversationMessages.NETWORK_FAILURE,
        )
        assertEquals(
            "Conversation service is unavailable. Retry later or check server settings.",
            ConversationMessages.SERVER_FAILURE,
        )
    }
}

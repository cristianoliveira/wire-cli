package wirecli.conversation

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StubConversationApiClientTest {
    private val session =
        AuthSession(
            userId = "user@example.com",
            accessToken = "token",
            server = null,
        )

    @Test
    fun `returns list of all conversations by default`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.listConversations(session)

        val success = assertIs<ListConversationsResult.Success>(result)
        assertEquals(8, success.view.conversations.size)
    }

    @Test
    fun `returns conversations with all conversation types`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.listConversations(session)

        val success = assertIs<ListConversationsResult.Success>(result)
        val conversations = success.view.conversations

        val hasDm = conversations.any { it.type == ConversationType.ONE_TO_ONE }
        val hasGroup = conversations.any { it.type == ConversationType.GROUP }
        val hasChannel = conversations.any { it.type == ConversationType.TEAM_CHANNEL }

        assertEquals(true, hasDm, "Should have at least one DM conversation")
        assertEquals(true, hasGroup, "Should have at least one group conversation")
        assertEquals(true, hasChannel, "Should have at least one channel conversation")
    }

    @Test
    fun `returns dm conversations with correct properties`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.listConversations(session)

        val success = assertIs<ListConversationsResult.Success>(result)
        val dmConversations = success.view.conversations.filter { it.type == ConversationType.ONE_TO_ONE }

        assertEquals(2, dmConversations.size)
        assertEquals("alice@example.com", dmConversations[0].name)
        assertEquals(2, dmConversations[0].memberCount)
        assertEquals(ConversationStatus.ACTIVE, dmConversations[0].status)
    }

    @Test
    fun `returns group conversations with correct properties`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.listConversations(session)

        val success = assertIs<ListConversationsResult.Success>(result)
        val groupConversations = success.view.conversations.filter { it.type == ConversationType.GROUP }

        assertEquals(3, groupConversations.size)
        assertEquals("Team Collaboration", groupConversations[0].name)
        assertEquals(8, groupConversations[0].memberCount)
    }

    @Test
    fun `returns channel conversations with correct properties`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.listConversations(session)

        val success = assertIs<ListConversationsResult.Success>(result)
        val channelConversations = success.view.conversations.filter { it.type == ConversationType.TEAM_CHANNEL }

        assertEquals(3, channelConversations.size)
        assertEquals("engineering", channelConversations[0].name)
        assertEquals(12, channelConversations[0].memberCount)
    }

    @Test
    fun `returns empty list in list_empty mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "list_empty"))

        val result = client.listConversations(session)

        val success = assertIs<ListConversationsResult.Success>(result)
        assertEquals(0, success.view.conversations.size)
    }

    @Test
    fun `returns all conversations in list_ok mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "list_ok"))

        val result = client.listConversations(session)

        val success = assertIs<ListConversationsResult.Success>(result)
        assertEquals(8, success.view.conversations.size)
    }

    @Test
    fun `returns only dm conversations in list_dm_only mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "list_dm_only"))

        val result = client.listConversations(session)

        val success = assertIs<ListConversationsResult.Success>(result)
        assertEquals(2, success.view.conversations.size)
        assertEquals(true, success.view.conversations.all { it.type == ConversationType.ONE_TO_ONE })
    }

    @Test
    fun `returns only group conversations in list_groups_only mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "list_groups_only"))

        val result = client.listConversations(session)

        val success = assertIs<ListConversationsResult.Success>(result)
        assertEquals(3, success.view.conversations.size)
        assertEquals(true, success.view.conversations.all { it.type == ConversationType.GROUP })
    }

    @Test
    fun `returns only channel conversations in list_channels_only mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "list_channels_only"))

        val result = client.listConversations(session)

        val success = assertIs<ListConversationsResult.Success>(result)
        assertEquals(3, success.view.conversations.size)
        assertEquals(true, success.view.conversations.all { it.type == ConversationType.TEAM_CHANNEL })
    }

    @Test
    fun `returns not found failure in not_found mode for list`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "not_found"))

        val result = client.listConversations(session)

        val failure = assertIs<ListConversationsResult.Failure>(result)
        assertEquals(ConversationMessages.CONVERSATION_NOT_FOUND, failure.message)
        assertEquals(ConversationExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns server error failure in server_error mode for list`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))

        val result = client.listConversations(session)

        val failure = assertIs<ListConversationsResult.Failure>(result)
        assertEquals(ConversationMessages.SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized failure in unauthorized mode for list`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "unauthorized"))

        val result = client.listConversations(session)

        val failure = assertIs<ListConversationsResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns conversation detail by default`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.getConversation(session, "conv-dm-001")

        val success = assertIs<GetConversationResult.Success>(result)
        assertEquals("conv-dm-001", success.view.conversation.id)
        assertEquals("alice@example.com", success.view.conversation.name)
        assertEquals(ConversationType.ONE_TO_ONE, success.view.conversation.type)
    }

    @Test
    fun `returns not found for missing conversation`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.getConversation(session, "conv-nonexistent")

        val failure = assertIs<GetConversationResult.Failure>(result)
        assertEquals(ConversationMessages.CONVERSATION_NOT_FOUND, failure.message)
        assertEquals(ConversationExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns not found failure for detail in not_found mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "not_found"))

        val result = client.getConversation(session, "conv-dm-001")

        val failure = assertIs<GetConversationResult.Failure>(result)
        assertEquals(ConversationMessages.CONVERSATION_NOT_FOUND, failure.message)
        assertEquals(ConversationExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns server error failure for detail in server_error mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))

        val result = client.getConversation(session, "conv-dm-001")

        val failure = assertIs<GetConversationResult.Failure>(result)
        assertEquals(ConversationMessages.SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized failure for detail in unauthorized mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "unauthorized"))

        val result = client.getConversation(session, "conv-dm-001")

        val failure = assertIs<GetConversationResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `creates new conversation successfully by default`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.createConversation(session, "New Team", ConversationType.GROUP)

        val success = assertIs<CreateConversationResult.Success>(result)
        assertEquals("New Team", success.view.conversation.name)
        assertEquals(ConversationType.GROUP, success.view.conversation.type)
        assertEquals(ConversationStatus.ACTIVE, success.view.conversation.status)
        assertEquals(1, success.view.conversation.memberCount)
    }

    @Test
    fun `returns invalid input failure in invalid_input mode for create`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "invalid_input"))

        val result = client.createConversation(session, "", ConversationType.GROUP)

        val failure = assertIs<CreateConversationResult.Failure>(result)
        assertEquals(ConversationMessages.INVALID_INPUT, failure.message)
        assertEquals(ConversationExitCodes.INVALID_INPUT, failure.exitCode)
    }

    @Test
    fun `returns server error failure in server_error mode for create`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))

        val result = client.createConversation(session, "New Team", ConversationType.GROUP)

        val failure = assertIs<CreateConversationResult.Failure>(result)
        assertEquals(ConversationMessages.CREATE_SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized failure in unauthorized mode for create`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "unauthorized"))

        val result = client.createConversation(session, "New Team", ConversationType.GROUP)

        val failure = assertIs<CreateConversationResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `deletes conversation successfully by default`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.deleteConversation(session, "conv-dm-001")

        val success = assertIs<DeleteConversationResult.Success>(result)
        assertEquals("Conversation deleted successfully.", success.message)
    }

    @Test
    fun `returns not found failure for delete in not_found mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "not_found"))

        val result = client.deleteConversation(session, "conv-nonexistent")

        val failure = assertIs<DeleteConversationResult.Failure>(result)
        assertEquals(ConversationMessages.CONVERSATION_NOT_FOUND, failure.message)
        assertEquals(ConversationExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns server error failure for delete in server_error mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))

        val result = client.deleteConversation(session, "conv-dm-001")

        val failure = assertIs<DeleteConversationResult.Failure>(result)
        assertEquals(ConversationMessages.DELETE_SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized failure for delete in unauthorized mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "unauthorized"))

        val result = client.deleteConversation(session, "conv-dm-001")

        val failure = assertIs<DeleteConversationResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `gets member count for conversation by default`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.getMemberCount(session, "conv-group-001")

        val success = assertIs<GetConversationResult.Success>(result)
        assertEquals("conv-group-001", success.view.conversation.id)
        assertEquals(8, success.view.conversation.memberCount)
    }

    @Test
    fun `returns not found for missing conversation member count`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.getMemberCount(session, "conv-nonexistent")

        val failure = assertIs<GetConversationResult.Failure>(result)
        assertEquals(ConversationMessages.CONVERSATION_NOT_FOUND, failure.message)
        assertEquals(ConversationExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns not found failure for member count in not_found mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "not_found"))

        val result = client.getMemberCount(session, "conv-group-001")

        val failure = assertIs<GetConversationResult.Failure>(result)
        assertEquals(ConversationMessages.CONVERSATION_NOT_FOUND, failure.message)
        assertEquals(ConversationExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `returns server error failure for member count in server_error mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))

        val result = client.getMemberCount(session, "conv-group-001")

        val failure = assertIs<GetConversationResult.Failure>(result)
        assertEquals(ConversationMessages.SERVER_FAILURE, failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized failure for member count in unauthorized mode`() {
        val client = StubConversationApiClient(mapOf("WIRE_STUB_MODE" to "unauthorized"))

        val result = client.getMemberCount(session, "conv-group-001")

        val failure = assertIs<GetConversationResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `archived conversation is in test data`() {
        val client = StubConversationApiClient(emptyMap())

        val result = client.listConversations(session)

        val success = assertIs<ListConversationsResult.Success>(result)
        val archivedConv = success.view.conversations.find { it.status == ConversationStatus.ARCHIVED }

        assertEquals(true, archivedConv != null, "Should have at least one archived conversation")
        assertEquals("Design Reviews", archivedConv?.name)
    }
}

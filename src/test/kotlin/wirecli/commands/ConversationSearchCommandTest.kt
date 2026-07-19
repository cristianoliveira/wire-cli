package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.conversation.Conversation
import wirecli.conversation.ConversationDetailView
import wirecli.conversation.ConversationListView
import wirecli.conversation.ConversationService
import wirecli.conversation.ConversationStatus
import wirecli.conversation.ConversationType
import wirecli.conversation.CreateConversationResult
import wirecli.conversation.DeleteConversationResult
import wirecli.conversation.GetConversationResult
import wirecli.conversation.ListConversationsResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConversationSearchCommandTest {
    @Test
    fun `search filters channels by name case insensitively`() {
        val service =
            StubConversationService(
                listOf(
                    conversation("1", "Engineering", ConversationType.TEAM_CHANNEL),
                    conversation("2", "General", ConversationType.TEAM_CHANNEL),
                    conversation("3", "Engineering DM", ConversationType.ONE_TO_ONE),
                ),
            )
        val output = execute(ConversationSearchCommand { service }, listOf("engineer", "--type", "channel"))

        assertTrue(output.contains("Engineering"))
        assertFalse(output.contains("General"))
        assertFalse(output.contains("Engineering DM"))
    }

    @Test
    fun `list rejects unsupported filter before calling service`() {
        val service = CountingConversationService()

        val failure = runCatching { ConversationListCommand { service }.parse(listOf("--filter-type", "bogus")) }.exceptionOrNull()

        assertEquals(2, assertIs<ProgramResult>(failure).statusCode)
        assertEquals(0, service.listCalls)
    }

    @Test
    fun `list rejects unsupported sort before calling service`() {
        val service = CountingConversationService()

        val failure = runCatching { ConversationListCommand { service }.parse(listOf("--sort-by", "bogus")) }.exceptionOrNull()

        assertEquals(2, assertIs<ProgramResult>(failure).statusCode)
        assertEquals(0, service.listCalls)
    }

    @Test
    fun `search rejects unsupported conversation type`() {
        val result =
            runCatching {
                execute(
                    ConversationSearchCommand { StubConversationService(emptyList()) },
                    listOf("x", "--type", "unknown"),
                )
            }

        assertTrue(result.isFailure)
    }

    private fun execute(
        command: ConversationSearchCommand,
        args: List<String>,
    ): String {
        val output = java.io.ByteArrayOutputStream()
        val original = System.out
        try {
            System.setOut(java.io.PrintStream(output))
            command.parse(args)
        } finally {
            System.setOut(original)
        }
        return output.toString(Charsets.UTF_8)
    }

    private fun conversation(
        id: String,
        name: String,
        type: ConversationType,
    ) = Conversation(
        id = id,
        name = name,
        type = type,
        status = ConversationStatus.ACTIVE,
        memberCount = 0,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    private class CountingConversationService : ConversationService {
        var listCalls = 0

        override fun listConversations(): ListConversationsResult {
            listCalls++
            return ListConversationsResult.Success(ConversationListView(emptyList()))
        }

        override fun getConversation(conversationId: String) = GetConversationResult.Failure("unsupported", 1)

        override fun createConversation(
            name: String,
            type: ConversationType,
        ) = CreateConversationResult.Failure("unsupported", 1)

        override fun deleteConversation(conversationId: String) = DeleteConversationResult.Failure("unsupported", 1)

        override fun getMemberCount(conversationId: String) = GetConversationResult.Failure("unsupported", 1)

        override fun getMembers(conversationId: String) = wirecli.conversation.GetMembersResult.Failure("unsupported", 1)
    }

    private class StubConversationService(conversations: List<Conversation>) : ConversationService {
        private val result = ListConversationsResult.Success(ConversationListView(conversations))

        override fun listConversations() = result

        override fun getConversation(conversationId: String) =
            GetConversationResult.Success(ConversationDetailView(result.view.conversations.first()))

        override fun createConversation(
            name: String,
            type: ConversationType,
        ) = CreateConversationResult.Failure("unsupported", 1)

        override fun deleteConversation(conversationId: String) = DeleteConversationResult.Failure("unsupported", 1)

        override fun getMemberCount(conversationId: String) = getConversation(conversationId)

        override fun getMembers(conversationId: String) = wirecli.conversation.GetMembersResult.Failure("unsupported", 1)
    }
}

package wirecli.conversation

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import wirecli.auth.LoginInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AuthGuardedConversationServiceTest {
    @Test
    fun `returns auth failure when session service denies access for listConversations`() {
        val service =
            AuthGuardedConversationService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Failure("Unauthorized", ExitCodes.UNAUTHORIZED),
                    ),
                delegate =
                    FakeConversationService(
                        listResult =
                            ListConversationsResult.Success(
                                ConversationListView(conversations = emptyList()),
                            ),
                    ),
            )

        val result = service.listConversations()

        val failure = assertIs<ListConversationsResult.Failure>(result)
        assertEquals("Unauthorized", failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates to service when auth succeeds for listConversations`() {
        val expected: ListConversationsResult =
            ListConversationsResult.Success(
                ConversationListView(
                    conversations =
                        listOf(
                            Conversation(
                                id = "conv-001",
                                name = "Team",
                                type = ConversationType.GROUP,
                                status = ConversationStatus.ACTIVE,
                                memberCount = 5,
                                createdAt = "2024-12-20T14:00:00Z",
                                updatedAt = "2025-03-13T16:20:00Z",
                            ),
                        ),
                ),
            )

        val service =
            AuthGuardedConversationService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Success("Authenticated"),
                    ),
                delegate = FakeConversationService(listResult = expected),
            )

        val result = service.listConversations()

        assertEquals(expected, result)
    }

    @Test
    fun `returns auth failure when session service denies access for getConversation`() {
        val service =
            AuthGuardedConversationService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Failure("Unauthorized", ExitCodes.UNAUTHORIZED),
                    ),
                delegate =
                    FakeConversationService(
                        getResult =
                            GetConversationResult.Failure(
                                message = "Not found",
                                exitCode = ConversationExitCodes.NOT_FOUND,
                            ),
                    ),
            )

        val result = service.getConversation("conv-001")

        val failure = assertIs<GetConversationResult.Failure>(result)
        assertEquals("Unauthorized", failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `delegates to service when auth succeeds for getConversation`() {
        val expected: GetConversationResult =
            GetConversationResult.Success(
                ConversationDetailView(
                    conversation =
                        Conversation(
                            id = "conv-001",
                            name = "Team",
                            type = ConversationType.GROUP,
                            status = ConversationStatus.ACTIVE,
                            memberCount = 5,
                            createdAt = "2024-12-20T14:00:00Z",
                            updatedAt = "2025-03-13T16:20:00Z",
                        ),
                ),
            )

        val service =
            AuthGuardedConversationService(
                authSessionService =
                    FakeAuthSessionService(
                        authResult = AuthResult.Success("Authenticated"),
                    ),
                delegate = FakeConversationService(getResult = expected),
            )

        val result = service.getConversation("conv-001")

        assertEquals(expected, result)
    }
}

// Fake implementations for testing
class FakeAuthSessionService(
    private val authResult: AuthResult,
) : AuthSessionService {
    override fun login(input: LoginInput): AuthResult = authResult

    override fun logout(): AuthResult = AuthResult.Success("Logged out")

    override fun requireActiveSession(): AuthResult = authResult
}

class FakeConversationService(
    private val listResult: ListConversationsResult? = null,
    private val getResult: GetConversationResult? = null,
    private val createResult: CreateConversationResult? = null,
    private val deleteResult: DeleteConversationResult? = null,
) : ConversationService {
    override fun listConversations(): ListConversationsResult =
        listResult
            ?: ListConversationsResult.Failure(
                message = "Not configured",
                exitCode = ConversationExitCodes.SERVER_ERROR,
            )

    override fun getConversation(conversationId: String): GetConversationResult =
        getResult
            ?: GetConversationResult.Failure(
                message = "Not configured",
                exitCode = ConversationExitCodes.SERVER_ERROR,
            )

    override fun createConversation(
        name: String,
        type: ConversationType,
    ): CreateConversationResult =
        createResult
            ?: CreateConversationResult.Failure(
                message = "Not configured",
                exitCode = ConversationExitCodes.SERVER_ERROR,
            )

    override fun deleteConversation(conversationId: String): DeleteConversationResult =
        deleteResult
            ?: DeleteConversationResult.Failure(
                message = "Not configured",
                exitCode = ConversationExitCodes.SERVER_ERROR,
            )

    override fun getMemberCount(conversationId: String): GetConversationResult =
        getResult
            ?: GetConversationResult.Failure(
                message = "Not configured",
                exitCode = ConversationExitCodes.SERVER_ERROR,
            )
}

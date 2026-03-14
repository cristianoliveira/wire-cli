package wirecli.conversation

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes
import wirecli.auth.SessionInventory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionBackedConversationServiceTest {
    @Test
    fun `returns unauthorized when no session is persisted for listConversations`() {
        val service =
            SessionBackedConversationService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient =
                    FakeConversationApiClient(
                        listResult =
                            ListConversationsResult.Success(
                                ConversationListView(conversations = emptyList()),
                            ),
                    ),
            )

        val result = service.listConversations()

        val failure = assertIs<ListConversationsResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns backend conversation list result for persisted session`() {
        val expected: ListConversationsResult =
            ListConversationsResult.Success(
                ConversationListView(
                    conversations =
                        listOf(
                            Conversation(
                                id = "conv-001",
                                name = "Team Collaboration",
                                type = ConversationType.GROUP,
                                status = ConversationStatus.ACTIVE,
                                memberCount = 8,
                                createdAt = "2024-12-20T14:00:00Z",
                                updatedAt = "2025-03-13T16:20:00Z",
                            ),
                        ),
                ),
            )
        val service =
            SessionBackedConversationService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token-123",
                                server = null,
                            ),
                    ),
                apiClient = FakeConversationApiClient(listResult = expected),
            )

        val result = service.listConversations()

        assertEquals(expected, result)
    }

    @Test
    fun `returns unauthorized when no session is persisted for getConversation`() {
        val service =
            SessionBackedConversationService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient =
                    FakeConversationApiClient(
                        getResult =
                            GetConversationResult.Failure(
                                message = "Not found",
                                exitCode = ConversationExitCodes.NOT_FOUND,
                            ),
                    ),
            )

        val result = service.getConversation("conv-001")

        val failure = assertIs<GetConversationResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns backend conversation result for persisted session`() {
        val expected: GetConversationResult =
            GetConversationResult.Success(
                ConversationDetailView(
                    conversation =
                        Conversation(
                            id = "conv-001",
                            name = "Team Collaboration",
                            type = ConversationType.GROUP,
                            status = ConversationStatus.ACTIVE,
                            memberCount = 8,
                            createdAt = "2024-12-20T14:00:00Z",
                            updatedAt = "2025-03-13T16:20:00Z",
                        ),
                ),
            )
        val service =
            SessionBackedConversationService(
                sessionStore =
                    FakeSessionStore(
                        activeSession =
                            AuthSession(
                                userId = "alice@example.com",
                                accessToken = "token-123",
                                server = null,
                            ),
                    ),
                apiClient = FakeConversationApiClient(getResult = expected),
            )

        val result = service.getConversation("conv-001")

        assertEquals(expected, result)
    }
}

// Fake implementations for testing
class FakeSessionStore(
    private val activeSession: AuthSession?,
) : AuthSessionStore {
    override fun readActiveSession(): AuthSession? = activeSession

    override fun readSessionInventory(): SessionInventory =
        SessionInventory(
            activeSession = activeSession,
            validSessions = 0,
            invalidSessions = 0,
        )

    override fun writeActiveSession(session: AuthSession) {
        // Fake implementation
    }

    override fun clearActiveSession() {
        // Fake implementation
    }
}

class FakeConversationApiClient(
    private val listResult: ListConversationsResult? = null,
    private val getResult: GetConversationResult? = null,
    private val createResult: CreateConversationResult? = null,
    private val deleteResult: DeleteConversationResult? = null,
) : ConversationApiClient {
    override fun listConversations(session: AuthSession): ListConversationsResult =
        listResult
            ?: ListConversationsResult.Failure(
                message = "Not configured",
                exitCode = ConversationExitCodes.SERVER_ERROR,
            )

    override fun getConversation(
        session: AuthSession,
        conversationId: String,
    ): GetConversationResult =
        getResult
            ?: GetConversationResult.Failure(
                message = "Not configured",
                exitCode = ConversationExitCodes.SERVER_ERROR,
            )

    override fun createConversation(
        session: AuthSession,
        name: String,
        type: ConversationType,
    ): CreateConversationResult =
        createResult
            ?: CreateConversationResult.Failure(
                message = "Not configured",
                exitCode = ConversationExitCodes.SERVER_ERROR,
            )

    override fun deleteConversation(
        session: AuthSession,
        conversationId: String,
    ): DeleteConversationResult =
        deleteResult
            ?: DeleteConversationResult.Failure(
                message = "Not configured",
                exitCode = ConversationExitCodes.SERVER_ERROR,
            )

    override fun getMemberCount(
        session: AuthSession,
        conversationId: String,
    ): GetConversationResult =
        getResult
            ?: GetConversationResult.Failure(
                message = "Not configured",
                exitCode = ConversationExitCodes.SERVER_ERROR,
            )
}

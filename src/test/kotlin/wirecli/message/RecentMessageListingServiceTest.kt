package wirecli.message

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import wirecli.conversation.Conversation
import wirecli.conversation.ConversationApiClient
import wirecli.conversation.ConversationListView
import wirecli.conversation.ConversationStatus
import wirecli.conversation.ConversationType
import wirecli.conversation.CreateConversationResult
import wirecli.conversation.DeleteConversationResult
import wirecli.conversation.GetConversationResult
import wirecli.conversation.GetMembersResult
import wirecli.conversation.ListConversationsResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RecentMessageListingServiceTest {
    @Test
    fun `listRecentMessages returns unauthorized when no session is persisted`() {
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(null),
                apiClient = FakeMessageApiClient(),
                conversationApiClient = FakeConversationApiClient(),
            )

        val result = service.listRecentMessages(RecentMessagesQuery(limit = 10, receivedOnly = false))

        val failure = assertIs<ListRecentMessagesResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `listRecentMessages merges conversations and sorts by timestamp descending`() {
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient =
                    FakeMessageApiClient(
                        byConversation =
                            mapOf(
                                "conv-a" to
                                    listOf(
                                        message("msg-1", "2026-03-20T10:00:00Z", "alpha"),
                                        message("msg-2", "2026-03-20T10:02:00Z", "beta", senderId = "me@example.com"),
                                    ),
                                "conv-b" to
                                    listOf(
                                        message("msg-3", "2026-03-20T10:01:00Z", "gamma", senderId = "bob@example.com"),
                                    ),
                            ),
                    ),
                conversationApiClient =
                    FakeConversationApiClient(
                        conversations =
                            listOf(
                                conversation("conv-a", "Alpha"),
                                conversation("conv-b", "Beta"),
                            ),
                    ),
            )

        val result = service.listRecentMessages(RecentMessagesQuery(limit = 2, receivedOnly = false))

        val success = assertIs<ListRecentMessagesResult.Success>(result)
        assertEquals(listOf("msg-2", "msg-3"), success.view.messages.map { it.messageId })
        assertEquals(listOf("Alpha", "Beta"), success.view.messages.map { it.conversationName })
    }

    @Test
    fun `listRecentMessages filters sent messages when received-only is set`() {
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient =
                    FakeMessageApiClient(
                        byConversation =
                            mapOf(
                                "conv-a" to
                                    listOf(
                                        message("msg-1", "2026-03-20T10:00:00Z", "mine", senderId = "me@example.com"),
                                        message("msg-2", "2026-03-20T10:01:00Z", "theirs", senderId = "alice@example.com"),
                                    ),
                            ),
                    ),
                conversationApiClient = FakeConversationApiClient(conversations = listOf(conversation("conv-a", "Alpha"))),
            )

        val result = service.listRecentMessages(RecentMessagesQuery(limit = 10, receivedOnly = true))

        val success = assertIs<ListRecentMessagesResult.Success>(result)
        assertEquals(listOf("msg-2"), success.view.messages.map { it.messageId })
    }

    @Test
    fun `listRecentMessages scopes conversation and filters since and self mentions`() {
        val apiClient =
            FakeMessageApiClient(
                byConversation =
                    mapOf(
                        "conv-a" to
                            listOf(
                                message("old", "2026-03-20T09:59:00Z", "old", mentionsSelf = true),
                                message("not-mentioned", "2026-03-20T10:01:00Z", "hello"),
                                message("mentioned", "2026-03-20T10:02:00Z", "hello me", mentionsSelf = true),
                            ),
                        "conv-b" to listOf(message("other", "2026-03-20T10:03:00Z", "other", mentionsSelf = true)),
                    ),
            )
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
                conversationApiClient =
                    FakeConversationApiClient(
                        conversations = listOf(conversation("conv-a", "Alpha"), conversation("conv-b", "Beta")),
                    ),
            )

        val result =
            service.listRecentMessages(
                RecentMessagesQuery(
                    limit = 2,
                    since = Instant.parse("2026-03-20T10:00:00Z"),
                    conversationId = "conv-a",
                    mentionsMe = true,
                ),
            )

        val success = assertIs<ListRecentMessagesResult.Success>(result)
        assertEquals(listOf("mentioned"), success.view.messages.map { it.messageId })
        assertEquals(listOf("conv-a"), apiClient.remoteFetches)
        assertEquals(listOf(2), apiClient.remoteFetchLimits)
    }

    @Test
    fun `listRecentMessages returns empty when scoped conversation does not exist`() {
        val apiClient = FakeMessageApiClient()
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
                conversationApiClient = FakeConversationApiClient(conversations = listOf(conversation("conv-a", "Alpha"))),
            )

        val result = service.listRecentMessages(RecentMessagesQuery(conversationId = "missing"))

        val success = assertIs<ListRecentMessagesResult.Success>(result)
        assertEquals(emptyList(), success.view.messages)
        assertEquals(emptyList(), apiClient.remoteFetches)
    }

    @Test
    fun `listRecentMessages syncs once then reads every conversation locally by default`() {
        val apiClient =
            FakeMessageApiClient(
                byConversation =
                    mapOf(
                        "conv-a" to listOf(message("msg-1", "2026-03-20T10:00:00Z", "alpha")),
                        "conv-b" to listOf(message("msg-2", "2026-03-20T10:01:00Z", "beta")),
                    ),
            )
        val syncRefresher = FakeRecentMessageRefresher()
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
                conversationApiClient =
                    FakeConversationApiClient(
                        conversations = listOf(conversation("conv-a", "Alpha"), conversation("conv-b", "Beta")),
                    ),
                syncRefresher = syncRefresher,
            )

        val result = service.listRecentMessages(RecentMessagesQuery(limit = 10, receivedOnly = false))

        val success = assertIs<ListRecentMessagesResult.Success>(result)
        assertEquals(listOf("msg-2", "msg-1"), success.view.messages.map { it.messageId })
        assertEquals(1, syncRefresher.refreshCount, "sync must run exactly once")
        assertEquals(listOf("conv-a", "conv-b"), apiClient.localFetches)
        assertEquals(emptyList<String>(), apiClient.remoteFetches, "no per-conversation server syncs")
    }

    @Test
    fun `listServerRecentMessages syncs once then reads every conversation locally`() {
        val apiClient =
            FakeMessageApiClient(
                byConversation =
                    mapOf(
                        "conv-a" to listOf(message("msg-1", "2026-03-20T10:00:00Z", "alpha")),
                    ),
            )
        val syncRefresher = FakeRecentMessageRefresher()
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
                conversationApiClient = FakeConversationApiClient(conversations = listOf(conversation("conv-a", "Alpha"))),
                syncRefresher = syncRefresher,
            )

        val result = service.listServerRecentMessages(RecentMessagesQuery(limit = 10, receivedOnly = false))

        assertIs<ListRecentMessagesResult.Success>(result)
        assertEquals(1, syncRefresher.refreshCount, "--no-cache must still sync once")
        assertEquals(listOf("conv-a"), apiClient.localFetches)
    }

    @Test
    fun `listRecentMessages returns failure and skips reads when the upfront sync fails`() {
        val apiClient = FakeMessageApiClient(byConversation = mapOf("conv-a" to listOf(message("msg-1", "2026-03-20T10:00:00Z", "alpha"))))
        val syncRefresher =
            FakeRecentMessageRefresher(failure = RecentMessageRefresher.RefreshFailure("sync boom", ExitCodes.NETWORK_ERROR))
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
                conversationApiClient = FakeConversationApiClient(conversations = listOf(conversation("conv-a", "Alpha"))),
                syncRefresher = syncRefresher,
            )

        val result = service.listRecentMessages(RecentMessagesQuery(limit = 10, receivedOnly = false))

        val failure = assertIs<ListRecentMessagesResult.Failure>(result)
        assertEquals("sync boom", failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
        assertEquals(emptyList<String>(), apiClient.localFetches)
        assertEquals(emptyList<String>(), apiClient.remoteFetches)
    }

    @Test
    fun `listLocalRecentMessages reads local cache without syncing`() {
        val apiClient = FakeMessageApiClient(byConversation = mapOf("conv-a" to listOf(message("msg-1", "2026-03-20T10:00:00Z", "hello"))))
        val syncRefresher = FakeRecentMessageRefresher()
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = apiClient,
                conversationApiClient = FakeConversationApiClient(conversations = listOf(conversation("conv-a", "Alpha"))),
                syncRefresher = syncRefresher,
            )

        val result = service.listLocalRecentMessages(RecentMessagesQuery(limit = 10, receivedOnly = false))

        assertIs<ListRecentMessagesResult.Success>(result)
        assertEquals(0, syncRefresher.refreshCount, "local read must not sync")
        assertEquals(listOf("conv-a"), apiClient.localFetches)
        assertEquals(emptyList<String>(), apiClient.remoteFetches)
    }

    @Test
    fun `listRecentMessages returns conversation list failure`() {
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = FakeMessageApiClient(),
                conversationApiClient = FakeConversationApiClient(listResult = ListConversationsResult.Failure("boom", 13)),
            )

        val result = service.listRecentMessages(RecentMessagesQuery(limit = 10, receivedOnly = false))

        val failure = assertIs<ListRecentMessagesResult.Failure>(result)
        assertEquals("boom", failure.message)
        assertEquals(13, failure.exitCode)
    }

    @Test
    fun `listRecentMessages returns fetch failure`() {
        val service =
            SessionBackedMessageService(
                sessionStore = FakeSessionStore(testSession),
                apiClient = FakeMessageApiClient(fetchFailure = FetchMessagesResult.Failure("fetch failed", 13)),
                conversationApiClient = FakeConversationApiClient(conversations = listOf(conversation("conv-a", "Alpha"))),
            )

        val result = service.listRecentMessages(RecentMessagesQuery(limit = 10, receivedOnly = false))

        val failure = assertIs<ListRecentMessagesResult.Failure>(result)
        assertEquals("fetch failed", failure.message)
        assertEquals(13, failure.exitCode)
    }

    private val testSession = AuthSession(userId = "me@example.com", accessToken = "token", server = null)

    private fun conversation(
        id: String,
        name: String,
    ) = Conversation(
        id = id,
        name = name,
        type = ConversationType.GROUP,
        status = ConversationStatus.ACTIVE,
        memberCount = 2,
        createdAt = "2026-03-20T09:00:00Z",
        updatedAt = "2026-03-20T09:00:00Z",
    )

    private fun message(
        id: String,
        timestamp: String,
        content: String,
        senderId: String = "alice@example.com",
        senderName: String = "Alice",
        mentionsSelf: Boolean = false,
    ) = ConversationMessage(
        id = id,
        senderId = senderId,
        senderName = senderName,
        timestamp = timestamp,
        content = content,
        mentionsSelf = mentionsSelf,
    )

    private class FakeSessionStore(private val activeSession: AuthSession?) : SessionProvider {
        override fun readActiveSession(): AuthSession? = activeSession
    }

    private class FakeConversationApiClient(
        private val conversations: List<Conversation> = emptyList(),
        private val listResult: ListConversationsResult = ListConversationsResult.Success(ConversationListView(conversations)),
    ) : ConversationApiClient {
        override fun listConversations(session: AuthSession): ListConversationsResult = listResult

        override fun getConversation(
            session: AuthSession,
            conversationId: String,
        ): GetConversationResult = GetConversationResult.Failure("unsupported", 1)

        override fun createConversation(
            session: AuthSession,
            name: String,
            type: ConversationType,
        ): CreateConversationResult = CreateConversationResult.Failure("unsupported", 1)

        override fun deleteConversation(
            session: AuthSession,
            conversationId: String,
        ): DeleteConversationResult = DeleteConversationResult.Failure("unsupported", 1)

        override fun getMemberCount(
            session: AuthSession,
            conversationId: String,
        ): GetConversationResult = GetConversationResult.Failure("unsupported", 1)

        override fun getMembers(
            session: AuthSession,
            conversationId: String,
        ): GetMembersResult = GetMembersResult.Failure("unsupported", 1)
    }

    private class FakeRecentMessageRefresher(
        private val failure: RecentMessageRefresher.RefreshFailure? = null,
    ) : RecentMessageRefresher {
        var refreshCount = 0
            private set

        override fun refresh(session: AuthSession): RecentMessageRefresher.RefreshFailure? {
            refreshCount += 1
            return failure
        }
    }

    private class FakeMessageApiClient(
        private val byConversation: Map<String, List<ConversationMessage>> = emptyMap(),
        private val fetchFailure: FetchMessagesResult? = null,
    ) : MessageApiClient {
        val remoteFetches = mutableListOf<String>()
        val remoteFetchLimits = mutableListOf<Int>()
        val localFetches = mutableListOf<String>()

        override fun sendMessage(
            session: AuthSession,
            conversationId: String,
            text: String,
        ): SendMessageResult = SendMessageResult.Success

        override fun fetchMessages(
            session: AuthSession,
            conversationId: String,
            limit: Int,
        ): FetchMessagesResult {
            remoteFetches += conversationId
            remoteFetchLimits += limit
            return fetchFailure ?: FetchMessagesResult.Success(FetchMessagesView(conversationId, byConversation[conversationId].orEmpty()))
        }

        override fun fetchLocalMessages(
            session: AuthSession,
            conversationId: String,
            limit: Int,
        ): FetchMessagesResult {
            localFetches += conversationId
            return fetchFailure ?: FetchMessagesResult.Success(FetchMessagesView(conversationId, byConversation[conversationId].orEmpty()))
        }

        override fun searchMessages(
            session: AuthSession,
            query: String,
            conversationId: String?,
            limit: Int,
        ): SearchMessagesResult = SearchMessagesResult.Success(emptyList())

        override fun toggleReaction(
            session: AuthSession,
            conversationId: String,
            messageId: String,
            emoji: String,
        ): ToggleReactionResult = ToggleReactionResult.Success(ReactionAction.ADDED)

        override fun deleteMessage(
            session: AuthSession,
            conversationId: String,
            messageId: String,
            scope: DeleteScope,
        ): DeleteMessageResult = DeleteMessageResult.Success(scope)

        override fun setMessageRead(
            session: AuthSession,
            conversationId: String,
            messageId: String,
        ): SetMessageReadResult = SetMessageReadResult.Success
    }
}

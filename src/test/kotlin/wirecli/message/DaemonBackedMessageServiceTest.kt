package wirecli.message

import kotlin.test.Test
import kotlin.test.assertEquals

class DaemonBackedMessageServiceTest {
    @Test
    fun `running daemon reads local cache without server sync`() {
        val delegate = FakeMessageService(localResult = success("cached"))
        val service = DaemonBackedMessageService({ delegate }, DaemonStatus { true })

        val result = service.fetchMessages("conv-1")

        assertEquals(success("cached"), result)
        assertEquals(1, delegate.localFetchCalls)
        assertEquals(0, delegate.serverFetchCalls)
    }

    @Test
    fun `running daemon lists from local cache without syncing`() {
        val delegate = FakeMessageService()
        val service = DaemonBackedMessageService({ delegate }, DaemonStatus { true })

        service.listRecentMessages(limit = 5, receivedOnly = true)

        assertEquals(1, delegate.localListCalls)
        assertEquals(0, delegate.serverListCalls, "daemon warm must not sync")
    }

    @Test
    fun `stopped daemon lists via the syncing path`() {
        val delegate = FakeMessageService()
        val service = DaemonBackedMessageService({ delegate }, DaemonStatus { false })

        service.listRecentMessages(limit = 5, receivedOnly = false)

        assertEquals(1, delegate.serverListCalls)
        assertEquals(0, delegate.localListCalls)
    }

    @Test
    fun `listServerRecentMessages bypasses the daemon cache even when warm`() {
        var statusChecks = 0
        val delegate = FakeMessageService()
        val service =
            DaemonBackedMessageService(
                { delegate },
                DaemonStatus {
                    statusChecks++
                    true
                },
            )

        service.listServerRecentMessages(limit = 5, receivedOnly = false)

        assertEquals(1, delegate.serverListCalls)
        assertEquals(0, delegate.localListCalls)
        assertEquals(0, statusChecks, "--no-cache must not consult daemon status")
    }

    @Test
    fun `cached empty list is returned without server sync`() {
        val empty = FetchMessagesResult.Success(FetchMessagesView("conv-1", emptyList()))
        val delegate = FakeMessageService(localResult = empty)
        val service = DaemonBackedMessageService({ delegate }, DaemonStatus { true })

        val result = service.fetchMessages("conv-1")

        assertEquals(empty, result)
        assertEquals(0, delegate.serverFetchCalls)
    }

    @Test
    fun `local cache failure falls back to server`() {
        val localFailure = FetchMessagesResult.Failure("cache failed", 13)
        val delegate = FakeMessageService(localResult = localFailure, serverResult = success("server"))
        val service = DaemonBackedMessageService({ delegate }, DaemonStatus { true })

        val result = service.fetchMessages("conv-1")

        assertEquals(success("server"), result)
        assertEquals(1, delegate.serverFetchCalls)
    }

    @Test
    fun `stopped daemon fetches from server without reading local cache`() {
        val delegate = FakeMessageService(serverResult = success("server"))
        val service = DaemonBackedMessageService({ delegate }, DaemonStatus { false })

        val result = service.fetchMessages("conv-1")

        assertEquals(success("server"), result)
        assertEquals(0, delegate.localFetchCalls)
        assertEquals(1, delegate.serverFetchCalls)
    }

    @Test
    fun `server fetch bypasses daemon status`() {
        var statusChecks = 0
        val delegate = FakeMessageService(serverResult = success("server"))
        val service =
            DaemonBackedMessageService(
                { delegate },
                DaemonStatus {
                    statusChecks++
                    true
                },
            )

        val result = service.fetchServerMessages("conv-1")

        assertEquals(success("server"), result)
        assertEquals(0, statusChecks)
        assertEquals(1, delegate.serverFetchCalls)
    }

    private fun success(content: String) =
        FetchMessagesResult.Success(
            FetchMessagesView(
                "conv-1",
                listOf(ConversationMessage("m1", "u1", "User", "now", content)),
            ),
        )

    private class FakeMessageService(
        private val localResult: FetchMessagesResult = successResult(),
        private val serverResult: FetchMessagesResult = successResult(),
    ) : MessageService {
        var localFetchCalls = 0
            private set
        var serverFetchCalls = 0
            private set
        var localListCalls = 0
            private set
        var serverListCalls = 0
            private set

        override fun sendMessage(
            conversationId: String,
            text: String,
        ) = SendMessageResult.Success

        override fun fetchMessages(conversationId: String): FetchMessagesResult {
            serverFetchCalls++
            return serverResult
        }

        override fun fetchLocalMessages(conversationId: String): FetchMessagesResult {
            localFetchCalls++
            return localResult
        }

        override fun listRecentMessages(
            limit: Int,
            receivedOnly: Boolean,
        ): ListRecentMessagesResult {
            serverListCalls++
            return ListRecentMessagesResult.Success(RecentMessagesView(emptyList()))
        }

        override fun listLocalRecentMessages(
            limit: Int,
            receivedOnly: Boolean,
        ): ListRecentMessagesResult {
            localListCalls++
            return ListRecentMessagesResult.Success(RecentMessagesView(emptyList()))
        }

        override fun searchMessages(
            query: String,
            conversationId: String?,
            limit: Int,
        ) = SearchMessagesResult.Success(emptyList())

        override fun toggleReaction(
            conversationId: String,
            messageId: String,
            emoji: String,
        ) = ToggleReactionResult.Success(ReactionAction.ADDED)

        companion object {
            private fun successResult() = FetchMessagesResult.Success(FetchMessagesView("conv-1", emptyList()))
        }
    }
}

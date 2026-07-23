package wirecli.message

import java.time.Instant

fun interface DaemonStatus {
    fun isRunning(): Boolean

    fun lastUpdateTimestamp(): Instant? = null
}

class DaemonBackedMessageService(
    private val delegateProvider: () -> MessageService,
    private val daemonStatus: DaemonStatus,
) : MessageService {
    private val delegate by lazy(delegateProvider)

    override fun fetchMessages(
        conversationId: String,
        limit: Int,
    ): FetchMessagesResult {
        if (!daemonStatus.isRunning()) return delegate.fetchMessages(conversationId, limit)

        return when (val cached = delegate.fetchLocalMessages(conversationId, limit)) {
            is FetchMessagesResult.Success -> cached
            is FetchMessagesResult.Failure -> delegate.fetchMessages(conversationId, limit)
        }
    }

    override fun fetchServerMessages(
        conversationId: String,
        limit: Int,
    ): FetchMessagesResult = delegate.fetchMessages(conversationId, limit)

    override fun fetchLocalMessages(
        conversationId: String,
        limit: Int,
    ): FetchMessagesResult = delegate.fetchLocalMessages(conversationId, limit)

    override fun sendMessage(
        conversationId: String,
        text: String,
    ): SendMessageResult = delegate.sendMessage(conversationId, text)

    override fun listRecentMessages(query: RecentMessagesQuery): ListRecentMessagesResult {
        // A running daemon keeps the local cache warm, so skip the sync and
        // read local; without it, the delegate syncs once then reads local.
        return if (daemonStatus.isRunning()) {
            delegate.listLocalRecentMessages(query)
        } else {
            delegate.listRecentMessages(query)
        }
    }

    override fun listServerRecentMessages(query: RecentMessagesQuery): ListRecentMessagesResult = delegate.listRecentMessages(query)

    override fun listLocalRecentMessages(query: RecentMessagesQuery): ListRecentMessagesResult = delegate.listLocalRecentMessages(query)

    override fun searchMessages(
        query: String,
        conversationId: String?,
        limit: Int,
    ): SearchMessagesResult = delegate.searchMessages(query, conversationId, limit)

    override fun toggleReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
    ): ToggleReactionResult = delegate.toggleReaction(conversationId, messageId, emoji)

    override fun deleteMessage(
        conversationId: String,
        messageId: String,
        scope: DeleteScope,
    ): DeleteMessageResult = delegate.deleteMessage(conversationId, messageId, scope)

    override fun setMessageRead(
        conversationId: String,
        messageId: String,
    ): SetMessageReadResult = delegate.setMessageRead(conversationId, messageId)

    override fun sendTypingStatus(
        conversationId: String,
        status: TypingStatus,
    ): SendTypingResult = delegate.sendTypingStatus(conversationId, status)

    override fun observeMessages(conversationId: String) = delegate.observeMessages(conversationId)
}

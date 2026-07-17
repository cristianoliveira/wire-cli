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

    override fun fetchMessages(conversationId: String): FetchMessagesResult {
        if (!daemonStatus.isRunning()) return delegate.fetchMessages(conversationId)

        return when (val cached = delegate.fetchLocalMessages(conversationId)) {
            is FetchMessagesResult.Success -> cached
            is FetchMessagesResult.Failure -> delegate.fetchMessages(conversationId)
        }
    }

    override fun fetchServerMessages(conversationId: String): FetchMessagesResult = delegate.fetchMessages(conversationId)

    override fun fetchLocalMessages(conversationId: String): FetchMessagesResult = delegate.fetchLocalMessages(conversationId)

    override fun sendMessage(
        conversationId: String,
        text: String,
    ): SendMessageResult = delegate.sendMessage(conversationId, text)

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

    override fun sendTypingStatus(
        conversationId: String,
        status: TypingStatus,
    ): SendTypingResult = delegate.sendTypingStatus(conversationId, status)

    override fun observeMessages(conversationId: String) = delegate.observeMessages(conversationId)
}

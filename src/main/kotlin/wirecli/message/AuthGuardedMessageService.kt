package wirecli.message

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class AuthGuardedMessageService(
    private val authSessionService: AuthSessionService,
    private val delegate: MessageService,
) : MessageService {
    override fun sendMessage(
        conversationId: String,
        text: String,
    ): SendMessageResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.sendMessage(conversationId, text)
            is AuthResult.Failure ->
                SendMessageResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun fetchMessages(
        conversationId: String,
        limit: Int,
    ): FetchMessagesResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.fetchMessages(conversationId, limit)
            is AuthResult.Failure ->
                FetchMessagesResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun fetchLocalMessages(
        conversationId: String,
        limit: Int,
    ): FetchMessagesResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.fetchLocalMessages(conversationId, limit)
            is AuthResult.Failure ->
                FetchMessagesResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun listRecentMessages(query: RecentMessagesQuery): ListRecentMessagesResult =
        withSession { delegate.listRecentMessages(query) }

    override fun listServerRecentMessages(query: RecentMessagesQuery): ListRecentMessagesResult =
        withSession { delegate.listServerRecentMessages(query) }

    override fun listLocalRecentMessages(query: RecentMessagesQuery): ListRecentMessagesResult =
        withSession { delegate.listLocalRecentMessages(query) }

    private inline fun withSession(action: () -> ListRecentMessagesResult): ListRecentMessagesResult =
        when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> action()
            is AuthResult.Failure ->
                ListRecentMessagesResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }

    override fun searchMessages(
        query: String,
        conversationId: String?,
        limit: Int,
    ): SearchMessagesResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.searchMessages(query, conversationId, limit)
            is AuthResult.Failure ->
                SearchMessagesResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun toggleReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
    ): ToggleReactionResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.toggleReaction(conversationId, messageId, emoji)
            is AuthResult.Failure ->
                ToggleReactionResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun deleteMessage(
        conversationId: String,
        messageId: String,
        scope: DeleteScope,
    ): DeleteMessageResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.deleteMessage(conversationId, messageId, scope)
            is AuthResult.Failure ->
                DeleteMessageResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun setMessageRead(
        conversationId: String,
        messageId: String,
    ): SetMessageReadResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.setMessageRead(conversationId, messageId)
            is AuthResult.Failure ->
                SetMessageReadResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun sendTypingStatus(
        conversationId: String,
        status: TypingStatus,
    ): SendTypingResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.sendTypingStatus(conversationId, status)
            is AuthResult.Failure ->
                SendTypingResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

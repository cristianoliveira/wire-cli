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

    override fun fetchMessages(conversationId: String): FetchMessagesResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.fetchMessages(conversationId)
            is AuthResult.Failure ->
                FetchMessagesResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
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

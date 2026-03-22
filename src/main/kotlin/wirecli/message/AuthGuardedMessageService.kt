package wirecli.message

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.shared.MessageError

class AuthGuardedMessageService(
    private val authSessionService: AuthSessionService,
    private val delegate: MessageService,
) : MessageService {
    override fun sendMessage(
        conversationId: String,
        text: String,
    ): MessageResult<Unit> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.sendMessage(conversationId, text)
            is AuthResult.Failure ->
                MessageResult.Failure(
                    error = MessageError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun fetchMessages(conversationId: String): MessageResult<FetchMessagesView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.fetchMessages(conversationId)
            is AuthResult.Failure ->
                MessageResult.Failure(
                    error = MessageError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun sendTypingStatus(
        conversationId: String,
        status: TypingStatus,
    ): MessageResult<Unit> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.sendTypingStatus(conversationId, status)
            is AuthResult.Failure ->
                MessageResult.Failure(
                    error = MessageError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }
}

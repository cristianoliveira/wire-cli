package wirecli.message

import wirecli.auth.AuthSessionService
import wirecli.shared.MessageError
import wirecli.shared.Result

class AuthGuardedMessageService(
    private val authSessionService: AuthSessionService,
    private val delegate: MessageService,
) : MessageService {
    override fun sendMessage(
        conversationId: String,
        text: String,
    ): MessageResult<Unit> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> delegate.sendMessage(conversationId, text)
            is Result.Failure ->
                Result.Failure(
                    error = MessageError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun fetchMessages(conversationId: String): MessageResult<FetchMessagesView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> delegate.fetchMessages(conversationId)
            is Result.Failure ->
                Result.Failure(
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
            is Result.Success -> delegate.sendTypingStatus(conversationId, status)
            is Result.Failure ->
                Result.Failure(
                    error = MessageError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }
}

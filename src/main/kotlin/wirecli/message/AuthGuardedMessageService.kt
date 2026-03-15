package wirecli.message

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

/**
 * Service wrapper that guards all message operations with authentication checks.
 *
 * Before delegating to the underlying service, this implementation verifies that
 * an active session exists using the AuthSessionService. If authentication fails,
 * the failure result is returned immediately without delegating.
 *
 * @invariant authSessionService.requireActiveSession() is called before each operation
 * @invariant Failure results are never delegated further; auth failures are terminal
 * @invariant Delegation only occurs on successful auth check
 */
class AuthGuardedMessageService(
    private val authSessionService: AuthSessionService,
    private val delegate: MessageService,
) : MessageService {
    override fun send(
        conversationId: String,
        text: String,
    ): MessageSendResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.send(conversationId, text)
            is AuthResult.Failure ->
                MessageSendResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun fetch(
        conversationId: String,
        limit: Int?,
        from: String?,
    ): MessageListResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.fetch(conversationId, limit, from)
            is AuthResult.Failure ->
                MessageListResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun getDetail(
        conversationId: String,
        messageId: String,
    ): MessageDetailResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getDetail(conversationId, messageId)
            is AuthResult.Failure ->
                MessageDetailResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

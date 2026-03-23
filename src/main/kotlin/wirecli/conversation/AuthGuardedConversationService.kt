package wirecli.conversation

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.shared.ConversationError
import wirecli.shared.Result

class AuthGuardedConversationService(
    private val authSessionService: AuthSessionService,
    private val delegate: ConversationService,
) : ConversationService {
    override fun listConversations(): ConversationResult<ConversationListView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.listConversations()
            is AuthResult.Failure ->
                Result.Failure(
                    error = ConversationError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun getConversation(conversationId: String): ConversationResult<ConversationDetailView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getConversation(conversationId)
            is AuthResult.Failure ->
                Result.Failure(
                    error = ConversationError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun createConversation(
        name: String,
        type: ConversationType,
    ): ConversationResult<ConversationDetailView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.createConversation(name, type)
            is AuthResult.Failure ->
                Result.Failure(
                    error = ConversationError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun deleteConversation(conversationId: String): ConversationResult<String> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.deleteConversation(conversationId)
            is AuthResult.Failure ->
                Result.Failure(
                    error = ConversationError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun getMemberCount(conversationId: String): ConversationResult<ConversationDetailView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getMemberCount(conversationId)
            is AuthResult.Failure ->
                Result.Failure(
                    error = ConversationError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }
}

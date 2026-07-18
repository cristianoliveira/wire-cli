package wirecli.conversation

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class AuthGuardedConversationService(
    private val authSessionService: AuthSessionService,
    private val delegate: ConversationService,
) : ConversationService {
    override fun listConversations(): ListConversationsResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.listConversations()
            is AuthResult.Failure ->
                ListConversationsResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun getConversation(conversationId: String): GetConversationResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getConversation(conversationId)
            is AuthResult.Failure ->
                GetConversationResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun createConversation(
        name: String,
        type: ConversationType,
    ): CreateConversationResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.createConversation(name, type)
            is AuthResult.Failure ->
                CreateConversationResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun deleteConversation(conversationId: String): DeleteConversationResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.deleteConversation(conversationId)
            is AuthResult.Failure ->
                DeleteConversationResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun getMemberCount(conversationId: String): GetConversationResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getMemberCount(conversationId)
            is AuthResult.Failure ->
                GetConversationResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun getMembers(conversationId: String): GetMembersResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getMembers(conversationId)
            is AuthResult.Failure ->
                GetMembersResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

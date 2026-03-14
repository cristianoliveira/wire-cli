package wirecli.conversation

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes

class SessionBackedConversationService(
    private val sessionStore: AuthSessionStore,
    private val apiClient: ConversationApiClient,
) : ConversationService {
    override fun listConversations(): ListConversationsResult {
        val session =
            sessionStore.readActiveSession()
                ?: return ListConversationsResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.listConversations(session)
    }

    override fun getConversation(conversationId: String): GetConversationResult {
        val session =
            sessionStore.readActiveSession()
                ?: return GetConversationResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.getConversation(session, conversationId)
    }

    override fun createConversation(
        name: String,
        type: ConversationType,
    ): CreateConversationResult {
        val session =
            sessionStore.readActiveSession()
                ?: return CreateConversationResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.createConversation(session, name, type)
    }

    override fun deleteConversation(conversationId: String): DeleteConversationResult {
        val session =
            sessionStore.readActiveSession()
                ?: return DeleteConversationResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.deleteConversation(session, conversationId)
    }

    override fun getMemberCount(conversationId: String): GetConversationResult {
        val session =
            sessionStore.readActiveSession()
                ?: return GetConversationResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.getMemberCount(session, conversationId)
    }
}

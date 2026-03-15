package wirecli.message

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes

/**
 * Service implementation that depends on the current AuthSession.
 *
 * Retrieves the active session from the session store and uses it to authorize
 * all message operations through the API client.
 *
 * @invariant sessionStore.readActiveSession() is called for each operation
 * @invariant Returns auth failure if no active session exists
 * @invariant All API calls are delegated to the provided apiClient
 */
class SessionBackedMessageService(
    private val sessionStore: AuthSessionStore,
    private val apiClient: MessageApiClient,
) : MessageService {
    override fun send(
        conversationId: String,
        text: String,
    ): MessageSendResult {
        val session =
            sessionStore.readActiveSession()
                ?: return MessageSendResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        val view = MessageSendView(conversationId = conversationId, text = text)
        return apiClient.sendMessage(session, view)
    }

    override fun fetch(
        conversationId: String,
        limit: Int?,
        from: String?,
    ): MessageListResult {
        val session =
            sessionStore.readActiveSession()
                ?: return MessageListResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.fetchMessages(session, conversationId, limit, from)
    }

    override fun getDetail(
        conversationId: String,
        messageId: String,
    ): MessageDetailResult {
        val session =
            sessionStore.readActiveSession()
                ?: return MessageDetailResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.fetchMessage(session, conversationId, messageId)
    }
}

package wirecli.message

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes

private val logger = KotlinLogging.logger {}

class SessionBackedMessageService(
    private val sessionStore: AuthSessionStore,
    private val apiClient: MessageApiClient,
) : MessageService {
    override fun sendMessage(
        conversationId: String,
        text: String,
    ): SendMessageResult {
        logger.debug { "Service operation: sendMessage(conversationId=$conversationId, textLength=${text.length}) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return SendMessageResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for sendMessage($conversationId)" } }

        logger.debug { "Active session found for user: ${session.userId} - calling API client" }
        return apiClient.sendMessage(session, conversationId, text).also { result ->
            when (result) {
                is SendMessageResult.Success ->
                    logger.info { "Service: Successfully sent message to conversation $conversationId" }
                is SendMessageResult.Failure ->
                    logger.warn { "Service: Failed to send message to conversation $conversationId - ${result.message}" }
            }
        }
    }
}

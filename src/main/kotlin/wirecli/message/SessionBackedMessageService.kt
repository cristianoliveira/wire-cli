package wirecli.message

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider

private val logger = KotlinLogging.logger {}

class SessionBackedMessageService(
    private val sessionStore: SessionProvider,
    private val apiClient: MessageApiClient,
    private val typingApiClient: MessageTypingApiClient? = apiClient as? MessageTypingApiClient,
) : MessageService {
    override fun sendMessage(
        conversationId: String,
        text: String,
    ): SendMessageResult {
        logger.debug {
            "Service operation: sendMessage(conversationId=$conversationId, textLength=${text.length}) started"
        }

        val session =
            sessionStore.readActiveSession()
                ?: return SendMessageResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for sendMessage($conversationId)" } }

        logger.info {
            "message-send session resolved: userId=${session.userId}, conversationId=$conversationId"
        }
        logger.debug { "message-send forwarding to API client: textLength=${text.length}" }
        return apiClient.sendMessage(session, conversationId, text).also { result ->
            when (result) {
                is SendMessageResult.Success ->
                    logger.info { "message-send service outcome=success conversationId=$conversationId" }
                is SendMessageResult.Failure ->
                    logger.warn {
                        "message-send service outcome=failure conversationId=$conversationId exitCode=${result.exitCode}"
                    }
            }
        }
    }

    override fun fetchMessages(conversationId: String): FetchMessagesResult {
        logger.debug { "Service operation: fetchMessages(conversationId=$conversationId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return FetchMessagesResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for fetchMessages($conversationId)" } }

        logger.info {
            "message-fetch session resolved: userId=${session.userId}, conversationId=$conversationId"
        }
        return apiClient.fetchMessages(session, conversationId).also { result ->
            when (result) {
                is FetchMessagesResult.Success ->
                    logger.info {
                        "message-fetch service outcome=success conversationId=$conversationId count=${result.view.messages.size}"
                    }

                is FetchMessagesResult.Failure ->
                    logger.warn {
                        "message-fetch service outcome=failure conversationId=$conversationId exitCode=${result.exitCode}"
                    }
            }
        }
    }

    override fun sendTypingStatus(
        conversationId: String,
        status: TypingStatus,
    ): SendTypingResult {
        if (typingApiClient == null) {
            return SendTypingResult.Failure(
                message = MessageUserMessages.TYPING_UNSUPPORTED,
                exitCode = MessageExitCodes.SERVER_ERROR,
            )
        }

        logger.debug {
            "Service operation: sendTypingStatus(conversationId=$conversationId, status=$status) started"
        }

        val session =
            sessionStore.readActiveSession()
                ?: return SendTypingResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for sendTypingStatus($conversationId)" } }

        return typingApiClient.sendTypingStatus(session, conversationId, status)
    }
}

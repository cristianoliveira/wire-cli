package wirecli.message

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider

private val logger = KotlinLogging.logger {}

class SessionBackedMessageService(
    private val sessionStore: SessionProvider,
    private val apiClient: MessageApiClient,
    private val typingApiClient: MessageTypingApiClient? = apiClient as? MessageTypingApiClient,
    private val watchApiClient: MessageWatchApiClient? = apiClient as? MessageWatchApiClient,
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

    override fun observeMessages(conversationId: String): Flow<FetchMessagesResult> {
        logger.debug { "Service operation: observeMessages(conversationId=$conversationId) started" }

        val session = sessionStore.readActiveSession()
        if (session == null) {
            logger.warn { "No active session found for observeMessages($conversationId)" }
            return flowOf(
                FetchMessagesResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ),
            )
        }

        logger.info {
            "message-watch session resolved: userId=${session.userId}, conversationId=$conversationId"
        }
        return watchApiClient?.observeMessages(session, conversationId)
            ?: flowOf(apiClient.fetchMessages(session, conversationId))
    }

    override fun searchMessages(
        query: String,
        conversationId: String?,
        limit: Int,
    ): SearchMessagesResult {
        logger.debug {
            "Service operation: searchMessages(queryLength=${query.length}, " +
                "conversationId=$conversationId, limit=$limit) started"
        }

        val session =
            sessionStore.readActiveSession()
                ?: return SearchMessagesResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for searchMessages" } }

        logger.info {
            "message-search session resolved: userId=${session.userId}"
        }
        return apiClient.searchMessages(session, query, conversationId, limit).also { result ->
            when (result) {
                is SearchMessagesResult.Success ->
                    logger.info {
                        "message-search service outcome=success queryLength=${query.length} " +
                            "count=${result.results.size}"
                    }
                is SearchMessagesResult.Failure ->
                    logger.warn {
                        "message-search service outcome=failure queryLength=${query.length} " +
                            "exitCode=${result.exitCode}"
                    }
            }
        }
    }

    override fun toggleReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
    ): ToggleReactionResult {
        logger.debug {
            "Service operation: toggleReaction(conversationId=$conversationId, " +
                "messageId=$messageId, emoji=$emoji) started"
        }

        val session =
            sessionStore.readActiveSession()
                ?: return ToggleReactionResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for toggleReaction($conversationId)" } }

        logger.info { "message-react session resolved: userId=${session.userId}" }
        return apiClient.toggleReaction(session, conversationId, messageId, emoji).also { result ->
            when (result) {
                is ToggleReactionResult.Success ->
                    logger.info {
                        "message-react service outcome=success conversationId=$conversationId " +
                            "messageId=$messageId action=${result.action}"
                    }
                is ToggleReactionResult.Failure ->
                    logger.warn {
                        "message-react service outcome=failure conversationId=$conversationId " +
                            "messageId=$messageId exitCode=${result.exitCode}"
                    }
            }
        }
    }

    override fun sendTypingStatus(
        conversationId: String,
        status: TypingStatus,
    ): SendTypingResult {
        return if (typingApiClient == null) {
            SendTypingResult.Failure(
                message = MessageUserMessages.TYPING_UNSUPPORTED,
                exitCode = MessageExitCodes.SERVER_ERROR,
            )
        } else {
            logger.debug {
                "Service operation: sendTypingStatus(conversationId=$conversationId, status=$status) started"
            }

            val session = sessionStore.readActiveSession()
            if (session == null) {
                SendTypingResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for sendTypingStatus($conversationId)" } }
            } else {
                typingApiClient.sendTypingStatus(session, conversationId, status)
            }
        }
    }
}

package wirecli.message

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

private val logger = KotlinLogging.logger {}

/**
 * Real Kalium-backed implementation of the message API client.
 *
 * This delegates message sending operations to the Kalium SDK via RealKaliumMessageRuntime.
 *
 * @invariant runtime is never null and properly initialized
 * @invariant All public methods return non-null SendMessageResult
 */
internal class RealKaliumMessageApiClient(
    private val runtime: RealKaliumMessageRuntime,
) : MessageApiClient, MessageTypingApiClient, MessageWatchApiClient {
    override fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): SendMessageResult {
        logger.info {
            "message-send api start: conversationId=$conversationId, userId=${session.userId}, textLength=${text.length}"
        }

        return when (val result = runtime.sendMessage(session, conversationId, text)) {
            is MessageStepResult.Success -> {
                logger.info { "message-send api outcome=success conversationId=$conversationId" }
                SendMessageResult.Success
            }

            is MessageStepResult.Failure -> {
                val (message, exitCode) =
                    when (result.category) {
                        MessageFailureCategory.VALIDATION ->
                            MessageUserMessages.VALIDATION_ERROR to MessageExitCodes.VALIDATION_ERROR

                        MessageFailureCategory.UNAUTHORIZED ->
                            AuthMessages.invalidOrExpiredSession() to ExitCodes.UNAUTHORIZED

                        MessageFailureCategory.TIMEOUT ->
                            MessageUserMessages.SEND_TIMEOUT to ExitCodes.NETWORK_ERROR

                        MessageFailureCategory.NETWORK ->
                            MessageUserMessages.NETWORK_ERROR to ExitCodes.NETWORK_ERROR

                        MessageFailureCategory.SERVER ->
                            MessageUserMessages.SERVER_ERROR to ExitCodes.SERVER_ERROR

                        MessageFailureCategory.NOT_FOUND ->
                            MessageUserMessages.CONVERSATION_NOT_FOUND to MessageExitCodes.NOT_FOUND

                        MessageFailureCategory.UNKNOWN ->
                            "Unknown error while sending message" to ExitCodes.UNKNOWN_ERROR
                    }

                logger.warn {
                    "message-send category mapping: category=${result.category} -> exitCode=$exitCode, " +
                        "conversationId=$conversationId"
                }
                logger.warn { "message-send api outcome=failure conversationId=$conversationId message=$message" }
                SendMessageResult.Failure(message = message, exitCode = exitCode)
            }
        }
    }

    override fun fetchMessages(
        session: AuthSession,
        conversationId: String,
    ): FetchMessagesResult {
        return when (val result = runtime.fetchMessages(session, conversationId)) {
            is MessageStepResult.Success ->
                FetchMessagesResult.Success(
                    view =
                        FetchMessagesView(
                            conversationId = conversationId,
                            messages = result.value,
                        ),
                )

            is MessageStepResult.Failure -> {
                val (message, exitCode) =
                    when (result.category) {
                        MessageFailureCategory.VALIDATION ->
                            MessageUserMessages.VALIDATION_ERROR to MessageExitCodes.VALIDATION_ERROR

                        MessageFailureCategory.UNAUTHORIZED ->
                            AuthMessages.invalidOrExpiredSession() to ExitCodes.UNAUTHORIZED

                        MessageFailureCategory.TIMEOUT ->
                            MessageUserMessages.FETCH_NETWORK_ERROR to ExitCodes.NETWORK_ERROR

                        MessageFailureCategory.NETWORK ->
                            MessageUserMessages.FETCH_NETWORK_ERROR to ExitCodes.NETWORK_ERROR

                        MessageFailureCategory.SERVER ->
                            MessageUserMessages.FETCH_SERVER_ERROR to ExitCodes.SERVER_ERROR

                        MessageFailureCategory.NOT_FOUND ->
                            MessageUserMessages.CONVERSATION_NOT_FOUND to MessageExitCodes.NOT_FOUND

                        MessageFailureCategory.UNKNOWN ->
                            MessageUserMessages.FETCH_UNKNOWN_ERROR to ExitCodes.UNKNOWN_ERROR
                    }

                FetchMessagesResult.Failure(message = message, exitCode = exitCode)
            }
        }
    }

    override fun observeMessages(
        session: AuthSession,
        conversationId: String,
    ) = runtime.observeMessages(session, conversationId)

    override fun searchMessages(
        session: AuthSession,
        query: String,
        conversationId: String?,
        limit: Int,
    ): SearchMessagesResult {
        return when (val result = runtime.searchMessages(session, query, conversationId, limit)) {
            is MessageStepResult.Success ->
                SearchMessagesResult.Success(results = result.value)

            is MessageStepResult.Failure -> {
                val (message, exitCode) =
                    when (result.category) {
                        MessageFailureCategory.VALIDATION ->
                            MessageUserMessages.SEARCH_EMPTY_QUERY to MessageExitCodes.VALIDATION_ERROR

                        MessageFailureCategory.UNAUTHORIZED ->
                            AuthMessages.invalidOrExpiredSession() to ExitCodes.UNAUTHORIZED

                        MessageFailureCategory.TIMEOUT ->
                            MessageUserMessages.SEARCH_NETWORK_ERROR to ExitCodes.NETWORK_ERROR

                        MessageFailureCategory.NETWORK ->
                            MessageUserMessages.SEARCH_NETWORK_ERROR to ExitCodes.NETWORK_ERROR

                        MessageFailureCategory.SERVER ->
                            MessageUserMessages.SEARCH_SERVER_ERROR to ExitCodes.SERVER_ERROR

                        MessageFailureCategory.NOT_FOUND ->
                            MessageUserMessages.CONVERSATION_NOT_FOUND to MessageExitCodes.NOT_FOUND

                        MessageFailureCategory.UNKNOWN ->
                            MessageUserMessages.SEARCH_SERVER_ERROR to ExitCodes.UNKNOWN_ERROR
                    }

                SearchMessagesResult.Failure(message = message, exitCode = exitCode)
            }
        }
    }

    override fun toggleReaction(
        session: AuthSession,
        conversationId: String,
        messageId: String,
        emoji: String,
    ): ToggleReactionResult {
        return when (val result = runtime.toggleReaction(session, conversationId, messageId, emoji)) {
            is MessageStepResult.Success -> ToggleReactionResult.Success(ReactionAction.ADDED)

            is MessageStepResult.Failure -> {
                val (message, exitCode) =
                    when (result.category) {
                        MessageFailureCategory.VALIDATION ->
                            MessageUserMessages.REACTION_EMOJI_BLANK to MessageExitCodes.VALIDATION_ERROR

                        MessageFailureCategory.UNAUTHORIZED ->
                            AuthMessages.invalidOrExpiredSession() to ExitCodes.UNAUTHORIZED

                        MessageFailureCategory.TIMEOUT,
                        MessageFailureCategory.NETWORK,
                        ->
                            MessageUserMessages.REACTION_NETWORK_ERROR to ExitCodes.NETWORK_ERROR

                        MessageFailureCategory.SERVER ->
                            MessageUserMessages.REACTION_SERVER_ERROR to ExitCodes.SERVER_ERROR

                        MessageFailureCategory.NOT_FOUND ->
                            MessageUserMessages.CONVERSATION_NOT_FOUND to MessageExitCodes.NOT_FOUND

                        MessageFailureCategory.UNKNOWN ->
                            MessageUserMessages.REACTION_UNKNOWN_ERROR to ExitCodes.UNKNOWN_ERROR
                    }

                ToggleReactionResult.Failure(message = message, exitCode = exitCode)
            }
        }
    }

    override fun sendTypingStatus(
        session: AuthSession,
        conversationId: String,
        status: TypingStatus,
    ): SendTypingResult {
        return when (val result = runtime.sendTypingStatus(session, conversationId, status)) {
            is MessageStepResult.Success -> SendTypingResult.Success

            is MessageStepResult.Failure -> {
                val (message, exitCode) =
                    when (result.category) {
                        MessageFailureCategory.VALIDATION ->
                            MessageUserMessages.VALIDATION_ERROR to MessageExitCodes.VALIDATION_ERROR

                        MessageFailureCategory.UNAUTHORIZED ->
                            AuthMessages.invalidOrExpiredSession() to ExitCodes.UNAUTHORIZED

                        MessageFailureCategory.TIMEOUT ->
                            MessageUserMessages.TYPING_TIMEOUT to ExitCodes.NETWORK_ERROR

                        MessageFailureCategory.NETWORK ->
                            MessageUserMessages.TYPING_NETWORK_ERROR to ExitCodes.NETWORK_ERROR

                        MessageFailureCategory.SERVER ->
                            MessageUserMessages.TYPING_SERVER_ERROR to ExitCodes.SERVER_ERROR

                        MessageFailureCategory.NOT_FOUND ->
                            MessageUserMessages.CONVERSATION_NOT_FOUND to MessageExitCodes.NOT_FOUND

                        MessageFailureCategory.UNKNOWN ->
                            MessageUserMessages.TYPING_UNKNOWN_ERROR to ExitCodes.UNKNOWN_ERROR
                    }

                SendTypingResult.Failure(message = message, exitCode = exitCode)
            }
        }
    }
}

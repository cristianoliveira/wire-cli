package wirecli.conversation

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import wirecli.shared.ConversationError
import wirecli.shared.Result

private val logger = KotlinLogging.logger {}

class SessionBackedConversationService(
    private val sessionStore: SessionProvider,
    private val apiClient: ConversationApiClient,
) : ConversationService {
    override fun listConversations(): ListConversationsResult {
        logger.debug { "Service operation: listConversations() started" }

        val session =
            sessionStore.readActiveSession()
                ?: return Result.Failure(
                    error = ConversationError(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                ).also { logger.warn { "No active session found for listConversations()" } }

        logger.debug { "Active session found, calling API client" }
        return apiClient.listConversations(session).also { result ->
            when (result) {
                is Result.Success -> {
                    val count = result.value.conversations.size
                    logger.info { "Service: Listed $count conversation(s)" }
                }
                is Result.Failure -> logger.warn { "Service: Failed to list conversations" }
            }
        }
    }

    override fun getConversation(conversationId: String): GetConversationResult {
        logger.debug { "Service operation: getConversation($conversationId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return Result.Failure(
                    error = ConversationError(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                ).also { logger.warn { "No active session found for getConversation($conversationId)" } }

        logger.debug { "Active session found, calling API client for conversation $conversationId" }
        return apiClient.getConversation(session, conversationId).also { result ->
            when (result) {
                is Result.Success -> logger.info { "Service: Retrieved conversation ${result.value.conversation.id}" }
                is Result.Failure -> logger.warn { "Service: Failed to get conversation $conversationId" }
            }
        }
    }

    override fun createConversation(
        name: String,
        type: ConversationType,
    ): CreateConversationResult {
        logger.debug { "Service operation: createConversation(name=$name, type=$type) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return Result.Failure(
                    error = ConversationError(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                ).also { logger.warn { "No active session found for createConversation($name, $type)" } }

        logger.debug { "Active session found, calling API client to create conversation" }
        return apiClient.createConversation(session, name, type).also { result ->
            when (result) {
                is Result.Success -> logger.info { "Service: Successfully created conversation: $name" }
                is Result.Failure -> logger.warn { "Service: Failed to create conversation $name" }
            }
        }
    }

    override fun deleteConversation(conversationId: String): DeleteConversationResult {
        logger.debug { "Service operation: deleteConversation($conversationId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return Result.Failure(
                    error = ConversationError(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                ).also { logger.warn { "No active session found for deleteConversation($conversationId)" } }

        logger.debug { "Active session found, calling API client to delete conversation $conversationId" }
        return apiClient.deleteConversation(session, conversationId).also { result ->
            when (result) {
                is Result.Success -> logger.info { "Service: Successfully deleted conversation $conversationId" }
                is Result.Failure -> logger.warn { "Service: Failed to delete conversation $conversationId" }
            }
        }
    }

    override fun getMemberCount(conversationId: String): GetConversationResult {
        logger.debug { "Service operation: getMemberCount($conversationId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return Result.Failure(
                    error = ConversationError(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                ).also { logger.warn { "No active session found for getMemberCount($conversationId)" } }

        logger.debug { "Active session found, calling API client for member count" }
        return apiClient.getMemberCount(session, conversationId).also { result ->
            when (result) {
                is Result.Success -> {
                    val id = result.value.conversation.id
                    logger.info { "Service: Retrieved member count for conversation $id" }
                }
                is Result.Failure -> logger.warn { "Service: Failed to get member count for conversation $conversationId" }
            }
        }
    }
}

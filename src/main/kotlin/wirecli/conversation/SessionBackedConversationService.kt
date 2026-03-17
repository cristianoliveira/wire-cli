package wirecli.conversation

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes

private val logger = KotlinLogging.logger {}

class SessionBackedConversationService(
    private val sessionStore: AuthSessionStore,
    private val apiClient: ConversationApiClient,
) : ConversationService {
    override fun listConversations(): ListConversationsResult {
        logger.debug { "Service operation: listConversations() started" }

        val session =
            sessionStore.readActiveSession()
                ?: return ListConversationsResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for listConversations()" } }

        logger.debug { "Active session found, calling API client" }
        return apiClient.listConversations(session).also { result ->
            when (result) {
                is ListConversationsResult.Success -> {
                    val count = result.view.conversations.size
                    logger.info { "Service: Listed $count conversation(s)" }
                }
                is ListConversationsResult.Failure -> logger.warn { "Service: Failed to list conversations" }
            }
        }
    }

    override fun getConversation(conversationId: String): GetConversationResult {
        logger.debug { "Service operation: getConversation($conversationId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return GetConversationResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for getConversation($conversationId)" } }

        logger.debug { "Active session found, calling API client for conversation $conversationId" }
        return apiClient.getConversation(session, conversationId).also { result ->
            when (result) {
                is GetConversationResult.Success -> logger.info { "Service: Retrieved conversation ${result.view.conversation.id}" }
                is GetConversationResult.Failure -> logger.warn { "Service: Failed to get conversation $conversationId" }
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
                ?: return CreateConversationResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for createConversation($name, $type)" } }

        logger.debug { "Active session found, calling API client to create conversation" }
        return apiClient.createConversation(session, name, type).also { result ->
            when (result) {
                is CreateConversationResult.Success -> logger.info { "Service: Successfully created conversation: $name" }
                is CreateConversationResult.Failure -> logger.warn { "Service: Failed to create conversation $name" }
            }
        }
    }

    override fun deleteConversation(conversationId: String): DeleteConversationResult {
        logger.debug { "Service operation: deleteConversation($conversationId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return DeleteConversationResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for deleteConversation($conversationId)" } }

        logger.debug { "Active session found, calling API client to delete conversation $conversationId" }
        return apiClient.deleteConversation(session, conversationId).also { result ->
            when (result) {
                is DeleteConversationResult.Success -> logger.info { "Service: Successfully deleted conversation $conversationId" }
                is DeleteConversationResult.Failure -> logger.warn { "Service: Failed to delete conversation $conversationId" }
            }
        }
    }

    override fun getMemberCount(conversationId: String): GetConversationResult {
        logger.debug { "Service operation: getMemberCount($conversationId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return GetConversationResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for getMemberCount($conversationId)" } }

        logger.debug { "Active session found, calling API client for member count" }
        return apiClient.getMemberCount(session, conversationId).also { result ->
            when (result) {
                is GetConversationResult.Success -> {
                    val id = result.view.conversation.id
                    logger.info { "Service: Retrieved member count for conversation $id" }
                }
                is GetConversationResult.Failure -> logger.warn { "Service: Failed to get member count for conversation $conversationId" }
            }
        }
    }
}

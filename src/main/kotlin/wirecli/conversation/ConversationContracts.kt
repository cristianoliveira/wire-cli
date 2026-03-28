package wirecli.conversation

import wirecli.auth.AuthSession

// Enum for conversation types
enum class ConversationType(val value: String) {
    ONE_TO_ONE("one_to_one"),
    GROUP("group"),
    TEAM_CHANNEL("team_channel"),
    ;

    override fun toString(): String = value
}

// Enum for conversation status
enum class ConversationStatus(val value: String) {
    ACTIVE("active"),
    ARCHIVED("archived"),
    DELETED("deleted"),
    ;

    override fun toString(): String = value
}

// Comprehensive conversation data class
data class Conversation(
    val id: String,
    val name: String,
    val type: ConversationType,
    val status: ConversationStatus,
    val memberCount: Int,
    val createdAt: String,
    val updatedAt: String,
)

// View model for list conversations
data class ConversationListView(
    val conversations: List<Conversation>,
)

// View model for single conversation
data class ConversationDetailView(
    val conversation: Conversation,
)

// Sealed interface for list conversations result
sealed interface ListConversationsResult {
    data class Success(val view: ConversationListView) : ListConversationsResult

    data class Failure(val message: String, val exitCode: Int) : ListConversationsResult
}

// Sealed interface for get conversation result
sealed interface GetConversationResult {
    data class Success(val view: ConversationDetailView) : GetConversationResult

    data class Failure(val message: String, val exitCode: Int) : GetConversationResult
}

// Sealed interface for create conversation result
sealed interface CreateConversationResult {
    data class Success(val view: ConversationDetailView) : CreateConversationResult

    data class Failure(val message: String, val exitCode: Int) : CreateConversationResult
}

// Sealed interface for delete conversation result
sealed interface DeleteConversationResult {
    data class Success(val message: String) : DeleteConversationResult

    data class Failure(val message: String, val exitCode: Int) : DeleteConversationResult
}

// API Client interface defining contract for conversation operations
interface ConversationApiClient {
    fun listConversations(session: AuthSession): ListConversationsResult

    fun getConversation(
        session: AuthSession,
        conversationId: String,
    ): GetConversationResult

    fun createConversation(
        session: AuthSession,
        name: String,
        type: ConversationType,
    ): CreateConversationResult

    fun deleteConversation(
        session: AuthSession,
        conversationId: String,
    ): DeleteConversationResult

    fun getMemberCount(
        session: AuthSession,
        conversationId: String,
    ): GetConversationResult
}

// Service interface for conversation operations
interface ConversationService {
    fun listConversations(): ListConversationsResult

    fun getConversation(conversationId: String): GetConversationResult

    fun createConversation(
        name: String,
        type: ConversationType,
    ): CreateConversationResult

    fun deleteConversation(conversationId: String): DeleteConversationResult

    fun getMemberCount(conversationId: String): GetConversationResult
}

// Exit codes for conversation operations
object ConversationExitCodes {
    const val OK = 0
    const val UNAUTHORIZED = 11
    const val NOT_FOUND = 13
    const val INVALID_INPUT = 14
    const val NETWORK_ERROR = 12
    const val SERVER_ERROR = 13
}

// User-friendly messages for conversation operations
internal object ConversationMessages {
    const val CONVERSATION_NOT_FOUND = "Conversation not found. Check conversation ID and try again."
    const val NETWORK_FAILURE = "Conversation fetch failed: network is unreachable. Check your connection and retry."
    const val SERVER_FAILURE = "Conversation service is unavailable. Retry later or check server settings."
    const val UNKNOWN_FAILURE = "Conversation operation failed unexpectedly. Retry and check your setup."
    const val UNAUTHORIZED_FAILURE = "Your session is invalid or expired. Please log in again."

    const val CREATE_NETWORK_FAILURE = "Conversation creation failed: network is unreachable. Check your connection and retry."
    const val CREATE_SERVER_FAILURE = "Conversation creation could not be completed. Retry later or check server settings."
    const val CREATE_UNKNOWN_FAILURE = "Conversation creation failed unexpectedly. Retry and check your setup."
    const val DELETE_NETWORK_FAILURE = "Conversation deletion failed: network is unreachable. Check your connection and retry."
    const val DELETE_SERVER_FAILURE = "Conversation deletion could not be completed. Retry later or check server settings."
    const val DELETE_UNKNOWN_FAILURE = "Conversation deletion failed unexpectedly. Retry and check your setup."

    const val INVALID_INPUT = "Invalid conversation name or parameters provided."
}

// Conversation-specific exceptions for error handling
sealed class ConversationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ConversationNotFound : ConversationException(ConversationMessages.CONVERSATION_NOT_FOUND)

    class Unauthorized(message: String = ConversationMessages.UNAUTHORIZED_FAILURE) : ConversationException(message)

    class InvalidInput(message: String = ConversationMessages.INVALID_INPUT) : ConversationException(message)

    class NetworkFailure(message: String = ConversationMessages.NETWORK_FAILURE, cause: Throwable? = null) :
        ConversationException(message, cause)

    class ServerError(message: String = ConversationMessages.SERVER_FAILURE, cause: Throwable? = null) :
        ConversationException(message, cause)

    class UnknownFailure(message: String = ConversationMessages.UNKNOWN_FAILURE, cause: Throwable? = null) :
        ConversationException(message, cause)
}

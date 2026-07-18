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

// Member role
enum class MemberRole(val value: String) {
    ADMIN("admin"),
    MEMBER("member"),
    ;

    override fun toString(): String = value
}

// Member of a conversation
data class Member(
    val id: String,
    val name: String,
    val handle: String?,
    val role: MemberRole,
)

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

// View model for members list
data class MemberListView(
    val members: List<Member>,
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

// Sealed interface for get members result
sealed interface GetMembersResult {
    data class Success(val view: MemberListView) : GetMembersResult

    data class Failure(val message: String, val exitCode: Int) : GetMembersResult
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

    fun getMembers(
        session: AuthSession,
        conversationId: String,
    ): GetMembersResult
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

    fun getMembers(conversationId: String): GetMembersResult
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

    const val MEMBERS_NETWORK_FAILURE = "Members fetch failed: network is unreachable. Check your connection and retry."
    const val MEMBERS_SERVER_FAILURE = "Members service is unavailable. Retry later or check server settings."
    const val MEMBERS_UNKNOWN_FAILURE = "Members operation failed unexpectedly. Retry and check your setup."
}

// Conversation-specific exceptions for error handling
sealed class ConversationException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ConversationNotFound(
        message: String = ConversationMessages.CONVERSATION_NOT_FOUND,
    ) : ConversationException(message)

    class Unauthorized(message: String = ConversationMessages.UNAUTHORIZED_FAILURE) : ConversationException(message)

    class InvalidInput(message: String = ConversationMessages.INVALID_INPUT) : ConversationException(message)

    class NetworkFailure(message: String = ConversationMessages.NETWORK_FAILURE, cause: Throwable? = null) :
        ConversationException(message, cause)

    class ServerError(message: String = ConversationMessages.SERVER_FAILURE, cause: Throwable? = null) :
        ConversationException(message, cause)

    class UnknownFailure(message: String = ConversationMessages.UNKNOWN_FAILURE, cause: Throwable? = null) :
        ConversationException(message, cause)
}

// Step result for runtime-level operations (SDK adapter layer)
internal sealed interface ConversationStepResult<out T> {
    data class Success<T>(val value: T) : ConversationStepResult<T>

    data class Failure(val category: ConversationFailureCategory) : ConversationStepResult<Nothing>
}

// Failure categories for runtime-level conversation operations
internal enum class ConversationFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    NOT_FOUND,
    UNKNOWN,
}

// Runtime-level interface for SDK adapters
internal interface ConversationRuntime {
    fun listConversations(session: AuthSession): ConversationStepResult<List<com.wire.kalium.logic.data.conversation.ConversationDetails>>

    fun getConversation(
        session: AuthSession,
        conversationId: String,
    ): ConversationStepResult<com.wire.kalium.logic.data.conversation.ConversationDetails>

    fun getMembers(
        session: AuthSession,
        conversationId: String,
    ): ConversationStepResult<List<com.wire.kalium.logic.data.conversation.MemberDetails>>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

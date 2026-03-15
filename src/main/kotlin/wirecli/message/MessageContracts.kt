package wirecli.message

import wirecli.auth.AuthSession

// Message status enumeration for tracking message lifecycle
enum class MessageStatus(val value: String) {
    SENT("sent"),
    PENDING("pending"),
    FAILED("failed"),
    DELETED("deleted"),
    UNKNOWN("unknown"),
    ;

    override fun toString(): String = value
}

// Conversation type enumeration
enum class ConversationType(val value: String) {
    ONE_ON_ONE("one_on_one"),
    GROUP("group"),
    CHANNEL("channel"),
    UNKNOWN("unknown"),
    ;

    override fun toString(): String = value
}

// Core message domain model representing a single message in the system
data class Message(
    val id: String,
    val text: String,
    val from: String,
    val fromName: String,
    val conversationId: String,
    val timestamp: String,
    val status: MessageStatus = MessageStatus.SENT,
    val conversationType: ConversationType = ConversationType.ONE_ON_ONE,
    val editedTimestamp: String? = null,
    val reactions: Map<String, Int> = emptyMap(),
    val mentions: List<String> = emptyList(),
)

// Request/input model for sending a message
data class MessageSendView(
    val conversationId: String,
    val text: String,
)

// Response model for fetching a single message
data class MessageDetailView(
    val message: Message,
)

// Response model for fetching a list of messages
data class MessageListView(
    val messages: List<Message>,
    val hasMore: Boolean = false,
    val nextCursor: String? = null,
)

// Sealed interface for message send operations
sealed interface MessageSendResult {
    data class Success(val message: Message) : MessageSendResult

    data class Failure(val message: String, val exitCode: Int) : MessageSendResult
}

// Sealed interface for message fetch operations (single message)
sealed interface MessageDetailResult {
    data class Success(val view: MessageDetailView) : MessageDetailResult

    data class Failure(val message: String, val exitCode: Int) : MessageDetailResult
}

// Sealed interface for message list fetch operations
sealed interface MessageListResult {
    data class Success(val view: MessageListView) : MessageListResult

    data class Failure(val message: String, val exitCode: Int) : MessageListResult
}

// API client interface defining message operations
interface MessageApiClient {
    fun sendMessage(
        session: AuthSession,
        view: MessageSendView,
    ): MessageSendResult

    fun fetchMessages(
        session: AuthSession,
        conversationId: String,
        limit: Int? = null,
        from: String? = null,
    ): MessageListResult

    fun fetchMessage(
        session: AuthSession,
        conversationId: String,
        messageId: String,
    ): MessageDetailResult
}

// Service interface for message operations (higher-level abstraction)
interface MessageService {
    fun send(
        conversationId: String,
        text: String,
    ): MessageSendResult

    fun fetch(
        conversationId: String,
        limit: Int? = null,
        from: String? = null,
    ): MessageListResult

    fun getDetail(
        conversationId: String,
        messageId: String,
    ): MessageDetailResult
}

// Exit codes for message operations
object MessageExitCodes {
    const val OK = 0
    const val UNAUTHORIZED = 11
    const val NOT_FOUND = 13
    const val INVALID_INPUT = 14
    const val CONVERSATION_NOT_FOUND = 16
    const val MESSAGE_NOT_FOUND = 17
}

// Error messages for message operations
internal object MessageMessages {
    const val NETWORK_FAILURE = "Message operation failed: network is unreachable. Check your connection and retry."
    const val SERVER_FAILURE = "Message service is unavailable. Retry later or check server settings."
    const val UNKNOWN_FAILURE = "Message operation failed unexpectedly. Retry and check your setup."
    const val UNAUTHORIZED_FAILURE = "Your session is invalid or expired. Please log in again."

    const val CONVERSATION_NOT_FOUND = "Conversation not found. Check conversation ID and try again."
    const val MESSAGE_NOT_FOUND = "Message not found. Check message ID and try again."
    const val INVALID_INPUT = "Invalid input provided (empty text or invalid conversation ID)."
    const val SEND_NETWORK_FAILURE = "Message send failed: network is unreachable. Check your connection and retry."
    const val SEND_SERVER_FAILURE = "Message could not be sent. Retry later or check server settings."
    const val SEND_UNKNOWN_FAILURE = "Message send failed unexpectedly. Retry and check your setup."
}

// Message-specific exceptions for error handling
sealed class MessageException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MessageNotFound(message: String = MessageMessages.MESSAGE_NOT_FOUND) : MessageException(message)

    class ConversationNotFound(message: String = MessageMessages.CONVERSATION_NOT_FOUND) : MessageException(message)

    class Unauthorized(message: String = MessageMessages.UNAUTHORIZED_FAILURE) : MessageException(message)

    class InvalidInput(message: String = MessageMessages.INVALID_INPUT) : MessageException(message)

    class NetworkFailure(message: String = MessageMessages.NETWORK_FAILURE, cause: Throwable? = null) :
        MessageException(message, cause)

    class ServerError(message: String = MessageMessages.SERVER_FAILURE, cause: Throwable? = null) :
        MessageException(message, cause)

    class UnknownFailure(message: String = MessageMessages.UNKNOWN_FAILURE, cause: Throwable? = null) :
        MessageException(message, cause)
}

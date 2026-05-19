package wirecli.message

import wirecli.auth.AuthSession

// Result type for message sending operation
sealed interface SendMessageResult {
    data object Success : SendMessageResult

    data class Failure(val message: String, val exitCode: Int) : SendMessageResult
}

enum class TypingStatus {
    STARTED,
    STOPPED,
}

sealed interface SendTypingResult {
    data object Success : SendTypingResult

    data class Failure(val message: String, val exitCode: Int) : SendTypingResult
}

data class ConversationMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val timestamp: String,
    val content: String,
)

data class FetchMessagesView(
    val conversationId: String,
    val messages: List<ConversationMessage>,
)

sealed interface FetchMessagesResult {
    data class Success(val view: FetchMessagesView) : FetchMessagesResult

    data class Failure(val message: String, val exitCode: Int) : FetchMessagesResult
}

data class MessageSearchResult(
    val conversationId: String,
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val timestamp: String,
    val content: String,
    val matchSnippet: String,
)

sealed interface SearchMessagesResult {
    data class Success(val results: List<MessageSearchResult>) : SearchMessagesResult

    data class Failure(val message: String, val exitCode: Int) : SearchMessagesResult
}

// Low-level API client interface - works with AuthSession directly
interface MessageApiClient {
    fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): SendMessageResult

    fun fetchMessages(
        session: AuthSession,
        conversationId: String,
    ): FetchMessagesResult

    fun searchMessages(
        session: AuthSession,
        query: String,
        conversationId: String? = null,
        limit: Int = 10,
    ): SearchMessagesResult
}

interface MessageTypingApiClient {
    fun sendTypingStatus(
        session: AuthSession,
        conversationId: String,
        status: TypingStatus,
    ): SendTypingResult
}

// High-level service interface - abstracts away session management
interface MessageService {
    fun sendMessage(
        conversationId: String,
        text: String,
    ): SendMessageResult

    fun fetchMessages(conversationId: String): FetchMessagesResult

    fun searchMessages(
        query: String,
        conversationId: String? = null,
        limit: Int = 10,
    ): SearchMessagesResult

    fun sendTypingStatus(
        conversationId: String,
        status: TypingStatus,
    ): SendTypingResult =
        SendTypingResult.Failure(
            message = MessageUserMessages.TYPING_UNSUPPORTED,
            exitCode = MessageExitCodes.SERVER_ERROR,
        )
}

// Exit codes for message operations following standard CLI conventions
object MessageExitCodes {
    const val OK = 0
    const val VALIDATION_ERROR = 14
    const val UNAUTHORIZED = 11
    const val NETWORK_ERROR = 12
    const val SERVER_ERROR = 13
    const val NOT_FOUND = 13
}

// User-friendly error messages for message operations
internal object MessageUserMessages {
    const val UNAUTHORIZED = "you must be logged in to send messages"
    const val VALIDATION_ERROR = "invalid message format or parameters"
    const val NETWORK_ERROR = "network error while sending message"
    const val SEND_TIMEOUT = "message send timed out while waiting for sync/MLS"
    const val SERVER_ERROR = "server error while sending message"
    const val CONVERSATION_NOT_FOUND = "conversation not found"
    const val MESSAGE_TOO_LONG = "message is too long"
    const val EMPTY_MESSAGE = "message cannot be empty"
    const val FETCH_NETWORK_ERROR = "network error while fetching messages"
    const val FETCH_SERVER_ERROR = "server error while fetching messages"
    const val FETCH_UNKNOWN_ERROR = "unknown error while fetching messages"
    const val TYPING_NETWORK_ERROR = "network error while sending typing status"
    const val TYPING_SERVER_ERROR = "server error while sending typing status"
    const val TYPING_TIMEOUT = "typing status send timed out while waiting for sync/MLS"
    const val TYPING_UNKNOWN_ERROR = "unknown error while sending typing status"
    const val TYPING_UNSUPPORTED = "typing status is not supported by this backend"
    const val SEARCH_NETWORK_ERROR = "network error while searching messages"
    const val SEARCH_SERVER_ERROR = "server error while searching messages"
    const val SEARCH_EMPTY_QUERY = "search query cannot be blank"
}

// Message-specific exceptions for error handling
sealed class MessageException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized(message: String = MessageUserMessages.UNAUTHORIZED) : MessageException(message)

    class ValidationError(message: String = MessageUserMessages.VALIDATION_ERROR) : MessageException(message)

    class ConversationNotFound(message: String = MessageUserMessages.CONVERSATION_NOT_FOUND) : MessageException(message)

    class NetworkFailure(message: String = MessageUserMessages.NETWORK_ERROR, cause: Throwable? = null) :
        MessageException(message, cause)

    class ServerError(message: String = MessageUserMessages.SERVER_ERROR, cause: Throwable? = null) :
        MessageException(message, cause)

    class UnknownFailure(message: String) : MessageException(message)
}

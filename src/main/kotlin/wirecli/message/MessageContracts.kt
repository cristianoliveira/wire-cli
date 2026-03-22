package wirecli.message

import wirecli.auth.AuthSession
import wirecli.shared.Result
import wirecli.shared.MessageError

// Type aliases for module-specific Result types
typealias MessageResult<T> = Result<T, MessageError>

enum class TypingStatus {
    STARTED,
    STOPPED,
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

// Low-level API client interface - works with AuthSession directly
interface MessageApiClient {
    fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): MessageResult<Unit>

    fun fetchMessages(
        session: AuthSession,
        conversationId: String,
    ): MessageResult<FetchMessagesView>
}

interface MessageTypingApiClient {
    fun sendTypingStatus(
        session: AuthSession,
        conversationId: String,
        status: TypingStatus,
    ): MessageResult<Unit>
}

// High-level service interface - abstracts away session management
interface MessageService {
    fun sendMessage(
        conversationId: String,
        text: String,
    ): MessageResult<Unit>

    fun fetchMessages(conversationId: String): MessageResult<FetchMessagesView>

    fun sendTypingStatus(
        conversationId: String,
        status: TypingStatus,
    ): MessageResult<Unit> =
        MessageResult.Failure(
            error = MessageError(
                message = MessageUserMessages.TYPING_UNSUPPORTED,
                exitCode = MessageExitCodes.SERVER_ERROR,
            ),
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

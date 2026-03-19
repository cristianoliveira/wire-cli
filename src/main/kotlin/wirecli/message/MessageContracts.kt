package wirecli.message

import wirecli.auth.AuthSession

// Result type for message sending operation
sealed interface SendMessageResult {
    data object Success : SendMessageResult

    data class Failure(val message: String, val exitCode: Int) : SendMessageResult
}

// Low-level API client interface - works with AuthSession directly
interface MessageApiClient {
    fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): SendMessageResult
}

// High-level service interface - abstracts away session management
interface MessageService {
    fun sendMessage(
        conversationId: String,
        text: String,
    ): SendMessageResult
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
    const val SERVER_ERROR = "server error while sending message"
    const val CONVERSATION_NOT_FOUND = "conversation not found"
    const val MESSAGE_TOO_LONG = "message is too long"
    const val EMPTY_MESSAGE = "message cannot be empty"
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

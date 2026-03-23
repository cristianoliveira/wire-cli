package wirecli.message

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.shared.MessageError
import wirecli.shared.Result

enum class StubMode {
    SUCCESS,
    UNAUTHORIZED,
    NETWORK_ERROR,
    SERVER_ERROR,
    VALIDATION_ERROR,
    CONVERSATION_NOT_FOUND,
}

class StubMessageApiClient(
    private val environment: Map<String, String> = emptyMap(),
) : MessageApiClient, MessageTypingApiClient {
    // Support both old constructor (for backward compatibility) and environment-based mode
    constructor(mode: StubMode) : this(mapOf("__mode__" to mode.name))

    private val mode: StubMode by lazy {
        val modeString = environment["WIRE_STUB_MODE"] ?: environment["__mode__"] ?: return@lazy StubMode.SUCCESS
        StubMode.entries.firstOrNull { it.name.lowercase().replace("_", "-") == modeString.lowercase().replace("_", "-") }
            ?: run {
                // Try exact match
                StubMode.entries.firstOrNull { it.name == modeString.uppercase() }
                    ?: StubMode.SUCCESS
            }
    }

    override fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): MessageResult<Unit> {
        return when (mode) {
            StubMode.SUCCESS ->
                Result.Success(Unit)

            StubMode.UNAUTHORIZED ->
                Result.Failure(
                    error = MessageError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            StubMode.NETWORK_ERROR ->
                Result.Failure(
                    error = MessageError(
                        message = MessageUserMessages.NETWORK_ERROR,
                        exitCode = ExitCodes.NETWORK_ERROR,
                    ),
                )

            StubMode.SERVER_ERROR ->
                Result.Failure(
                    error = MessageError(
                        message = MessageUserMessages.SERVER_ERROR,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            StubMode.VALIDATION_ERROR ->
                Result.Failure(
                    error = MessageError(
                        message = MessageUserMessages.VALIDATION_ERROR,
                        exitCode = MessageExitCodes.VALIDATION_ERROR,
                    ),
                )

            StubMode.CONVERSATION_NOT_FOUND ->
                Result.Failure(
                    error = MessageError(
                        message = MessageUserMessages.CONVERSATION_NOT_FOUND,
                        exitCode = MessageExitCodes.NOT_FOUND,
                    ),
                )
        }
    }

    override fun fetchMessages(
        session: AuthSession,
        conversationId: String,
    ): MessageResult<FetchMessagesView> {
        return when (mode) {
            StubMode.SUCCESS ->
                Result.Success(
                    value =
                        FetchMessagesView(
                            conversationId = conversationId,
                            messages =
                                listOf(
                                    ConversationMessage(
                                        id = "msg-001",
                                        senderId = "alice@example.com",
                                        senderName = "Alice",
                                        timestamp = "2026-03-20T10:00:00Z",
                                        content = "Hello from stub",
                                    ),
                                    ConversationMessage(
                                        id = "msg-002",
                                        senderId = "bob@example.com",
                                        senderName = "Bob",
                                        timestamp = "2026-03-20T10:01:00Z",
                                        content = "Reply from stub",
                                    ),
                                ),
                    ),
                )

            StubMode.UNAUTHORIZED ->
                Result.Failure(
                    error = MessageError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            StubMode.NETWORK_ERROR ->
                Result.Failure(
                    error = MessageError(
                        message = MessageUserMessages.FETCH_NETWORK_ERROR,
                        exitCode = ExitCodes.NETWORK_ERROR,
                    ),
                )

            StubMode.SERVER_ERROR ->
                Result.Failure(
                    error = MessageError(
                        message = MessageUserMessages.FETCH_SERVER_ERROR,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            StubMode.VALIDATION_ERROR ->
                Result.Failure(
                    error = MessageError(
                        message = MessageUserMessages.VALIDATION_ERROR,
                        exitCode = MessageExitCodes.VALIDATION_ERROR,
                    ),
                )

            StubMode.CONVERSATION_NOT_FOUND ->
                Result.Failure(
                    error = MessageError(
                        message = MessageUserMessages.CONVERSATION_NOT_FOUND,
                        exitCode = MessageExitCodes.NOT_FOUND,
                    ),
                )
        }
    }

    override fun sendTypingStatus(
        session: AuthSession,
        conversationId: String,
        status: TypingStatus,
    ): MessageResult<Unit> {
        return when (mode) {
            StubMode.SUCCESS -> Result.Success(value = Unit)

            StubMode.UNAUTHORIZED ->
                Result.Failure(
                    error = MessageError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            StubMode.NETWORK_ERROR ->
                Result.Failure(
                    error = MessageError(
                        message = MessageUserMessages.TYPING_NETWORK_ERROR,
                        exitCode = ExitCodes.NETWORK_ERROR,
                    ),
                )

            StubMode.SERVER_ERROR,
            StubMode.VALIDATION_ERROR,
            StubMode.CONVERSATION_NOT_FOUND,
            ->
                Result.Failure(
                    error = MessageError(
                        message = MessageUserMessages.TYPING_SERVER_ERROR,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )
        }
    }
}

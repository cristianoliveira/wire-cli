package wirecli.message

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

enum class StubMode {
    SUCCESS,
    UNAUTHORIZED,
    NETWORK_ERROR,
    SERVER_ERROR,
    VALIDATION_ERROR,
    CONVERSATION_NOT_FOUND,
}

class StubMessageApiClient(
    private val mode: StubMode,
) : MessageApiClient {
    override fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): SendMessageResult {
        return when (mode) {
            StubMode.SUCCESS ->
                SendMessageResult.Success

            StubMode.UNAUTHORIZED ->
                SendMessageResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            StubMode.NETWORK_ERROR ->
                SendMessageResult.Failure(
                    message = MessageUserMessages.NETWORK_ERROR,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            StubMode.SERVER_ERROR ->
                SendMessageResult.Failure(
                    message = MessageUserMessages.SERVER_ERROR,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            StubMode.VALIDATION_ERROR ->
                SendMessageResult.Failure(
                    message = MessageUserMessages.VALIDATION_ERROR,
                    exitCode = MessageExitCodes.VALIDATION_ERROR,
                )

            StubMode.CONVERSATION_NOT_FOUND ->
                SendMessageResult.Failure(
                    message = MessageUserMessages.CONVERSATION_NOT_FOUND,
                    exitCode = MessageExitCodes.NOT_FOUND,
                )
        }
    }
}

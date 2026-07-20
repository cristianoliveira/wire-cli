package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import wirecli.auth.ExitCodes
import wirecli.message.MessageService
import wirecli.message.MessageUserMessages
import wirecli.message.SetMessageReadResult

private val logger = KotlinLogging.logger {}

class MessageSetCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "set",
        help = "Set message state.",
        epilog =
            """
            EXAMPLE:
              Mark a conversation as read through a message:
                wire message set <conversation-id> --read <message-id>

              Mark as read with JSON output:
                wire message set <conversation-id> --read <message-id> --json
            """.trimIndent(),
    ) {
    private val conversationId by argument(
        name = "CONVERSATION",
        help = "The conversation ID.",
    )

    private val readMessageId by option(
        "--read",
        metavar = "MESSAGE",
        help = "Mark the conversation as read through this message ID.",
    ).required()

    private val jsonOutput by option(
        "--json",
        help = "Output result as JSON.",
    ).flag()

    override fun run() {
        val validatedConversationId = requireMessageSetValue(conversationId, "conversation-id required")
        val validatedMessageId = requireMessageSetValue(readMessageId, "message-id required")

        logger.info {
            "message-set-read invoked: conversationId=$validatedConversationId, messageId=$validatedMessageId"
        }

        when (val result = messageServiceProvider().setMessageRead(validatedConversationId, validatedMessageId)) {
            is SetMessageReadResult.Success ->
                echo(formatResult(validatedConversationId, validatedMessageId, "applied"))
            is SetMessageReadResult.AlreadyRead ->
                echo(formatResult(validatedConversationId, validatedMessageId, "already_read"))
            is SetMessageReadResult.Failure -> {
                logger.warn { "message-set-read failed: exitCode=${result.exitCode}" }
                if (jsonOutput) {
                    emitStructuredErrorAndExit(
                        error =
                            StructuredCommandError(
                                code = errorCode(result.exitCode, result.message),
                                message = result.message,
                                retryable = result.exitCode == ExitCodes.NETWORK_ERROR,
                                next = retryCommand(result.exitCode, validatedConversationId, validatedMessageId),
                            ),
                        exitCode = result.exitCode,
                    )
                }
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }

    private fun requireMessageSetValue(
        value: String,
        errorMessage: String,
    ): String {
        if (value.isNotBlank()) return value
        if (jsonOutput) {
            emitStructuredErrorAndExit(
                error = StructuredCommandError("validation_error", errorMessage, retryable = false),
                exitCode = ExitCodes.VALIDATION_ERROR,
            )
        }
        return requireValueOrExit(value, "Value", errorMessage)
    }

    private fun errorCode(
        exitCode: Int,
        message: String,
    ): String =
        when {
            exitCode == ExitCodes.NETWORK_ERROR -> "network_error"
            exitCode == ExitCodes.UNAUTHORIZED -> "auth_error"
            message == MessageUserMessages.MESSAGE_NOT_FOUND -> "not_found"
            exitCode == ExitCodes.SERVER_ERROR -> "server_error"
            else -> "unknown_error"
        }

    private fun retryCommand(
        exitCode: Int,
        conversationId: String,
        messageId: String,
    ): String? =
        if (exitCode == ExitCodes.NETWORK_ERROR) {
            "wire message set $conversationId --read $messageId --json"
        } else {
            null
        }

    private fun formatResult(
        conversationId: String,
        messageId: String,
        outcome: String,
    ): String =
        if (jsonOutput) {
            buildJsonObject {
                put("conversationId", JsonPrimitive(conversationId))
                put("messageId", JsonPrimitive(messageId))
                put("state", JsonPrimitive("read"))
                put("outcome", JsonPrimitive(outcome))
            }.toString()
        } else if (outcome == "already_read") {
            "Message already marked as read."
        } else {
            "Message marked as read."
        }
}

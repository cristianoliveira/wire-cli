package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.message.MessageService
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

    override fun run() {
        val validatedConversationId =
            requireValueOrExit(
                value = conversationId,
                fieldName = "Conversation-id",
                errorMessage = "conversation-id required",
            )
        val validatedMessageId =
            requireValueOrExit(
                value = readMessageId,
                fieldName = "Message-id",
                errorMessage = "message-id required",
            )

        logger.info {
            "message-set-read invoked: conversationId=$validatedConversationId, messageId=$validatedMessageId"
        }

        when (val result = messageServiceProvider().setMessageRead(validatedConversationId, validatedMessageId)) {
            is SetMessageReadResult.Success -> echo("Message marked as read.")
            is SetMessageReadResult.Failure -> {
                logger.warn { "message-set-read failed: exitCode=${result.exitCode}" }
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }
}

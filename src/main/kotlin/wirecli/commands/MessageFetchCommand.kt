package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.ExitCodes
import wirecli.message.FetchMessagesResult
import wirecli.message.MessageFetchFormatter
import wirecli.message.MessageService

private val logger = KotlinLogging.logger {}

class MessageFetchCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "fetch",
        help = "Fetch messages from a conversation.",
    ) {
    private val conversationId by argument(name = "CONVERSATION_ID", help = "The conversation ID to fetch messages from")

    override fun run() {
        if (conversationId.isBlank()) {
            logger.warn { "Validation failed: blank conversation ID for message fetch" }
            echo("validation error: conversation required", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }

        val messageService = messageServiceProvider()
        when (val result = messageService.fetchMessages(conversationId)) {
            is FetchMessagesResult.Success -> {
                val formatter = MessageFetchFormatter()
                val output = formatter.toHumanReadable(result.view.messages)
                echo(output)
            }

            is FetchMessagesResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

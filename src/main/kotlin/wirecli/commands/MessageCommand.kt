package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.message.MessageService

private val logger = KotlinLogging.logger {}

class MessageCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "message",
        help = "Send and manage messages (send, fetch, watch).",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            MessageSendCommand(messageServiceProvider),
            MessageFetchCommand(messageServiceProvider),
            MessageWatchCommand(messageServiceProvider),
        )
    }

    override fun run() {
        logger.info { "Message command executed" }
        logger.debug { "Current subcommand: ${currentContext.invokedSubcommand}" }

        if (currentContext.invokedSubcommand == null) {
            logger.warn { "No subcommand specified for message command" }
            echo("No subcommand specified. Use 'wire message --help' for available commands.")
            throw ProgramResult(0)
        }

        logger.debug { "Message subcommand routing to: ${currentContext.invokedSubcommand}" }
    }
}

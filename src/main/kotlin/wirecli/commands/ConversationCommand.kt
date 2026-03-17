package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.conversation.ConversationService

private val logger = KotlinLogging.logger {}

class ConversationCommand(
    private val conversationServiceProvider: () -> ConversationService,
) : CliktCommand(
        name = "conversation",
        help = "Manage conversations (list, get).",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            ConversationListCommand(conversationServiceProvider),
            ConversationGetCommand(conversationServiceProvider),
        )
    }

    override fun run() {
        logger.info { "Conversation command executed" }
        logger.debug { "Current subcommand: ${currentContext.invokedSubcommand}" }

        if (currentContext.invokedSubcommand == null) {
            logger.warn { "No subcommand specified for conversation command" }
            echo("No subcommand specified. Use 'wire conversation --help' for available commands.")
            throw ProgramResult(0)
        }

        logger.debug { "Conversation subcommand routing to: ${currentContext.invokedSubcommand}" }
    }
}

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import wirecli.conversation.ConversationService

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
        if (currentContext.invokedSubcommand == null) {
            echo("No subcommand specified. Use 'wire conversation --help' for available commands.")
            throw ProgramResult(0)
        }
    }
}

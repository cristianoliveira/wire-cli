package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import wirecli.message.MessageService

/**
 * Root command for message operations.
 *
 * Groups message-related subcommands:
 * - send: Send a message to a conversation
 * - fetch: Fetch messages from a conversation
 *
 * @invariant messageServiceProvider returns non-null MessageService
 */
class MessageCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "message",
        help = "Manage messages (send, fetch).",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            MessageSendCommand(messageServiceProvider),
            MessageFetchCommand(messageServiceProvider),
        )
    }

    /**
     * Executes when MessageCommand is invoked without a subcommand.
     *
     * @throws ProgramResult with exit code 0 (info output)
     */
    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            echo("No subcommand specified. Use 'wire message --help' for available commands.")
            throw ProgramResult(0)
        }
    }
}

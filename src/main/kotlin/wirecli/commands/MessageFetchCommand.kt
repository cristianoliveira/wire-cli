package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.message.MessageFormatter
import wirecli.message.MessageListResult
import wirecli.message.MessageService

/**
 * CLI command to fetch messages from a conversation.
 *
 * Arguments:
 * - conversationId (required): The ID of the conversation to fetch messages from
 *
 * Options:
 * - --limit (Int): Maximum number of messages to return (default: 50)
 * - --from (String): Cursor for pagination (message ID to start from)
 * - --format: Output format (text|json|jsonlines) - default: text
 *
 * Supports multiple output formats:
 * - Text format (default): Human-readable table of messages
 * - JSON format: JSON array of message objects
 * - JSON Lines format: One message per line in JSON format
 *
 * Pagination is supported via the --from option which specifies a message ID
 * to start from. The result includes a nextCursor if more messages are available.
 *
 * @invariant messageServiceProvider returns non-null MessageService
 * @invariant conversationId is never empty
 * @invariant limit is always positive if provided
 * @invariant Output format is exactly one of: text, json, or json-lines
 */
class MessageFetchCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(name = "fetch", help = "Fetch messages from a conversation.") {
    private val conversationId by argument(
        name = "conversation-id",
        help = "The ID of the conversation to fetch messages from",
    )
    private val limit by option(
        "--limit",
        help = "Maximum number of messages to return (default: 50)",
    ).default("50")
    private val from by option(
        "--from",
        help = "Cursor for pagination (message ID to start from)",
    )
    private val format by option(
        "--format",
        help = "Output format: text, json, or jsonlines (default: text)",
    ).default("text")

    /**
     * Executes the message fetch command.
     *
     * Fetches messages from the service and outputs in the requested format.
     * On success, outputs formatted message list. On failure, prints error and exits.
     *
     * @throws ProgramResult on API failure with appropriate exit code
     *
     * @pre messageServiceProvider must return non-null service
     * @pre conversationId must be non-empty
     * @pre limit must be a valid positive integer string
     * @post Output is exactly one format: text, JSON, or JSON Lines
     * @post If failure, error message is redacted before output
     */
    override fun run() {
        val messageService = messageServiceProvider()

        // Parse limit as integer, default to 50 if invalid
        val limitValue = limit.toIntOrNull() ?: 50

        val result =
            messageService.fetch(
                conversationId = conversationId,
                limit = limitValue,
                from = from,
            )

        when (result) {
            is MessageListResult.Success -> {
                val formatter = MessageFormatter()
                val output = formatter.formatMessageList(result.view.messages, format)
                echo(output)

                // If there are more messages available, output pagination info
                if (result.view.hasMore && result.view.nextCursor != null) {
                    echo("", err = false)
                    echo("More messages available. Use --from ${result.view.nextCursor} to fetch next page.", err = false)
                }
            }

            is MessageListResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

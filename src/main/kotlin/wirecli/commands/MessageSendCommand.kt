package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.message.MessageFormatter
import wirecli.message.MessageSendResult
import wirecli.message.MessageService

/**
 * CLI command to send a message to a conversation.
 *
 * Arguments:
 * - conversationId (required): The ID of the target conversation
 * - message text (required): The message text to send
 *
 * Options:
 * - --format: Output format (text|json|jsonlines) - default: text
 *
 * Supports multiple output formats:
 * - Text format (default): Human-readable message details
 * - JSON format: Single JSON object representation
 * - JSON Lines format: One JSON object per line
 *
 * @invariant messageServiceProvider returns non-null MessageService
 * @invariant conversationId and text are never empty
 * @invariant Output format is exactly one of: text, json, or json-lines
 */
class MessageSendCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(name = "send", help = "Send a message to a conversation.") {
    private val conversationId by argument(
        name = "conversation-id",
        help = "The ID of the conversation to send the message to",
    )
    private val text by argument(
        name = "text",
        help = "The message text to send",
    )
    private val format by option(
        "--format",
        help = "Output format: text, json, or jsonlines (default: text)",
    ).default("text")

    /**
     * Executes the message send command.
     *
     * Sends the message via the service and outputs the result in the requested format.
     * On success, outputs formatted message. On failure, prints error and exits.
     *
     * @throws ProgramResult on API failure with appropriate exit code
     *
     * @pre messageServiceProvider must return non-null service
     * @pre conversationId and text must be non-empty
     * @post Output is exactly one format: text, JSON, or JSON Lines
     * @post If failure, error message is redacted before output
     */
    override fun run() {
        val messageService = messageServiceProvider()
        val result = messageService.send(conversationId, text)

        when (result) {
            is MessageSendResult.Success -> {
                val formatter = MessageFormatter()
                val output = formatter.formatSendResult(result.message, format)
                echo(output)
            }

            is MessageSendResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import wirecli.auth.ExitCodes
import wirecli.message.ListRecentMessagesResult
import wirecli.message.MessageFetchFormatter
import wirecli.message.MessageService

private const val DEFAULT_RECENT_MESSAGES_LIMIT = 10
private const val MAX_RECENT_MESSAGES_LIMIT = 100

class MessageListCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "list",
        help = "List recent messages across conversations.",
    ) {
    private val limit by option("--limit", "-n", help = "Maximum number of results (default: 10).").int()
    private val receivedOnly by option("--received-only", help = "Only include messages received from others.").flag(default = false)
    private val localOnly by option("--local", help = "Only read from local cache.").flag(default = false)
    private val jsonOutput by option("--json", help = "Output results as JSON.").flag(default = false)
    private val jsonLinesOutput by option("--json-lines", help = "Output one JSON object per line.").flag(default = false)

    override fun run() {
        validateStructuredOutputOrExit(jsonOutput, jsonLinesOutput)
        val resolvedLimit = validateLimitOrExit(limit ?: DEFAULT_RECENT_MESSAGES_LIMIT)
        val formatter = MessageFetchFormatter()
        val messageService = messageServiceProvider()

        when (val result = messageService.listRecentMessages(resolvedLimit, receivedOnly, localOnly)) {
            is ListRecentMessagesResult.Success -> {
                val output =
                    when {
                        jsonLinesOutput -> formatter.toJsonLinesRecent(result.view.messages)
                        jsonOutput -> formatter.toJsonRecent(result.view.messages)
                        else -> formatter.toHumanReadableRecent(result.view.messages)
                    }
                echo(output)
            }
            is ListRecentMessagesResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }

    private fun validateLimitOrExit(limit: Int): Int {
        if (limit in 1..MAX_RECENT_MESSAGES_LIMIT) return limit

        echo("validation error: limit must be between 1 and $MAX_RECENT_MESSAGES_LIMIT", err = true)
        throw ProgramResult(ExitCodes.VALIDATION_ERROR)
    }
}

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
import wirecli.message.RecentMessagesQuery
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

private const val DEFAULT_RECENT_MESSAGES_LIMIT = 10
private const val MAX_RECENT_MESSAGES_LIMIT = 100

class MessageListCommand(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "list",
        help = "List recent messages across conversations.",
        epilog =
            """
            EXAMPLES:
              List the 10 most recent messages:
                wire message list
            
              List 20 recent messages in JSON:
                wire message list --limit 20 --json
            
              List only received messages from today:
                wire message list --received-only --since today

              List messages that mention you in one conversation:
                wire message list --conversation-id conv-123 --mentions-me --json
            """.trimIndent(),
    ) {
    private val limit by option("--limit", "-n", help = "Maximum number of results (1-100, default: 10).").int()
    private val receivedOnly by option("--received-only", help = "Only include messages received from others.").flag(default = false)
    private val since by option(
        "--since",
        help = "Only include messages at or after 'today', an ISO-8601 date, or an ISO-8601 timestamp.",
    )
    private val conversationId by option("--conversation-id", help = "Only include messages from this conversation ID.")
    private val mentionsMe by option("--mentions-me", help = "Only include messages that explicitly mention you.").flag(default = false)
    private val noCache by option("--no-cache", help = "Bypass the daemon cache and fetch from Wire.").flag(default = false)
    private val jsonOutput by option("--json", help = "Output results as JSON.").flag(default = false)
    private val jsonLinesOutput by option("--json-lines", help = "Output one JSON object per line.").flag(default = false)
    private val fullContent by option("--full", help = "Show full message content without truncation.").flag(default = false)

    override fun run() {
        validateStructuredOutputOrExit(jsonOutput, jsonLinesOutput)
        val resolvedLimit = validateLimitOrExit(limit ?: DEFAULT_RECENT_MESSAGES_LIMIT)
        val resolvedConversationId =
            conversationId?.let {
                requireValueOrExit(
                    value = it,
                    fieldName = "Conversation ID",
                    errorMessage = "conversation required",
                )
            }
        val query =
            RecentMessagesQuery(
                limit = resolvedLimit,
                receivedOnly = receivedOnly,
                since = parseSinceOrExit(since),
                conversationId = resolvedConversationId,
                mentionsMe = mentionsMe,
            )
        val formatter = MessageFetchFormatter()
        val messageService = messageServiceProvider()

        val result =
            if (noCache) {
                messageService.listServerRecentMessages(query)
            } else {
                messageService.listRecentMessages(query)
            }
        when (result) {
            is ListRecentMessagesResult.Success -> {
                val output =
                    when {
                        jsonLinesOutput -> formatter.toJsonLinesRecent(result.view.messages, fullContent)
                        jsonOutput -> formatter.toJsonRecent(result.view.messages, fullContent)
                        else -> formatter.toHumanReadableRecent(result.view.messages, fullContent)
                    }
                echo(output)
            }
            is ListRecentMessagesResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }

    private fun parseSinceOrExit(value: String?): Instant? {
        if (value == null) return null
        if (value.equals("today", ignoreCase = true)) {
            return LocalDate.now(clock).atStartOfDay(clock.zone).toInstant()
        }

        val parsed =
            listOf<() -> Instant>(
                { Instant.parse(value) },
                { OffsetDateTime.parse(value).toInstant() },
                { LocalDate.parse(value).atStartOfDay(clock.zone).toInstant() },
            ).firstNotNullOfOrNull { parser -> runCatching(parser).getOrNull() }
        if (parsed != null) return parsed

        echo(
            "validation error: since must be 'today', an ISO-8601 date, or an ISO-8601 timestamp",
            err = true,
        )
        throw ProgramResult(ExitCodes.VALIDATION_ERROR)
    }

    private fun validateLimitOrExit(limit: Int): Int {
        if (limit in 1..MAX_RECENT_MESSAGES_LIMIT) return limit

        echo("validation error: limit must be between 1 and $MAX_RECENT_MESSAGES_LIMIT", err = true)
        throw ProgramResult(ExitCodes.VALIDATION_ERROR)
    }
}

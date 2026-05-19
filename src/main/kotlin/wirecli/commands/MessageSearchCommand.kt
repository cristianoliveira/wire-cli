package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.ExitCodes
import wirecli.message.MessageSearchResult
import wirecli.message.MessageService
import wirecli.message.SearchMessagesResult

private const val DEFAULT_SEARCH_LIMIT = 10
private const val MAX_SEARCH_LIMIT = 100

private val logger = KotlinLogging.logger {}

class MessageSearchCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "search",
        help = "Search messages globally or within a conversation.",
        epilog =
            """
            EXAMPLES:
              Search all conversations:
                wire message search "hello"
              
              Search within a specific conversation:
                wire message search "hello" --conversation-id <id>
              
              Search with JSON output:
                wire message search "hello" --json
              
              Limit results:
                wire message search "hello" --limit 5
            """.trimIndent(),
    ) {
    private val query by argument(
        name = "QUERY",
        help = "Search query text.",
    )

    private val conversationId by option(
        "--conversation-id",
        "-c",
        help = "Limit search to a specific conversation.",
    )

    private val limit by option(
        "--limit",
        "-n",
        help = "Maximum number of results (default: 10).",
    ).int()

    private val jsonOutput by option(
        "--json",
        help = "Output results as JSON.",
    ).flag()

    override fun run() {
        val validatedQuery =
            requireValueOrExit(
                value = query,
                fieldName = "Query",
                errorMessage = "search query required",
            )

        logger.info {
            "message-search invoked: queryLength=${validatedQuery.length}, " +
                "conversationId=$conversationId, limit=$limit, json=$jsonOutput"
        }

        val messageService = messageServiceProvider()
        val resolvedLimit = validateLimitOrExit(limit ?: DEFAULT_SEARCH_LIMIT)
        when (val result = messageService.searchMessages(validatedQuery, conversationId, resolvedLimit)) {
            is SearchMessagesResult.Success -> {
                when {
                    jsonOutput -> echo(formatResults(result.results, jsonOutput = true))
                    result.results.isEmpty() -> {
                        logger.info {
                            "message-search outcome=success, no results, " +
                                "queryLength=${validatedQuery.length}"
                        }
                        echo("No messages found for \"$validatedQuery\".")
                    }
                    else -> {
                        logger.info { "message-search outcome=success, count=${result.results.size}" }
                        echo(formatResults(result.results, jsonOutput = false))
                    }
                }
            }

            is SearchMessagesResult.Failure -> {
                logger.warn {
                    "message-search outcome=failure exitCode=${result.exitCode} message=${result.message}"
                }
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun validateLimitOrExit(limit: Int): Int {
        if (limit in 1..MAX_SEARCH_LIMIT) return limit

        echo("validation error: limit must be between 1 and $MAX_SEARCH_LIMIT", err = true)
        throw ProgramResult(ExitCodes.VALIDATION_ERROR)
    }

    private fun formatResults(
        results: List<MessageSearchResult>,
        jsonOutput: Boolean,
    ): String {
        if (jsonOutput) {
            val items =
                results.joinToString(",") { result ->
                    val escapedContent =
                        result.content
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                    val escapedSnippet =
                        result.matchSnippet
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", "\\n")
                    val escapedSender =
                        result.senderName
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                    buildString {
                        append("{")
                        append("\"conversationId\":\"${result.conversationId}\",")
                        append("\"messageId\":\"${result.messageId}\",")
                        append("\"senderId\":\"${result.senderId}\",")
                        append("\"senderName\":\"$escapedSender\",")
                        append("\"timestamp\":\"${result.timestamp}\",")
                        append("\"content\":\"$escapedContent\",")
                        append("\"matchSnippet\":\"$escapedSnippet\"")
                        append("}")
                    }
                }
            return "[$items]"
        }
        return results.joinToString("\n") { result ->
            val sender = result.senderName.ifBlank { result.senderId }
            "[${result.timestamp}] $sender (${result.conversationId}): ${result.matchSnippet}"
        }
    }
}

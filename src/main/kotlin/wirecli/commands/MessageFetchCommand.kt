package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import wirecli.auth.ExitCodes
import wirecli.message.FetchMessagesResult
import wirecli.message.MessageFetchFormatter
import wirecli.message.MessageService

private const val DEFAULT_FETCH_MESSAGES_LIMIT = 10
private const val MAX_FETCH_MESSAGES_LIMIT = 100

class MessageFetchCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "fetch",
        help = "Fetch messages from a conversation.",
        epilog =
            """
            EXAMPLES:
              Fetch messages as JSON:
                wire message fetch --json conv-123

              Return only the 5 latest fetched messages:
                wire message fetch --limit 5 conv-123
            """.trimIndent(),
    ) {
    private val conversationId by argument(
        name = "CONVERSATION_ID",
        help = "The conversation ID to fetch messages from",
    )

    private val noCache by option(
        "--no-cache",
        help = "Bypass the daemon cache and fetch from Wire.",
    ).flag(default = false)

    private val limit by option(
        "--limit",
        "-n",
        help = "Maximum number of fetched messages to output (1-100).",
    ).int()

    private val jsonOutput by option(
        "--json",
        help = "Output messages as JSON.",
    ).flag(default = false)

    override fun run() {
        val validatedConversationId =
            requireValueOrExit(
                value = conversationId,
                fieldName = "Conversation ID",
                errorMessage = "conversation required",
            )

        val resolvedLimit = limit?.let(::validateLimitOrExit) ?: DEFAULT_FETCH_MESSAGES_LIMIT
        val fetchLimit = if (limit == null) resolvedLimit else resolvedLimit + 1
        val messageService = messageServiceProvider()
        val result =
            if (noCache) {
                messageService.fetchServerMessages(validatedConversationId, fetchLimit)
            } else {
                messageService.fetchMessages(validatedConversationId, fetchLimit)
            }
        when (result) {
            is FetchMessagesResult.Success -> {
                val formatter = MessageFetchFormatter()
                val messages = result.view.messages.takeLast(resolvedLimit)
                val output =
                    if (jsonOutput) {
                        formatter.toJson(
                            conversationId = result.view.conversationId,
                            messages = messages,
                            truncated = messages.size < result.view.messages.size,
                        )
                    } else {
                        formatter.toHumanReadable(messages)
                    }
                echo(output)
            }

            is FetchMessagesResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }

    private fun validateLimitOrExit(limit: Int): Int {
        if (limit in 1..MAX_FETCH_MESSAGES_LIMIT) return limit

        echo("validation error: limit must be between 1 and $MAX_FETCH_MESSAGES_LIMIT", err = true)
        throw ProgramResult(ExitCodes.VALIDATION_ERROR)
    }
}

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.ExitCodes
import wirecli.message.FetchMessagesResult
import wirecli.message.MessageService
import wirecli.message.MessageUserMessages
import kotlin.math.min

private val logger = KotlinLogging.logger {}

private const val DEFAULT_WATCH_POLL_INTERVAL_MS = 1_000L
private const val MAX_RETRY_BACKOFF_MS = 15_000L

class MessageWatchCommand(
    private val messageServiceProvider: () -> MessageService,
    private val pollIntervalMs: Long = DEFAULT_WATCH_POLL_INTERVAL_MS,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val keepWatching: () -> Boolean = { true },
) : CliktCommand(
        name = "watch",
        help = "Watch a conversation and stream incoming messages.",
        epilog =
            """
            EXAMPLES:
              Stream incoming messages in a conversation:
                wire message watch <conversation-id>

              React to each incoming message from the stream:
                wire message watch <conversation-id> | while IFS= read -r MSG; do
                  echo "${'$'}MSG"
                done
            """.trimIndent(),
    ) {
    private val conversationId by argument(name = "CONVERSATION_ID", help = "The conversation ID to watch")

    override fun run() {
        val validatedConversationId =
            requireValueOrExit(
                value = conversationId,
                fieldName = "Conversation ID",
                errorMessage = "conversation required",
            )

        val messageService = messageServiceProvider()
        val knownMessageIds = mutableSetOf<String>()
        var hasBaselineSnapshot = false
        var transientFailureCount = 0

        while (keepWatching()) {
            val result = messageService.fetchMessages(validatedConversationId)
            when (result) {
                is FetchMessagesResult.Success ->
                    transientFailureCount =
                        handleSuccessfulFetch(result, knownMessageIds, hasBaselineSnapshot)
                            .also { hasBaselineSnapshot = true }

                is FetchMessagesResult.Failure -> {
                    val newFailureCount = handleFetchFailure(result, validatedConversationId, transientFailureCount)
                    if (newFailureCount != null) {
                        transientFailureCount = newFailureCount
                    } else {
                        echo(result.message, err = true)
                        throw ProgramResult(result.exitCode)
                    }
                }
            }
        }
    }

    private fun handleSuccessfulFetch(
        result: FetchMessagesResult.Success,
        knownMessageIds: MutableSet<String>,
        hasBaselineSnapshot: Boolean,
    ): Int {
        if (!hasBaselineSnapshot) {
            result.view.messages.forEach { knownMessageIds += it.id }
        } else {
            val newMessages = result.view.messages.filter { it.id !in knownMessageIds }
            result.view.messages.forEach { knownMessageIds += it.id }
            newMessages.forEach { echo(it.content) }
        }
        sleep(pollIntervalMs)
        return 0
    }

    private fun handleFetchFailure(
        result: FetchMessagesResult.Failure,
        conversationId: String,
        failureCount: Int,
    ): Int? {
        if (!isRetryableWatchFailure(result)) return null

        val newFailureCount = failureCount + 1
        val delayMs = calculateRetryDelayMs(newFailureCount)
        logger.warn {
            "Transient message watch fetch failure for conversationId=$conversationId; " +
                "retrying in ${delayMs}ms: ${result.message}"
        }
        echo("${result.message}; retrying in ${delayMs}ms", err = true)
        sleep(delayMs)
        return newFailureCount
    }

    private fun isRetryableWatchFailure(result: FetchMessagesResult.Failure): Boolean {
        if (result.exitCode == ExitCodes.UNAUTHORIZED) return false
        if (result.exitCode == ExitCodes.VALIDATION_ERROR) return false

        val normalizedMessage = result.message.lowercase()
        if (normalizedMessage.contains(MessageUserMessages.CONVERSATION_NOT_FOUND)) return false

        return true
    }

    private fun calculateRetryDelayMs(transientFailureCount: Int): Long {
        val baseDelayMs = pollIntervalMs.takeIf { it > 0 } ?: DEFAULT_WATCH_POLL_INTERVAL_MS
        val growthFactor = 1L shl min(transientFailureCount - 1, 4)
        return min(baseDelayMs * growthFactor, MAX_RETRY_BACKOFF_MS)
    }
}

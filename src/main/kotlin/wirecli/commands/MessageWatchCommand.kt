package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import wirecli.auth.ExitCodes
import wirecli.message.FetchMessagesResult
import wirecli.message.MessageService
import wirecli.message.MessageUserMessages

private val logger = KotlinLogging.logger {}

class MessageWatchCommand(
    private val messageServiceProvider: () -> MessageService,
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

        runBlocking {
            messageService.observeMessages(validatedConversationId)
                .takeWhile { keepWatching() }
                .collect { result ->
                    when (result) {
                        is FetchMessagesResult.Success -> {
                            handleSuccessfulFetch(result, knownMessageIds, hasBaselineSnapshot)
                            hasBaselineSnapshot = true
                        }

                        is FetchMessagesResult.Failure -> handleFetchFailure(result, validatedConversationId)
                    }
                }
        }
    }

    private fun handleSuccessfulFetch(
        result: FetchMessagesResult.Success,
        knownMessageIds: MutableSet<String>,
        hasBaselineSnapshot: Boolean,
    ) {
        if (!hasBaselineSnapshot) {
            result.view.messages.forEach { knownMessageIds += it.id }
            return
        }

        val newMessages = result.view.messages.filter { it.id !in knownMessageIds }
        result.view.messages.forEach { knownMessageIds += it.id }
        newMessages.forEach { echo(it.content) }
    }

    private fun handleFetchFailure(
        result: FetchMessagesResult.Failure,
        conversationId: String,
    ) {
        if (!isRetryableWatchFailure(result)) {
            echo(result.message, err = true)
            throw ProgramResult(result.exitCode)
        }

        logger.warn {
            "Transient message watch flow failure for conversationId=$conversationId: ${result.message}"
        }
        echo(result.message, err = true)
    }

    private fun isRetryableWatchFailure(result: FetchMessagesResult.Failure): Boolean {
        val normalizedMessage = result.message.lowercase()
        return result.exitCode != ExitCodes.UNAUTHORIZED &&
            result.exitCode != ExitCodes.VALIDATION_ERROR &&
            !normalizedMessage.contains(MessageUserMessages.CONVERSATION_NOT_FOUND)
    }
}

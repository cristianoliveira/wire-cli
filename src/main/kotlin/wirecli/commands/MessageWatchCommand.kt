package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import wirecli.auth.ExitCodes
import wirecli.message.FetchMessagesResult
import wirecli.message.MessageService
import wirecli.message.MessageUserMessages

private val logger = KotlinLogging.logger {}

private const val WATCH_FORMAT_TEXT = "text"
private const val WATCH_FORMAT_JSON = "json"

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

    private val format by option(
        "--format",
        help = "Output format: text or json (default: text).",
    )

    override fun run() {
        val validatedConversationId =
            requireValueOrExit(
                value = conversationId,
                fieldName = "Conversation ID",
                errorMessage = "conversation required",
            )

        val outputFormat = validateFormatOrExit(format ?: WATCH_FORMAT_TEXT)
        val messageService = messageServiceProvider()
        val knownMessageIds = mutableSetOf<String>()
        var hasBaselineSnapshot = false

        runBlocking {
            messageService.observeMessages(validatedConversationId)
                .takeWhile { keepWatching() }
                .collect { result ->
                    when (result) {
                        is FetchMessagesResult.Success -> {
                            handleSuccessfulFetch(result, knownMessageIds, hasBaselineSnapshot, outputFormat)
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
        outputFormat: String,
    ) {
        if (!hasBaselineSnapshot) {
            result.view.messages.forEach { knownMessageIds += it.id }
            return
        }

        val newMessages = result.view.messages.filter { it.id !in knownMessageIds }
        result.view.messages.forEach { knownMessageIds += it.id }
        newMessages.forEach { message -> echo(formatMessage(result.view.conversationId, message, outputFormat)) }
    }

    private fun validateFormatOrExit(format: String): String {
        if (format == WATCH_FORMAT_TEXT || format == WATCH_FORMAT_JSON) return format

        echo("validation error: format must be one of: text, json", err = true)
        throw ProgramResult(ExitCodes.VALIDATION_ERROR)
    }

    private fun formatMessage(
        conversationId: String,
        message: wirecli.message.ConversationMessage,
        outputFormat: String,
    ): String {
        if (outputFormat == WATCH_FORMAT_TEXT) return message.content

        return buildJsonObject {
            put("conversationId", JsonPrimitive(conversationId))
            put("messageId", JsonPrimitive(message.id))
            put("senderId", JsonPrimitive(message.senderId))
            put("senderName", JsonPrimitive(message.senderName))
            put("timestamp", JsonPrimitive(message.timestamp))
            put("content", JsonPrimitive(message.content))
        }.toString()
    }

    private fun handleFetchFailure(
        result: FetchMessagesResult.Failure,
        conversationId: String,
    ) {
        if (!isRetryableWatchFailure(result)) {
            echo(result.message, err = true)
            throw ProgramResult(processExitCode(result.exitCode))
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

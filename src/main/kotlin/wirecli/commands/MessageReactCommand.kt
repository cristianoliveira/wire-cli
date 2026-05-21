package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import wirecli.message.MessageService
import wirecli.message.ReactionAction
import wirecli.message.ToggleReactionResult

private val logger = KotlinLogging.logger {}

class MessageReactCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "react",
        help = "Toggle a reaction on a message.",
        epilog =
            """
            EXAMPLES:
              React to a message:
                wire message react <conversation-id> <message-id> 👍

              React with JSON output:
                wire message react <conversation-id> <message-id> ❤️ --json
            """.trimIndent(),
    ) {
    private val conversationId by argument(
        name = "CONVERSATION",
        help = "The conversation ID.",
    )

    private val messageId by argument(
        name = "MESSAGE",
        help = "The message ID to react to.",
    )

    private val emoji by argument(
        name = "EMOJI",
        help = "The emoji reaction to toggle.",
    )

    private val jsonOutput by option(
        "--json",
        help = "Output result as JSON.",
    ).flag()

    override fun run() {
        val validatedConversationId =
            requireValueOrExit(
                value = conversationId,
                fieldName = "Conversation-id",
                errorMessage = "conversation-id required",
            )
        val validatedMessageId =
            requireValueOrExit(
                value = messageId,
                fieldName = "Message-id",
                errorMessage = "message-id required",
            )
        val validatedEmoji =
            requireValueOrExit(
                value = emoji,
                fieldName = "Emoji",
                errorMessage = "emoji cannot be blank",
            )

        logger.info {
            "message-react invoked: conversationId=$validatedConversationId, " +
                "messageId=$validatedMessageId, emojiLength=${validatedEmoji.length}"
        }

        val messageService = messageServiceProvider()
        when (val result = messageService.toggleReaction(validatedConversationId, validatedMessageId, validatedEmoji)) {
            is ToggleReactionResult.Success -> {
                logger.info {
                    "message-react outcome=success action=${result.action}, " +
                        "conversationId=$validatedConversationId, messageId=$validatedMessageId"
                }
                when {
                    jsonOutput -> echo(formatJson(result.action, validatedEmoji))
                    else -> echo(formatHuman(result.action, validatedEmoji))
                }
            }

            is ToggleReactionResult.Failure -> {
                logger.warn {
                    "message-react outcome=failure exitCode=${result.exitCode} message=${result.message}"
                }
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun formatHuman(
        action: ReactionAction,
        emoji: String,
    ): String {
        val verb = if (action == ReactionAction.ADDED) "added" else "removed"
        return "Reaction $emoji $verb."
    }

    private fun formatJson(
        action: ReactionAction,
        emoji: String,
    ): String {
        return buildJsonObject {
            put("action", JsonPrimitive(if (action == ReactionAction.ADDED) "added" else "removed"))
            put("emoji", JsonPrimitive(emoji))
        }.toString()
    }
}

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import wirecli.message.DeleteMessageResult
import wirecli.message.DeleteScope
import wirecli.message.MessageService

private val logger = KotlinLogging.logger {}

class MessageDeleteCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "delete",
        help = "Delete a message (default: only for you; use --for-everyone to delete remotely).",
        epilog =
            """
            EXAMPLES:
              Delete a message just for you (local delete):
                wire message delete <conversation-id> <message-id>

              Delete a message for everyone in the conversation:
                wire message delete <conversation-id> <message-id> --for-everyone

              Delete with JSON output:
                wire message delete <conversation-id> <message-id> --json
            """.trimIndent(),
    ) {
    private val conversationId by argument(
        name = "CONVERSATION",
        help = "The conversation ID.",
    )

    private val messageId by argument(
        name = "MESSAGE",
        help = "The message ID to delete.",
    )

    private val forEveryone by option(
        "--for-everyone",
        help = "Delete the message for all participants (remote delete).",
    ).flag()

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

        val scope = if (forEveryone) DeleteScope.FOR_EVERYONE else DeleteScope.FOR_ME

        logger.info {
            "message-delete invoked: conversationId=$validatedConversationId, " +
                "messageId=$validatedMessageId, scope=$scope"
        }

        val messageService = messageServiceProvider()
        when (val result = messageService.deleteMessage(validatedConversationId, validatedMessageId, scope)) {
            is DeleteMessageResult.Success -> {
                logger.info {
                    "message-delete outcome=success scope=${result.scope}, " +
                        "conversationId=$validatedConversationId, messageId=$validatedMessageId"
                }
                when {
                    jsonOutput -> echo(formatJson(result.scope))
                    else -> echo(formatHuman(result.scope))
                }
            }

            is DeleteMessageResult.Failure -> {
                logger.warn {
                    "message-delete outcome=failure exitCode=${result.exitCode} message=${result.message}"
                }
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }

    private fun formatHuman(scope: DeleteScope): String =
        if (scope == DeleteScope.FOR_EVERYONE) "Message deleted for everyone." else "Message deleted."

    private fun formatJson(scope: DeleteScope): String =
        buildJsonObject {
            put("scope", JsonPrimitive(if (scope == DeleteScope.FOR_EVERYONE) "for_everyone" else "for_me"))
        }.toString()
}

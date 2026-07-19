package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.message.FetchMessagesResult
import wirecli.message.MessageFetchFormatter
import wirecli.message.MessageService

class MessageFetchCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "fetch",
        help = "Fetch messages from a conversation.",
    ) {
    private val conversationId by argument(
        name = "CONVERSATION_ID",
        help = "The conversation ID to fetch messages from",
    )

    private val noCache by option(
        "--no-cache",
        help = "Bypass the daemon cache and fetch from Wire.",
    ).flag(default = false)

    override fun run() {
        val validatedConversationId =
            requireValueOrExit(
                value = conversationId,
                fieldName = "Conversation ID",
                errorMessage = "conversation required",
            )

        val messageService = messageServiceProvider()
        val result =
            if (noCache) {
                messageService.fetchServerMessages(validatedConversationId)
            } else {
                messageService.fetchMessages(validatedConversationId)
            }
        when (result) {
            is FetchMessagesResult.Success -> {
                val formatter = MessageFetchFormatter()
                val output = formatter.toHumanReadable(result.view.messages)
                echo(output)
            }

            is FetchMessagesResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }
}

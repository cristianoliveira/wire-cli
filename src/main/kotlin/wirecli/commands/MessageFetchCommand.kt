package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
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

    override fun run() {
        val validatedConversationId =
            requireValueOrExit(
                value = conversationId,
                fieldName = "Conversation ID",
                errorMessage = "conversation required",
            )

        val messageService = messageServiceProvider()
        when (val result = messageService.fetchMessages(validatedConversationId)) {
            is FetchMessagesResult.Success -> {
                val formatter = MessageFetchFormatter()
                val output = formatter.toHumanReadable(result.view.messages)
                echo(output)
            }

            is FetchMessagesResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

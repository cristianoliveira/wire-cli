package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import wirecli.message.MessageFetchFormatter
import wirecli.message.MessageResult
import wirecli.message.MessageService

class MessageFetchCommand(
    private val messageServiceProvider: () -> MessageService,
) : CliktCommand(
        name = "fetch",
        help = "Fetch messages from a conversation.",
    ) {
    private val conversationId by argument(name = "CONVERSATION_ID", help = "The conversation ID to fetch messages from")

    override fun run() {
        val validatedConversationId =
            requireValueOrExit(
                value = conversationId,
                fieldName = "Conversation ID",
                errorMessage = "conversation required",
            )

        val messageService = messageServiceProvider()
        when (val result = messageService.fetchMessages(validatedConversationId)) {
            is MessageResult.Success -> {
                val formatter = MessageFetchFormatter()
                val output = formatter.toHumanReadable(result.value.messages)
                echo(output)
            }

            is MessageResult.Failure -> {
                echo(result.error.message, err = true)
                throw ProgramResult(result.error.exitCode)
            }
        }
    }
}

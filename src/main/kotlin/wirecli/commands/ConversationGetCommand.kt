package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.conversation.ConversationFormatter
import wirecli.conversation.ConversationService
import wirecli.conversation.GetConversationResult

class ConversationGetCommand(
    private val conversationServiceProvider: () -> ConversationService,
) : CliktCommand(name = "get", help = "Get detailed information about a specific conversation.") {
    private val conversationId by argument(
        name = "id",
        help = "The ID of the conversation to retrieve",
    )
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val conversationService = conversationServiceProvider()
        val result = conversationService.getConversation(conversationId)

        when (result) {
            is GetConversationResult.Success -> {
                val formatter = ConversationFormatter()
                val output =
                    if (json) {
                        formatter.toJson(listOf(result.view.conversation))
                    } else {
                        formatter.toDetail(result.view.conversation)
                    }

                echo(output)
            }

            is GetConversationResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }
}

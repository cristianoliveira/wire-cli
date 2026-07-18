package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.conversation.ConversationFormatter
import wirecli.conversation.ConversationService
import wirecli.conversation.GetMembersResult

class ConversationMembersCommand(
    private val conversationServiceProvider: () -> ConversationService,
) : CliktCommand(name = "members", help = "List members of a conversation.") {
    private val conversationId by argument(
        name = "id",
        help = "The ID of the conversation",
    )
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val conversationService = conversationServiceProvider()
        val result = conversationService.getMembers(conversationId)

        when (result) {
            is GetMembersResult.Success -> {
                val formatter = ConversationFormatter()
                val output =
                    if (json) {
                        formatter.membersToJson(result.view.members)
                    } else {
                        formatter.toMembersTable(result.view.members)
                    }

                echo(output)
            }

            is GetMembersResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

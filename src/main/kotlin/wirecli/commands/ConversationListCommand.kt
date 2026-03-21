package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.conversation.ConversationFormatter
import wirecli.conversation.ConversationService
import wirecli.conversation.ListConversationsResult
import wirecli.shared.Result.Success
import wirecli.shared.Result.Failure

class ConversationListCommand(
    private val conversationServiceProvider: () -> ConversationService,
) : CliktCommand(name = "list", help = "List all conversations with optional filtering and sorting.") {
    private val filterType by option(
        "--filter-type",
        help = "Filter by conversation type: ONE_TO_ONE, GROUP, TEAM_CHANNEL",
    )
    private val sortBy by option(
        "--sort-by",
        help = "Sort by: NAME, MEMBERS, CREATED",
    )
    private val json by option("--json", help = "Output as JSON").flag(default = false)
    private val jsonLines by option("--json-lines", help = "Output as JSON lines").flag(default = false)

    override fun run() {
        val conversationService = conversationServiceProvider()
        val result = conversationService.listConversations()

        when (result) {
            is ListConversationsResult.Success -> {
                var conversations = result.view.conversations

                // Apply filters
                if (filterType != null) {
                    conversations =
                        conversations.filter { conv ->
                            conv.type.name == filterType?.uppercase()
                        }
                }

                // Apply sorting
                conversations =
                    when (sortBy?.uppercase()) {
                        "NAME" -> conversations.sortedBy { it.name }
                        "MEMBERS" -> conversations.sortedByDescending { it.memberCount }
                        "CREATED" -> conversations.sortedByDescending { it.createdAt }
                        else -> conversations
                    }

                // Format and output
                val formatter = ConversationFormatter()
                val output =
                    when {
                        jsonLines -> formatter.toJsonLines(conversations)
                        json -> formatter.toJson(conversations)
                        else -> formatter.toTable(conversations)
                    }

                echo(output)
            }

            is ListConversationsResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

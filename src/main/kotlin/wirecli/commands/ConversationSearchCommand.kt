package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.conversation.ConversationFormatter
import wirecli.conversation.ConversationService
import wirecli.conversation.ConversationType
import wirecli.conversation.ListConversationsResult

class ConversationSearchCommand(
    private val conversationServiceProvider: () -> ConversationService,
) : CliktCommand(name = "search", help = "Search synchronized conversations by name.") {
    private val query by argument(name = "query", help = "Conversation name to search for")
    private val type by option("--type", help = "Conversation type: channel, group, or direct")
    private val json by option("--json", help = "Output as JSON").flag(default = false)
    private val jsonLines by option("--json-lines", help = "Output as JSON lines").flag(default = false)

    override fun run() {
        val expectedType = parseType(type)
        when (val result = conversationServiceProvider().listConversations()) {
            is ListConversationsResult.Success -> {
                val conversations =
                    result.view.conversations.filter { conversation ->
                        conversation.name.contains(query, ignoreCase = true) &&
                            (expectedType == null || conversation.type == expectedType)
                    }
                val formatter = ConversationFormatter()
                echo(
                    when {
                        jsonLines -> formatter.toJsonLines(conversations)
                        json -> formatter.toJson(conversations)
                        else -> formatter.toTable(conversations)
                    },
                )
            }

            is ListConversationsResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun parseType(value: String?): ConversationType? =
        when (value?.lowercase()) {
            null -> null
            "channel" -> ConversationType.TEAM_CHANNEL
            "group" -> ConversationType.GROUP
            "direct", "dm", "one-to-one" -> ConversationType.ONE_TO_ONE
            else -> throw UsageError("Invalid --type '$value'. Use channel, group, or direct.")
        }
}

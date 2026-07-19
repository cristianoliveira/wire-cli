package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.conversation.ConversationFormatter
import wirecli.conversation.ConversationService
import wirecli.conversation.ConversationType
import wirecli.conversation.ListConversationsResult

private enum class ConversationSort {
    NAME,
    MEMBERS,
    CREATED,
}

class ConversationListCommand(
    private val conversationServiceProvider: () -> ConversationService,
) : CliktCommand(
        name = "list",
        help = "List all conversations with optional filtering and sorting.",
        epilog =
            """
            EXAMPLES:
              List all conversations as human-readable table:
                wire conversation list
            
              List only team channels sorted by member count:
                wire conversation list --filter-type TEAM_CHANNEL --sort-by MEMBERS
            
              List conversations as JSON:
                wire conversation list --json
            """.trimIndent(),
    ) {
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
        validateStructuredOutputOrExit(json, jsonLines)
        val validatedFilter = validateEnum(filterType, "filter-type", ConversationType.entries)
        val validatedSort = validateEnum(sortBy, "sort-by", ConversationSort.entries)
        val conversationService = conversationServiceProvider()
        val result = conversationService.listConversations()

        when (result) {
            is ListConversationsResult.Success -> {
                var conversations = result.view.conversations

                // Apply filters
                if (validatedFilter != null) {
                    conversations = conversations.filter { conversation -> conversation.type == validatedFilter }
                }

                conversations =
                    when (validatedSort) {
                        ConversationSort.NAME -> conversations.sortedBy { it.name }
                        ConversationSort.MEMBERS -> conversations.sortedByDescending { it.memberCount }
                        ConversationSort.CREATED -> conversations.sortedByDescending { it.createdAt }
                        null -> conversations
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
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }

    private inline fun <reified T : Enum<T>> validateEnum(
        value: String?,
        optionName: String,
        validValues: List<T>,
    ): T? {
        if (value == null) return null

        return validateOrExit(errorFormatter = { "validation error: $it" }) {
            validValues.firstOrNull { valid -> valid.name == value.uppercase() }
                ?: throw IllegalArgumentException(
                    "$optionName must be one of: ${validValues.joinToString { valid -> valid.name }}",
                )
        }
    }
}

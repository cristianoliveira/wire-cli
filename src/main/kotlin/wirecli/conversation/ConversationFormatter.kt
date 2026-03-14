package wirecli.conversation

class ConversationFormatter {
    fun toTable(conversations: List<Conversation>): String {
        if (conversations.isEmpty()) {
            return "No conversations found."
        }

        val sb = StringBuilder()

        // Print header with column names and separator
        sb.append(String.format("%-24s %-20s %-15s %-10s %7s\n", "ID", "NAME", "TYPE", "STATUS", "MEMBERS"))
        sb.append("-".repeat(85)).append("\n")

        // Print each conversation as a row
        for (conv in conversations) {
            val id = conv.id.take(24)
            val name = conv.name.take(19)
            val type = conv.type.toString().take(14)
            val status = conv.status.toString().take(9)
            val members = conv.memberCount.toString().padStart(7)

            sb.append(
                String.format(
                    "%-24s %-20s %-15s %-10s %7s\n",
                    id,
                    name,
                    type,
                    status,
                    members,
                ),
            )
        }

        return sb.toString().trimEnd()
    }

    fun toJson(conversations: List<Conversation>): String {
        if (conversations.isEmpty()) {
            return "[]"
        }

        val jsonItems =
            conversations
                .map { buildConversationJsonObject(it) }
                .joinToString(",")

        return "[$jsonItems]"
    }

    fun toJsonLines(conversations: List<Conversation>): String {
        return conversations
            .map { buildConversationJsonObject(it) }
            .joinToString("\n")
    }

    fun toDetail(conversation: Conversation): String {
        return buildString {
            appendLine("ID:            ${conversation.id}")
            appendLine("Name:          ${conversation.name}")
            appendLine("Type:          ${conversation.type}")
            appendLine("Status:        ${conversation.status}")
            appendLine("Members:       ${conversation.memberCount}")
            appendLine("Created:       ${conversation.createdAt}")
            appendLine("Updated:       ${conversation.updatedAt}")
        }.trimEnd()
    }

    private fun buildConversationJsonObject(conversation: Conversation): String {
        val id = escapeJson(conversation.id)
        val name = escapeJson(conversation.name)
        val type = escapeJson(conversation.type.toString())
        val status = escapeJson(conversation.status.toString())
        val memberCount = conversation.memberCount
        val createdAt = escapeJson(conversation.createdAt)
        val updatedAt = escapeJson(conversation.updatedAt)

        return """{
            |"id":"$id",
            |"name":"$name",
            |"type":"$type",
            |"status":"$status",
            |"memberCount":$memberCount,
            |"createdAt":"$createdAt",
            |"updatedAt":"$updatedAt"
            |}
            """.trimMargin().replace("\n", "")
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

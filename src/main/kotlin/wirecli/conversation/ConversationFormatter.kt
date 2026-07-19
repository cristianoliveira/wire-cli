package wirecli.conversation

class ConversationFormatter {
    companion object {
        private const val UUID_LENGTH = 36
        private const val NAME_TRUNCATE_LENGTH = 23
        private const val TYPE_TRUNCATE_LENGTH = 14
        private const val STATUS_TRUNCATE_LENGTH = 9
        private const val TABLE_WIDTH = 105
        private const val MEMBER_ID_WIDTH = 42
        private const val MEMBER_NAME_WIDTH = 24
        private const val MEMBER_HANDLE_WIDTH = 20
        private const val MEMBER_ROLE_WIDTH = 10
        private const val MEMBER_TABLE_WIDTH = 96
    }

    fun toTable(conversations: List<Conversation>): String {
        if (conversations.isEmpty()) {
            return "No conversations found."
        }

        val sb = StringBuilder()

        // Print header with column names and separator
        sb.append(
            String.format(
                java.util.Locale.US,
                "%-37s %-24s %-15s %-10s %7s\n",
                "ID",
                "NAME",
                "TYPE",
                "STATUS",
                "MEMBERS",
            ),
        )
        sb.append("-".repeat(TABLE_WIDTH)).append("\n")

        // Print each conversation as a row
        for (conv in conversations) {
            val id = conv.id.take(UUID_LENGTH)
            val name = conv.name.take(NAME_TRUNCATE_LENGTH)
            val type = conv.type.toString().take(TYPE_TRUNCATE_LENGTH)
            val status = conv.status.toString().take(STATUS_TRUNCATE_LENGTH)
            val members = conv.memberCount.toString().padStart(7)

            sb.append(
                String.format(
                    java.util.Locale.US,
                    "%-37s %-24s %-15s %-10s %7s\n",
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
        val jsonItems =
            conversations
                .map { buildConversationJsonObject(it) }
                .joinToString(",")

        return """{"items":[$jsonItems],"returned":${conversations.size},"total":${conversations.size},"truncated":false}"""
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

    fun toMembersTable(members: List<Member>): String {
        if (members.isEmpty()) {
            return "No members found."
        }

        val sb = StringBuilder()

        sb.append(
            String.format(
                java.util.Locale.US,
                "%-${MEMBER_ID_WIDTH}s %-${MEMBER_NAME_WIDTH}s %-${MEMBER_HANDLE_WIDTH}s %-${MEMBER_ROLE_WIDTH}s\n",
                "ID",
                "NAME",
                "HANDLE",
                "ROLE",
            ),
        )
        sb.append("-".repeat(MEMBER_TABLE_WIDTH)).append("\n")

        for (member in members) {
            val id = member.id.take(MEMBER_ID_WIDTH)
            val name = member.name.take(MEMBER_NAME_WIDTH)
            val handle = (member.handle ?: "-").take(MEMBER_HANDLE_WIDTH)
            val role = member.role.toString().take(MEMBER_ROLE_WIDTH)

            sb.append(
                String.format(
                    java.util.Locale.US,
                    "%-${MEMBER_ID_WIDTH}s %-${MEMBER_NAME_WIDTH}s %-${MEMBER_HANDLE_WIDTH}s %-${MEMBER_ROLE_WIDTH}s\n",
                    id,
                    name,
                    handle,
                    role,
                ),
            )
        }

        return sb.toString().trimEnd()
    }

    fun membersToJson(members: List<Member>): String {
        if (members.isEmpty()) {
            return "[]"
        }

        val jsonItems =
            members
                .map { buildMemberJsonObject(it) }
                .joinToString(",")

        return "[$jsonItems]"
    }

    private fun buildMemberJsonObject(member: Member): String {
        val id = escapeJson(member.id)
        val name = escapeJson(member.name)
        val handle = escapeJson(member.handle ?: "")
        val role = escapeJson(member.role.toString())

        return """{
            |"id":"$id",
            |"name":"$name",
            |"handle":"$handle",
            |"role":"$role"
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

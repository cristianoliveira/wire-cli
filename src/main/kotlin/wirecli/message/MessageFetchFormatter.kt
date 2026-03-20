package wirecli.message

class MessageFetchFormatter {
    fun toHumanReadable(messages: List<ConversationMessage>): String {
        if (messages.isEmpty()) {
            return "No messages found."
        }

        return messages.joinToString("\n") { message ->
            val sender = message.senderName.ifBlank { message.senderId }
            "[${message.timestamp}] $sender: ${sanitizeContent(message.content)}"
        }
    }

    private fun sanitizeContent(content: String): String {
        return content
            .replace("\r", "\\r")
            .replace("\n", "\\n")
    }
}

package wirecli.message

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

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

    fun toHumanReadableRecent(messages: List<RecentMessageItem>): String {
        if (messages.isEmpty()) {
            return "No messages found."
        }

        return messages.joinToString("\n") { message ->
            val sender = message.senderName.ifBlank { message.senderId }
            "[${message.timestamp}] $sender @ ${message.conversationName} (${message.conversationId}): ${sanitizeContent(message.content)}"
        }
    }

    /**
     * The source cannot report the total number of messages beyond the requested
     * limit, so total is explicitly null and truncation remains unknown.
     */
    fun toJsonRecent(messages: List<RecentMessageItem>): String =
        buildJsonObject {
            put(
                "items",
                buildJsonArray {
                    messages.forEach { add(recentMessageJson(it)) }
                },
            )
            put("returned", JsonPrimitive(messages.size))
            put("total", JsonNull)
            put("truncated", JsonPrimitive(false))
        }.toString()

    fun toJsonLinesRecent(messages: List<RecentMessageItem>): String = messages.joinToString("\n") { recentMessageJson(it).toString() }

    private fun recentMessageJson(message: RecentMessageItem) =
        buildJsonObject {
            put("conversationId", JsonPrimitive(message.conversationId))
            put("conversationName", JsonPrimitive(message.conversationName))
            put("messageId", JsonPrimitive(message.messageId))
            put("senderId", JsonPrimitive(message.senderId))
            put("senderName", JsonPrimitive(message.senderName))
            put("timestamp", JsonPrimitive(message.timestamp))
            put("content", JsonPrimitive(message.content))
        }

    private fun sanitizeContent(content: String): String {
        return content
            .replace("\r", "\\r")
            .replace("\n", "\\n")
    }
}

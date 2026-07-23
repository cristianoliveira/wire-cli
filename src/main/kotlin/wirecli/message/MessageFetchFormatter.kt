package wirecli.message

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class MessageFetchFormatter {
    companion object {
        private const val MAX_CONTENT_BYTES = 200
    }

    fun toHumanReadable(messages: List<ConversationMessage>): String {
        if (messages.isEmpty()) {
            return "No messages found."
        }

        return messages.joinToString("\n") { message ->
            val sender = message.senderName.ifBlank { message.senderId }
            "[${message.timestamp}] $sender: ${previewContent(message.content)}"
        }
    }

    fun toJson(
        conversationId: String,
        messages: List<ConversationMessage>,
        truncated: Boolean = false,
    ): String =
        buildJsonObject {
            put("conversationId", JsonPrimitive(conversationId))
            put(
                "items",
                buildJsonArray {
                    messages.forEach { add(conversationMessageJson(it)) }
                },
            )
            put("returned", JsonPrimitive(messages.size))
            put("total", JsonNull)
            put("truncated", JsonPrimitive(truncated))
        }.toString()

    fun toHumanReadableRecent(
        messages: List<RecentMessageItem>,
        fullContent: Boolean = false,
    ): String {
        if (messages.isEmpty()) {
            return "No messages found."
        }

        return messages.joinToString("\n") { message ->
            val sender = message.senderName.ifBlank { message.senderId }
            val content = if (fullContent) sanitizeContent(message.content) else previewContent(message.content)
            "[${message.timestamp}] $sender @ ${message.conversationName} (${message.conversationId}): $content"
        }
    }

    fun toJsonRecent(
        messages: List<RecentMessageItem>,
        fullContent: Boolean = false,
    ): String =
        buildJsonObject {
            put(
                "items",
                buildJsonArray {
                    messages.forEach { add(recentMessageJson(it, fullContent)) }
                },
            )
            put("returned", JsonPrimitive(messages.size))
            put("total", JsonNull)
            put("truncated", JsonPrimitive(false))
        }.toString()

    fun toJsonLinesRecent(
        messages: List<RecentMessageItem>,
        fullContent: Boolean = false,
    ): String = messages.joinToString("\n") { recentMessageJson(it, fullContent).toString() }

    private fun conversationMessageJson(message: ConversationMessage) =
        buildJsonObject {
            put("messageId", JsonPrimitive(message.id))
            put("senderId", JsonPrimitive(message.senderId))
            put("senderName", JsonPrimitive(message.senderName))
            put("timestamp", JsonPrimitive(message.timestamp))
            put("content", JsonPrimitive(message.content))
            put("mentionsSelf", JsonPrimitive(message.mentionsSelf))
        }

    private fun recentMessageJson(
        message: RecentMessageItem,
        fullContent: Boolean,
    ) = buildJsonObject {
        put("conversationId", JsonPrimitive(message.conversationId))
        put("conversationName", JsonPrimitive(message.conversationName))
        put("messageId", JsonPrimitive(message.messageId))
        put("senderId", JsonPrimitive(message.senderId))
        put("senderName", JsonPrimitive(message.senderName))
        put("timestamp", JsonPrimitive(message.timestamp))
        put("mentionsSelf", JsonPrimitive(message.mentionsSelf))
        val content = message.content
        if (fullContent || content.toByteArray().size <= MAX_CONTENT_BYTES) {
            put("content", JsonPrimitive(content))
            put("contentTruncated", JsonPrimitive(false))
        } else {
            put("content", JsonPrimitive(previewContent(content)))
            put("contentTruncated", JsonPrimitive(true))
        }
        put("contentSize", JsonPrimitive(content.toByteArray().size))
    }

    private fun previewContent(content: String): String {
        val bytes = content.toByteArray()
        if (bytes.size <= MAX_CONTENT_BYTES) return sanitizeContent(content)

        val preview = content.take(MAX_CONTENT_BYTES / 2) // rough char estimate
        return sanitizeContent(preview) + "... [${bytes.size} bytes]"
    }

    private fun sanitizeContent(content: String): String {
        return content
            .replace("\r", "\\r")
            .replace("\n", "\\n")
    }
}

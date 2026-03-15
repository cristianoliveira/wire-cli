package wirecli.message

/**
 * Formatter for message-related output in various formats.
 *
 * Supports three output formats:
 * - text: Human-readable text format
 * - json: JSON array/object format
 * - jsonlines: JSON Lines format (one object per line)
 *
 * All JSON output properly escapes special characters.
 * @invariant Escaped JSON contains no unescaped special characters
 */
class MessageFormatter {
    /**
     * Format a single message send result.
     *
     * @param message The sent message to format
     * @param format Output format: "text", "json", or "jsonlines"
     * @return Formatted message string
     *
     * @post Output is valid in the requested format
     * @post For JSON, all string values are properly escaped
     */
    fun formatSendResult(
        message: Message,
        format: String,
    ): String {
        return when (format.lowercase()) {
            "json" -> buildMessageJsonObject(message)
            "jsonlines" -> buildMessageJsonObject(message)
            else -> formatMessageAsText(message)
        }
    }

    /**
     * Format a list of messages.
     *
     * @param messages The messages to format
     * @param format Output format: "text", "json", or "jsonlines"
     * @return Formatted message list string
     *
     * @post Output is valid in the requested format
     * @post For JSON, all string values are properly escaped
     * @post For text, messages are formatted as a table
     */
    fun formatMessageList(
        messages: List<Message>,
        format: String,
    ): String {
        return when (format.lowercase()) {
            "json" -> formatMessagesAsJson(messages)
            "jsonlines" -> formatMessagesAsJsonLines(messages)
            else -> formatMessagesAsTable(messages)
        }
    }

    /**
     * Format a single message for text output.
     *
     * Displays message details in a readable format with key-value pairs.
     *
     * @param message The message to format
     * @return Human-readable message details
     *
     * @post Output includes all important message fields
     * @post Output is readable with clear field labels
     */
    private fun formatMessageAsText(message: Message): String {
        return buildString {
            appendLine("ID:          ${message.id}")
            appendLine("From:        ${message.fromName} (${message.from})")
            appendLine("Text:        ${message.text}")
            appendLine("Status:      ${message.status}")
            appendLine("Timestamp:   ${message.timestamp}")
            if (message.editedTimestamp != null) {
                appendLine("Edited:      ${message.editedTimestamp}")
            }
            if (message.mentions.isNotEmpty()) {
                appendLine("Mentions:    ${message.mentions.joinToString(", ")}")
            }
            if (message.reactions.isNotEmpty()) {
                val reactionsStr = message.reactions.entries.joinToString(", ") { "${it.key} (${it.value})" }
                appendLine("Reactions:   $reactionsStr")
            }
        }.trimEnd()
    }

    /**
     * Format messages as a table for text output.
     *
     * @param messages The messages to format
     * @return Table representation of messages
     *
     * @post Header row followed by data rows
     * @post Each row contains: ID | From | Text | Timestamp | Status
     */
    private fun formatMessagesAsTable(messages: List<Message>): String {
        if (messages.isEmpty()) {
            return "No messages found."
        }

        val sb = StringBuilder()

        // Print header
        sb.append(
            String.format(
                "%-25s %-20s %-30s %-20s %-8s\n",
                "ID",
                "FROM",
                "TEXT",
                "TIMESTAMP",
                "STATUS",
            ),
        )
        sb.append("-".repeat(105)).append("\n")

        // Print rows
        for (message in messages) {
            val id = message.id.take(23) + if (message.id.length > 23) ".." else ""
            val from = message.fromName.take(18) + if (message.fromName.length > 18) ".." else ""
            val text = message.text.take(28) + if (message.text.length > 28) ".." else ""
            val timestamp = message.timestamp.take(19)
            val status = message.status.toString().take(6)

            sb.append(
                String.format(
                    "%-25s %-20s %-30s %-20s %-8s\n",
                    id,
                    from,
                    text,
                    timestamp,
                    status,
                ),
            )
        }

        return sb.toString().trimEnd()
    }

    /**
     * Format messages as a JSON array.
     *
     * Format: [{"id": "...", ...}, {"id": "...", ...}]
     *
     * @param messages The messages to format
     * @return JSON array of messages
     *
     * @post Output is valid JSON
     * @post All string values are properly escaped
     */
    private fun formatMessagesAsJson(messages: List<Message>): String {
        if (messages.isEmpty()) {
            return "[]"
        }

        val jsonItems =
            messages
                .map { buildMessageJsonObject(it) }
                .joinToString(",")

        return "[$jsonItems]"
    }

    /**
     * Format messages as JSON Lines (one message per line).
     *
     * Each line is a complete, valid JSON object.
     *
     * @param messages The messages to format
     * @return JSON Lines formatted output
     *
     * @post Each line is valid JSON
     * @post All string values are properly escaped
     */
    private fun formatMessagesAsJsonLines(messages: List<Message>): String {
        return messages
            .map { buildMessageJsonObject(it) }
            .joinToString("\n")
    }

    /**
     * Build a JSON object string for a single message.
     *
     * @param message The message to serialize
     * @return Valid JSON string with message properties
     *
     * @post Result is valid JSON with all string values escaped
     * @post Includes: id, text, from, fromName, status, timestamp
     * @post Conditionally includes: editedTimestamp, reactions, mentions
     */
    private fun buildMessageJsonObject(message: Message): String {
        val id = escapeJson(message.id)
        val text = escapeJson(message.text)
        val from = escapeJson(message.from)
        val fromName = escapeJson(message.fromName)
        val status = escapeJson(message.status.toString())
        val timestamp = escapeJson(message.timestamp)

        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"id\":\"$id\",")
        sb.append("\"text\":\"$text\",")
        sb.append("\"from\":\"$from\",")
        sb.append("\"fromName\":\"$fromName\",")
        sb.append("\"status\":\"$status\",")
        sb.append("\"timestamp\":\"$timestamp\"")

        if (message.editedTimestamp != null) {
            val editedTimestamp = escapeJson(message.editedTimestamp)
            sb.append(",\"editedTimestamp\":\"$editedTimestamp\"")
        }

        if (message.reactions.isNotEmpty()) {
            val reactionsJson =
                message.reactions.entries.joinToString(",") { (emoji, count) ->
                    "\"${escapeJson(emoji)}\":$count"
                }
            sb.append(",\"reactions\":{$reactionsJson}")
        }

        if (message.mentions.isNotEmpty()) {
            val mentionsJson = message.mentions.joinToString(",") { "\"${escapeJson(it)}\"" }
            sb.append(",\"mentions\":[$mentionsJson]")
        }

        sb.append("}")
        return sb.toString()
    }

    /**
     * Escape a string value for safe inclusion in JSON.
     *
     * Escapes: backslash, quote, newline, carriage return, tab
     *
     * @param value The string to escape
     * @return Escaped string safe for JSON inclusion
     *
     * @post Result contains no unescaped special characters
     * @post Result is safe to include in JSON strings
     */
    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

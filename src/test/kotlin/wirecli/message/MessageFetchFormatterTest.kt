package wirecli.message

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageFetchFormatterTest {
    private val formatter = MessageFetchFormatter()

    @Test
    fun `toHumanReadable returns fallback for empty list`() {
        val output = formatter.toHumanReadable(emptyList())

        assertEquals("No messages found.", output)
    }

    @Test
    fun `toJson returns stable message envelope with mention metadata`() {
        val output =
            formatter.toJson(
                conversationId = "conv-1",
                messages =
                    listOf(
                        ConversationMessage(
                            id = "msg-1",
                            senderId = "alice@example.com",
                            senderName = "Alice",
                            timestamp = "2026-03-20T10:00:00Z",
                            content = "hello\nworld",
                            mentionsSelf = true,
                        ),
                    ),
            )

        val envelope = Json.parseToJsonElement(output).jsonObject
        assertEquals("conv-1", envelope.getValue("conversationId").jsonPrimitive.content)
        val message = envelope.getValue("items").jsonArray.single().jsonObject
        assertEquals("hello\nworld", message.getValue("content").jsonPrimitive.content)
        assertEquals(true, message.getValue("mentionsSelf").jsonPrimitive.boolean)
    }

    @Test
    fun `toHumanReadable returns deterministic line format`() {
        val output =
            formatter.toHumanReadable(
                listOf(
                    ConversationMessage(
                        id = "msg-1",
                        senderId = "alice@example.com",
                        senderName = "Alice",
                        timestamp = "2026-03-20T10:00:00Z",
                        content = "hello\nworld",
                    ),
                ),
            )

        assertEquals("[2026-03-20T10:00:00Z] Alice: hello\\nworld", output)
    }
}

package wirecli.message

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

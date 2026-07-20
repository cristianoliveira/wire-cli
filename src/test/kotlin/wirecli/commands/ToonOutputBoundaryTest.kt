package wirecli.commands

import dev.toonformat.jtoon.JToon
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ToonOutputBoundaryTest {
    @Test
    fun `JToon round trips canonical message mutation JSON`() {
        val canonicalJson =
            "{\"conversationId\":\"conv-001\",\"messageId\":\"msg-001\",\"state\":\"read\",\"outcome\":\"applied\"}"

        val toon = JToon.encodeJson(canonicalJson)
        val decoded = JToon.decodeToJson(toon)

        assertEquals(
            Json.parseToJsonElement(canonicalJson),
            Json.parseToJsonElement(decoded),
        )
    }
}

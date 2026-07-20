package wirecli.commands

import kotlin.test.Test
import kotlin.test.assertEquals

class StructuredCommandErrorTest {
    @Test
    fun `formats deterministic retryable error with next command`() {
        val error =
            StructuredCommandError(
                code = "network_error",
                message = "network unavailable",
                retryable = true,
                next = "wire message set conv-1 --read msg-1 --json",
            )

        assertEquals(
            "{\"error\":{\"code\":\"network_error\",\"message\":\"network unavailable\",\"retryable\":true," +
                "\"next\":\"wire message set conv-1 --read msg-1 --json\"}}",
            formatStructuredError(error),
        )
    }

    @Test
    fun `omits next command when no safe recovery exists`() {
        val error = StructuredCommandError("not_found", "message not found", retryable = false)

        assertEquals(
            "{\"error\":{\"code\":\"not_found\",\"message\":\"message not found\",\"retryable\":false}}",
            formatStructuredError(error),
        )
    }
}

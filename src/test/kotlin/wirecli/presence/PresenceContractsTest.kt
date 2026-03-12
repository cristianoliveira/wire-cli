package wirecli.presence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PresenceContractsTest {
    @Test
    fun `failure exposes exit code payload`() {
        val result: PresenceResult = PresenceResult.Failure(
            message = "network unavailable",
            exitCode = 12
        )

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals("network unavailable", failure.message)
        assertEquals(12, failure.exitCode)
    }

    @Test
    fun `success exposes normalized state`() {
        val result: PresenceResult = PresenceResult.Success(PresenceView(PresenceState.AWAY))

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.AWAY, success.presence.state)
    }
}

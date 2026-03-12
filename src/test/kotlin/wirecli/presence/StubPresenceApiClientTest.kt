package wirecli.presence

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StubPresenceApiClientTest {
    private val session =
        AuthSession(
            userId = "jane@example.com",
            accessToken = "token",
            server = null,
        )

    @Test
    fun `returns normalized online presence by default`() {
        val client = StubPresenceApiClient(emptyMap())

        val result = client.fetchPresence(session)

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.ONLINE, success.presence.state)
    }

    @Test
    fun `maps invalid backend value to unknown`() {
        val client = StubPresenceApiClient(mapOf("WIRE_STUB_MODE" to "presence_invalid_value"))

        val result = client.fetchPresence(session)

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.UNKNOWN, success.presence.state)
    }

    @Test
    fun `returns unauthorized failure in unauthorized mode`() {
        val client = StubPresenceApiClient(mapOf("WIRE_STUB_MODE" to "presence_unauthorized"))

        val result = client.fetchPresence(session)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns network failure in network mode`() {
        val client = StubPresenceApiClient(mapOf("WIRE_STUB_MODE" to "presence_network_error"))

        val result = client.fetchPresence(session)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(
            "Presence fetch failed: network is unreachable. Check your connection and retry.",
            failure.message,
        )
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `returns server failure in server mode`() {
        val client = StubPresenceApiClient(mapOf("WIRE_STUB_MODE" to "presence_server_error"))

        val result = client.fetchPresence(session)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(
            "Presence service is unavailable. Retry later or check server settings.",
            failure.message,
        )
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns applied state for set by default`() {
        val client = StubPresenceApiClient(emptyMap())

        val result = client.updatePresence(session, WritablePresenceState.BUSY)

        val success = assertIs<PresenceResult.Success>(result)
        assertEquals(PresenceState.BUSY, success.presence.state)
    }

    @Test
    fun `returns unauthorized failure for set in unauthorized mode`() {
        val client = StubPresenceApiClient(mapOf("WIRE_STUB_MODE" to "presence_set_unauthorized"))

        val result = client.updatePresence(session, WritablePresenceState.ONLINE)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `returns network failure for set in network mode`() {
        val client = StubPresenceApiClient(mapOf("WIRE_STUB_MODE" to "presence_set_network_error"))

        val result = client.updatePresence(session, WritablePresenceState.AWAY)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(
            "Presence update failed: network is unreachable. Check your connection and retry.",
            failure.message,
        )
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `returns server failure for set in server mode`() {
        val client = StubPresenceApiClient(mapOf("WIRE_STUB_MODE" to "presence_set_server_error"))

        val result = client.updatePresence(session, WritablePresenceState.AWAY)

        val failure = assertIs<PresenceResult.Failure>(result)
        assertEquals(
            "Presence update could not be completed. Retry later or check server settings.",
            failure.message,
        )
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }
}

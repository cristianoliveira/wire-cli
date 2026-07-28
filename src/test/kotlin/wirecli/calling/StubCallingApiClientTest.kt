package wirecli.calling

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StubCallingApiClientTest {
    private val session = AuthSession("me@example.com", "token", null)

    @Test
    fun `ongoing call mode emits deterministic incoming call`() =
        runBlocking {
            val client = StubCallingApiClient(mapOf("WIRE_STUB_MODE" to "ongoing_call"))

            val result = assertIs<IncomingCallsResult.Success>(client.observeIncomingCalls(session).first())

            assertEquals("stub-conversation@example.com", result.calls.single().conversationId)
            assertEquals("stub-caller@example.com", result.calls.single().callerId)
        }

    @Test
    fun `default mode emits empty call list`() =
        runBlocking {
            val client = StubCallingApiClient(emptyMap())

            val result = assertIs<IncomingCallsResult.Success>(client.observeIncomingCalls(session).first())

            assertTrue(result.calls.isEmpty())
        }

    @Test
    fun `error mode emits failure`() =
        runBlocking {
            val client = StubCallingApiClient(mapOf("WIRE_STUB_MODE" to "ongoing_call_error"))

            val result = assertIs<IncomingCallsResult.Failure>(client.observeIncomingCalls(session).first())

            assertEquals("Stub incoming call observation failed.", result.message)
        }
}

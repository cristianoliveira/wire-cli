package wirecli.calling

import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.QualifiedID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RealKaliumCallingApiClientTest {
    private val session = AuthSession("me@example.com", "token", null)

    @Test
    fun `maps runtime calls to success result`() =
        runBlocking {
            val call = IncomingCall("conversation@example.com", "Engineering", "alice@example.com", "Alice")
            val client = RealKaliumCallingApiClient(FakeCallingRuntime(flowOf(listOf(call))))

            val result = assertIs<IncomingCallsResult.Success>(client.observeIncomingCalls(session).first())

            assertEquals(listOf(call), result.calls)
        }

    @Test
    fun `maps runtime failure without terminating daemon`() =
        runBlocking {
            val client =
                RealKaliumCallingApiClient(
                    FakeCallingRuntime(
                        flow { error("database unavailable") },
                    ),
                )

            val result = assertIs<IncomingCallsResult.Failure>(client.observeIncomingCalls(session).first())

            assertEquals("database unavailable", result.message)
        }

    @Test
    fun `maps kalium call without leaking sdk model`() {
        val call =
            Call(
                conversationId = QualifiedID("conversation", "example.com"),
                status = CallStatus.INCOMING,
                isMuted = false,
                isCameraOn = false,
                isCbrEnabled = false,
                callerId = QualifiedID("alice", "example.com"),
                conversationName = null,
                conversationType = Conversation.Type.OneOnOne,
                callerName = null,
                callerTeamName = null,
            )

        val result = call.toIncomingCall()

        assertEquals("conversation@example.com", result.conversationId)
        assertEquals("alice@example.com", result.callerId)
        assertEquals("", result.conversationName)
        assertEquals("", result.callerName)
    }

    private class FakeCallingRuntime(
        private val calls: kotlinx.coroutines.flow.Flow<List<IncomingCall>>,
    ) : CallingRuntime {
        override fun observeIncomingCalls(session: AuthSession) = calls
    }
}

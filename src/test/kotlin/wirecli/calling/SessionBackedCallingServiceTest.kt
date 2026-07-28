package wirecli.calling

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthSession
import wirecli.auth.SessionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionBackedCallingServiceTest {
    @Test
    fun `observes calls for active session`() =
        runBlocking {
            val session = AuthSession("me@example.com", "token", null)
            val call = IncomingCall("conversation@example.com", "Engineering", "alice@example.com", "Alice")
            val apiClient = RecordingCallingApiClient(flowOf(IncomingCallsResult.Success(listOf(call))))
            val service = SessionBackedCallingService(FakeSessionProvider(session), apiClient)

            val result = assertIs<IncomingCallsResult.Success>(service.observeIncomingCalls().first())

            assertEquals(session, apiClient.session)
            assertEquals(listOf(call), result.calls)
        }

    @Test
    fun `returns failure when active session is absent`() =
        runBlocking {
            val apiClient = RecordingCallingApiClient(flowOf(IncomingCallsResult.Success(emptyList())))
            val service = SessionBackedCallingService(FakeSessionProvider(null), apiClient)

            val result = assertIs<IncomingCallsResult.Failure>(service.observeIncomingCalls().first())

            assertEquals("No active session. Run wire login to re-authenticate.", result.message)
            assertEquals(null, apiClient.session)
        }

    private class FakeSessionProvider(
        private val session: AuthSession?,
    ) : SessionProvider {
        override fun readActiveSession(): AuthSession? = session
    }

    private class RecordingCallingApiClient(
        private val results: kotlinx.coroutines.flow.Flow<IncomingCallsResult>,
    ) : CallingApiClient {
        var session: AuthSession? = null

        override fun observeIncomingCalls(session: AuthSession): kotlinx.coroutines.flow.Flow<IncomingCallsResult> {
            this.session = session
            return results
        }
    }
}

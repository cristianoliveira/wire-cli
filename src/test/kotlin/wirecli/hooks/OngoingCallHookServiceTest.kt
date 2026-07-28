package wirecli.hooks

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import wirecli.calling.CallingService
import wirecli.calling.IncomingCall
import wirecli.calling.IncomingCallsResult
import kotlin.test.Test
import kotlin.test.assertEquals

class OngoingCallHookServiceTest {
    @Test
    fun `runs hook once when incoming call appears`() {
        runBlocking {
            val call = incomingCall()
            val hookRunner = RecordingHookRunner()
            val service =
                OngoingCallHookService(
                    callingService = FakeCallingService(flowOf(IncomingCallsResult.Success(listOf(call)))),
                    hookRunner = hookRunner,
                )

            service.observe()

            assertEquals(1, hookRunner.invocations.size)
            assertEquals(HookEvent.ONGOING_CALL, hookRunner.invocations.single().event)
            assertEquals(
                mapOf(
                    "WIRE_HOOK_EVENT" to "ongoing-call",
                    "WIRE_CALL_STATUS" to "incoming",
                    "WIRE_CALL_CONVERSATION_ID" to "conversation@example.com",
                    "WIRE_CALL_CONVERSATION_NAME" to "Engineering",
                    "WIRE_CALLER_ID" to "alice@example.com",
                    "WIRE_CALLER_NAME" to "Alice",
                ),
                hookRunner.invocations.single().variables,
            )
        }
    }

    @Test
    fun `does not run hook again until call disappears and reappears`() {
        runBlocking {
            val call = incomingCall()
            val hookRunner = RecordingHookRunner()
            val results =
                flowOf(
                    IncomingCallsResult.Success(listOf(call)),
                    IncomingCallsResult.Success(listOf(call)),
                    IncomingCallsResult.Success(emptyList()),
                    IncomingCallsResult.Success(listOf(call)),
                )
            val service =
                OngoingCallHookService(
                    callingService = FakeCallingService(results),
                    hookRunner = hookRunner,
                )

            service.observe()

            assertEquals(2, hookRunner.invocations.size)
        }
    }

    @Test
    fun `continues observing after hook exits unsuccessfully`() {
        runBlocking {
            val first = incomingCall(conversationId = "first@example.com")
            val second = incomingCall(conversationId = "second@example.com")
            val hookRunner = RecordingHookRunner(HookRunResult.Completed(exitCode = 7))
            val results =
                flowOf(
                    IncomingCallsResult.Success(listOf(first)),
                    IncomingCallsResult.Success(listOf(first, second)),
                )
            val service =
                OngoingCallHookService(
                    callingService = FakeCallingService(results),
                    hookRunner = hookRunner,
                )

            service.observe()

            assertEquals(2, hookRunner.invocations.size)
        }
    }

    @Test
    fun `does not run hook when call observation fails`() {
        runBlocking {
            val hookRunner = RecordingHookRunner()
            val service =
                OngoingCallHookService(
                    callingService = FakeCallingService(flowOf(IncomingCallsResult.Failure("not authenticated"))),
                    hookRunner = hookRunner,
                )

            service.observe()

            assertEquals(0, hookRunner.invocations.size)
        }
    }

    private fun incomingCall(conversationId: String = "conversation@example.com") =
        IncomingCall(
            conversationId = conversationId,
            conversationName = "Engineering",
            callerId = "alice@example.com",
            callerName = "Alice",
        )

    private class FakeCallingService(
        private val results: kotlinx.coroutines.flow.Flow<IncomingCallsResult>,
    ) : CallingService {
        override fun observeIncomingCalls() = results
    }

    private class RecordingHookRunner(
        private val result: HookRunResult = HookRunResult.Completed(exitCode = 0),
    ) : HookRunner {
        val invocations = mutableListOf<Invocation>()

        override fun run(
            event: HookEvent,
            variables: Map<String, String>,
        ): HookRunResult {
            invocations += Invocation(event, variables)
            return result
        }
    }

    private data class Invocation(
        val event: HookEvent,
        val variables: Map<String, String>,
    )
}

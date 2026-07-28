package wirecli.calling

import kotlinx.coroutines.flow.flowOf
import wirecli.auth.AuthSession

class StubCallingApiClient(
    private val environment: Map<String, String>,
) : CallingApiClient {
    override fun observeIncomingCalls(session: AuthSession) =
        when (environment["WIRE_STUB_MODE"]) {
            "ongoing_call" ->
                flowOf(
                    IncomingCallsResult.Success(
                        listOf(
                            IncomingCall(
                                conversationId = "stub-conversation@example.com",
                                conversationName = "Stub conversation",
                                callerId = "stub-caller@example.com",
                                callerName = "Stub caller",
                            ),
                        ),
                    ),
                )

            "ongoing_call_error" -> flowOf(IncomingCallsResult.Failure("Stub incoming call observation failed."))
            else -> flowOf(IncomingCallsResult.Success(emptyList()))
        }
}

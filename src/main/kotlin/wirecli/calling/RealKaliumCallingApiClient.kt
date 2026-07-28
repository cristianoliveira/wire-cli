package wirecli.calling

import com.wire.kalium.logic.data.call.Call
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import wirecli.auth.AuthSession

internal fun Call.toIncomingCall(): IncomingCall =
    IncomingCall(
        conversationId = conversationId.toString(),
        conversationName = conversationName.orEmpty(),
        callerId = callerId.toString(),
        callerName = callerName.orEmpty(),
    )

internal class RealKaliumCallingApiClient(
    private val runtime: CallingRuntime,
) : CallingApiClient {
    override fun observeIncomingCalls(session: AuthSession) =
        runtime.observeIncomingCalls(session)
            .map<List<IncomingCall>, IncomingCallsResult> { calls -> IncomingCallsResult.Success(calls) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(IncomingCallsResult.Failure(error.message ?: "Incoming call observation failed."))
            }
}

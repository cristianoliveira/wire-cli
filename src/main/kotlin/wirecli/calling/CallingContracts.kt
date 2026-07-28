package wirecli.calling

import kotlinx.coroutines.flow.Flow
import wirecli.auth.AuthSession

data class IncomingCall(
    val conversationId: String,
    val conversationName: String,
    val callerId: String,
    val callerName: String,
)

sealed interface IncomingCallsResult {
    data class Success(val calls: List<IncomingCall>) : IncomingCallsResult

    data class Failure(val message: String) : IncomingCallsResult
}

interface CallingApiClient {
    fun observeIncomingCalls(session: AuthSession): Flow<IncomingCallsResult>
}

interface CallingService {
    fun observeIncomingCalls(): Flow<IncomingCallsResult>
}

internal interface CallingRuntime {
    fun observeIncomingCalls(session: AuthSession): Flow<List<IncomingCall>>
}

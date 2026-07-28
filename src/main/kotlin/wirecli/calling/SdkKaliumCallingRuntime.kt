package wirecli.calling

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import wirecli.auth.AuthSession
import wirecli.auth.toQualifiedIdOrNull

internal class SdkKaliumCallingRuntime(
    private val coreLogicForSession: (UserId) -> CoreLogic,
) : CallingRuntime {
    override fun observeIncomingCalls(session: AuthSession): Flow<List<IncomingCall>> =
        flow {
            require(session.userId.isNotBlank()) { "Incoming call observation requires a non-blank user ID." }
            val qualifiedId =
                session.userId.toQualifiedIdOrNull()
                    ?: error("Incoming call observation requires a qualified user ID.")
            val coreLogic = coreLogicForSession(qualifiedId)
            val incomingCalls = coreLogic.sessionScope(qualifiedId) { calls.getIncomingCalls() }

            incomingCalls.collect { calls ->
                emit(calls.map { call -> call.toIncomingCall() })
            }
        }
}

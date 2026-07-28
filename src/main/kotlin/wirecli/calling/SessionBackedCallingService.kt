package wirecli.calling

import kotlinx.coroutines.flow.flowOf
import wirecli.auth.AuthMessages
import wirecli.auth.SessionProvider

class SessionBackedCallingService(
    private val sessionStore: SessionProvider,
    private val apiClient: CallingApiClient,
) : CallingService {
    override fun observeIncomingCalls() =
        sessionStore.readActiveSession()?.let(apiClient::observeIncomingCalls)
            ?: flowOf(IncomingCallsResult.Failure(AuthMessages.noActiveSession()))
}

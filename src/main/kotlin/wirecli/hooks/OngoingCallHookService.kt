package wirecli.hooks

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wirecli.calling.CallingService
import wirecli.calling.IncomingCall
import wirecli.calling.IncomingCallsResult

private val logger = KotlinLogging.logger {}

interface DaemonHookService {
    suspend fun observe()
}

class OngoingCallHookService(
    private val callingService: CallingService,
    private val hookRunner: HookRunner,
) : DaemonHookService {
    override suspend fun observe() {
        val activeConversationIds = mutableSetOf<String>()
        callingService.observeIncomingCalls().collect { result ->
            when (result) {
                is IncomingCallsResult.Failure ->
                    logger.warn { "Incoming call hook observation failed: ${result.message}" }

                is IncomingCallsResult.Success -> {
                    val currentConversationIds = result.calls.mapTo(mutableSetOf()) { it.conversationId }
                    result.calls
                        .filterNot { it.conversationId in activeConversationIds }
                        .forEach { call -> runHook(call) }
                    activeConversationIds.retainAll(currentConversationIds)
                    activeConversationIds.addAll(currentConversationIds)
                }
            }
        }
    }

    private suspend fun runHook(call: IncomingCall) {
        val result =
            withContext(Dispatchers.IO) {
                hookRunner.run(
                    HookEvent.ONGOING_CALL,
                    mapOf(
                        "WIRE_HOOK_EVENT" to "ongoing-call",
                        "WIRE_CALL_STATUS" to "incoming",
                        "WIRE_CALL_CONVERSATION_ID" to call.conversationId,
                        "WIRE_CALL_CONVERSATION_NAME" to call.conversationName,
                        "WIRE_CALLER_ID" to call.callerId,
                        "WIRE_CALLER_NAME" to call.callerName,
                    ),
                )
            }

        when (result) {
            HookRunResult.NotConfigured -> Unit
            is HookRunResult.Completed ->
                if (result.exitCode != 0) {
                    logger.warn { "Ongoing call hook exited with code ${result.exitCode}." }
                }
            is HookRunResult.NotExecutable -> logger.warn { "Ongoing call hook is not executable: ${result.path}" }
            is HookRunResult.TimedOut -> logger.warn { "Ongoing call hook timed out after ${result.timeoutMillis}ms." }
            is HookRunResult.Failed -> logger.warn { "Ongoing call hook failed: ${result.message}" }
        }
    }
}

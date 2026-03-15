package wirecli.conversation

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationFilter
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.ObserveConversationDetailsUseCase
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthSession
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs
import java.nio.file.Paths

/**
 * Runtime abstraction for Kalium conversation operations
 */
internal interface RealKaliumConversationRuntime {
    fun listConversations(session: AuthSession): ConversationStepResult<List<ConversationDetails>>

    fun getConversation(
        session: AuthSession,
        conversationId: String,
    ): ConversationStepResult<ConversationDetails>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

internal sealed interface ConversationStepResult<out T> {
    data class Success<T>(val value: T) : ConversationStepResult<T>

    data class Failure(val category: ConversationFailureCategory) : ConversationStepResult<Nothing>
}

internal enum class ConversationFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    NOT_FOUND,
    UNKNOWN,
}

/**
 * SDK-based implementation of RealKaliumConversationRuntime using CoreLogic
 */
internal class SdkKaliumConversationRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : RealKaliumConversationRuntime {
    private val activeSessionUserIds = mutableSetOf<UserId>()

    private val coreLogicLazy =
        lazy {
            CoreLogic(
                rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium",
                kaliumConfigs = kaliumCliConfigs(cliMode),
                userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
            )
        }
    private val coreLogic: CoreLogic by coreLogicLazy

    override fun listConversations(session: AuthSession): ConversationStepResult<List<ConversationDetails>> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return ConversationStepResult.Failure(ConversationFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                // Use observeConversationListDetailsWithEvents usecase to get full ConversationDetails with contact info
                val conversationDetails =
                    coreLogic.sessionScope(qualifiedId) {
                        conversations.observeConversationListDetailsWithEvents(
                            fromArchive = false,
                            conversationFilter = ConversationFilter.All,
                        )
                            .firstOrNull()
                            ?.map { it.conversationDetails }
                            ?: emptyList()
                    }

                ConversationStepResult.Success(conversationDetails)
            } catch (error: Throwable) {
                ConversationStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getConversation(
        session: AuthSession,
        conversationId: String,
    ): ConversationStepResult<ConversationDetails> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return ConversationStepResult.Failure(ConversationFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                val kaliumConvId =
                    ConversationId(
                        value = conversationId,
                        domain = "wire.com",
                    )

                val result =
                    coreLogic.sessionScope(qualifiedId) {
                        conversations.observeConversationDetails(kaliumConvId)
                            .firstOrNull()
                    }

                if (result is ObserveConversationDetailsUseCase.Result.Success) {
                    ConversationStepResult.Success(result.conversationDetails)
                } else {
                    ConversationStepResult.Failure(ConversationFailureCategory.NOT_FOUND)
                }
            } catch (error: Throwable) {
                ConversationStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun shutdown() {
        if (coreLogicLazy.isInitialized()) {
            runBlocking {
                try {
                    // Cancel all active session scopes
                    activeSessionUserIds.forEach { userId ->
                        try {
                            coreLogic.sessionScope(userId) {
                                // Cancel the scope to terminate background tasks
                                cancel()
                            }
                        } catch (e: Exception) {
                            // Ignore errors during scope cancellation
                        }
                    }
                    // Cancel the global scope to terminate all background tasks
                    coreLogic.getGlobalScope().cancel()
                } catch (e: Exception) {
                    // Ignore errors during global scope cancellation
                }
            }
            activeSessionUserIds.clear()
        }
    }

    companion object {
        private fun resolveHomeDirectory(environment: Map<String, String>): String {
            return environment["HOME"] ?: System.getProperty("user.home")
                ?: Paths.get("").toAbsolutePath().toString()
        }

        private fun categoryFromThrowable(error: Throwable): ConversationFailureCategory {
            return when {
                error.message?.contains("Unauthorized") == true ||
                    error.message?.contains("401") == true ->
                    ConversationFailureCategory.UNAUTHORIZED

                error.message?.contains("Network") == true ||
                    error.message?.contains("Connection") == true ->
                    ConversationFailureCategory.NETWORK

                error.message?.contains("404") == true ||
                    error.message?.contains("Not found") == true ->
                    ConversationFailureCategory.NOT_FOUND

                else -> ConversationFailureCategory.UNKNOWN
            }
        }
    }
}

private fun String.toQualifiedIdOrNull(): UserId? {
    val trimmed = trim()
    val atIndex = trimmed.lastIndexOf('@')
    if (atIndex <= 0 || atIndex == trimmed.lastIndex) return null
    val value = trimmed.substring(0, atIndex)
    val domain = trimmed.substring(atIndex + 1)
    if (value.isBlank() || domain.isBlank()) return null
    return UserId(value = value, domain = domain)
}

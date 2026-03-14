package wirecli.conversation

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthSession
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs
import java.nio.file.Paths
import com.wire.kalium.logic.data.conversation.Conversation as KaliumConversation

/**
 * Runtime abstraction for Kalium conversation operations
 */
internal interface RealKaliumConversationRuntime {
    fun listConversations(session: AuthSession): ConversationStepResult<List<KaliumConversation>>

    fun getConversation(
        session: AuthSession,
        conversationId: String,
    ): ConversationStepResult<KaliumConversation>

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

    override fun listConversations(session: AuthSession): ConversationStepResult<List<KaliumConversation>> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return ConversationStepResult.Failure(ConversationFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                // Use the public GetConversationsUseCase API
                val result =
                    coreLogic.sessionScope(qualifiedId) {
                        conversations.getConversations()
                    }

                when (result) {
                    is GetConversationsUseCase.Result.Success -> {
                        val conversations = result.convFlow.firstOrNull() ?: emptyList()
                        ConversationStepResult.Success(conversations)
                    }

                    is GetConversationsUseCase.Result.Failure ->
                        ConversationStepResult.Failure(ConversationFailureCategory.SERVER)
                }
            } catch (error: Throwable) {
                ConversationStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getConversation(
        session: AuthSession,
        conversationId: String,
    ): ConversationStepResult<KaliumConversation> {
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
                        conversations.getConversations()
                    }

                when (result) {
                    is GetConversationsUseCase.Result.Success -> {
                        val conversations = result.convFlow.firstOrNull() ?: emptyList()
                        val conversation = conversations.firstOrNull { it.id == kaliumConvId }
                        if (conversation != null) {
                            ConversationStepResult.Success(conversation)
                        } else {
                            ConversationStepResult.Failure(ConversationFailureCategory.NOT_FOUND)
                        }
                    }

                    is GetConversationsUseCase.Result.Failure ->
                        ConversationStepResult.Failure(ConversationFailureCategory.SERVER)
                }
            } catch (error: Throwable) {
                ConversationStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun shutdown() {
        if (coreLogicLazy.isInitialized()) {
            activeSessionUserIds.forEach { userId ->
                runBlocking {
                    try {
                        coreLogic.sessionScope(userId) {
                            // Clear the session
                        }
                    } catch (e: Exception) {
                        // Ignore errors during shutdown
                    }
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

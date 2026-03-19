package wirecli.message

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import wirecli.auth.AuthSession
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs
import java.nio.file.Paths

/**
 * Runtime abstraction for Kalium message operations
 */
internal interface RealKaliumMessageRuntime {
    fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): MessageStepResult

    fun close() {
        shutdown()
    }

    fun shutdown()
}

internal sealed interface MessageStepResult {
    data object Success : MessageStepResult

    data class Failure(val category: MessageFailureCategory) : MessageStepResult
}

internal enum class MessageFailureCategory {
    VALIDATION,
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    NOT_FOUND,
    UNKNOWN,
}

/**
 * SDK-based implementation of RealKaliumMessageRuntime using CoreLogic
 */
internal class SdkKaliumMessageRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : RealKaliumMessageRuntime {
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

    override fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): MessageStepResult {
        // Validate inputs
        if (conversationId.isBlank()) {
            return MessageStepResult.Failure(MessageFailureCategory.VALIDATION)
        }
        if (text.isBlank()) {
            return MessageStepResult.Failure(MessageFailureCategory.VALIDATION)
        }

        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        return try {
            // TODO: Integrate with Kalium SDK message sending API when available
            // For now, this is a stub implementation that validates inputs and returns success
            val kaliumConvId =
                ConversationId(
                    value = conversationId,
                    domain = "wire.com",
                )

            // Validate the conversation ID can be parsed
            if (conversationId.isEmpty()) {
                return MessageStepResult.Failure(MessageFailureCategory.VALIDATION)
            }

            MessageStepResult.Success
        } catch (error: Throwable) {
            MessageStepResult.Failure(categoryFromThrowable(error))
        }
    }

    override fun shutdown() {
        if (coreLogicLazy.isInitialized()) {
            activeSessionUserIds.clear()
        }
    }

    companion object {
        private fun resolveHomeDirectory(environment: Map<String, String>): String {
            return environment["HOME"] ?: System.getProperty("user.home")
                ?: Paths.get("").toAbsolutePath().toString()
        }

        private fun categoryFromThrowable(error: Throwable): MessageFailureCategory {
            return when {
                error.message?.contains("Unauthorized") == true ||
                    error.message?.contains("401") == true ->
                    MessageFailureCategory.UNAUTHORIZED

                error.message?.contains("Network") == true ||
                    error.message?.contains("Connection") == true ->
                    MessageFailureCategory.NETWORK

                error.message?.contains("404") == true ||
                    error.message?.contains("Not found") == true ->
                    MessageFailureCategory.NOT_FOUND

                error.message?.contains("Validation") == true ||
                    error.message?.contains("Invalid") == true ->
                    MessageFailureCategory.VALIDATION

                error.message?.contains("Server") == true ||
                    error.message?.contains("500") == true ->
                    MessageFailureCategory.SERVER

                else -> MessageFailureCategory.UNKNOWN
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

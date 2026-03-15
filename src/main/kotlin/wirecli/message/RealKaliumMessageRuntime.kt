package wirecli.message

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthSession
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs
import java.nio.file.Paths

/**
 * Runtime abstraction for Kalium message operations
 */
internal interface RealKaliumMessageRuntime {
    fun fetchMessages(
        session: AuthSession,
        conversationId: String,
        limit: Int? = null,
        from: String? = null,
    ): MessageStepResult<List<Message>>

    fun fetchMessage(
        session: AuthSession,
        conversationId: String,
        messageId: String,
    ): MessageStepResult<Message>

    fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): MessageStepResult<Message>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

internal sealed interface MessageStepResult<out T> {
    data class Success<T>(val value: T) : MessageStepResult<T>

    data class Failure(val category: MessageFailureCategory) : MessageStepResult<Nothing>
}

internal enum class MessageFailureCategory {
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

    override fun fetchMessages(
        session: AuthSession,
        conversationId: String,
        limit: Int?,
        from: String?,
    ): MessageStepResult<List<Message>> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                val kaliumConvId =
                    ConversationId(
                        value = conversationId,
                        domain = "wire.com",
                    )

                val effectiveLimit = limit ?: 50
                // Get messages using the messages use case
                val messages =
                    coreLogic.sessionScope(qualifiedId) {
                        messages.getRecentMessages(kaliumConvId, effectiveLimit).firstOrNull()
                            ?: emptyList()
                    }

                MessageStepResult.Success(messages)
            } catch (error: Throwable) {
                MessageStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun fetchMessage(
        session: AuthSession,
        conversationId: String,
        messageId: String,
    ): MessageStepResult<Message> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)
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
                        messages.getMessageById(
                            conversationId = kaliumConvId,
                            messageId = messageId,
                        )
                    }

                return@runBlocking when (result) {
                    is com.wire.kalium.common.functional.Either.Right<*> ->
                        MessageStepResult.Success(result.value as Message)

                    is com.wire.kalium.common.functional.Either.Left<*> ->
                        MessageStepResult.Failure(MessageFailureCategory.NOT_FOUND)

                    else -> MessageStepResult.Failure(MessageFailureCategory.UNKNOWN)
                }
            } catch (error: Throwable) {
                MessageStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): MessageStepResult<Message> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                val kaliumConvId =
                    ConversationId(
                        value = conversationId,
                        domain = "wire.com",
                    )

                // Use SendTextMessageUseCase to send message through Kalium SDK
                val result =
                    coreLogic.sessionScope(qualifiedId) {
                        messages.sendTextMessage(
                            conversationId = kaliumConvId,
                            text = text,
                        )
                    }

                return@runBlocking when (result) {
                    is com.wire.kalium.common.functional.Either.Right<*> -> {
                        // Message sent successfully, fetch it to get full details
                        val messageId = (result.value as Message).id
                        val message =
                            coreLogic.sessionScope(qualifiedId) {
                                messages.getMessageById(
                                    conversationId = kaliumConvId,
                                    messageId = messageId,
                                )
                            }

                        when (message) {
                            is com.wire.kalium.common.functional.Either.Right<*> ->
                                MessageStepResult.Success(message.value as Message)

                            is com.wire.kalium.common.functional.Either.Left<*> ->
                                MessageStepResult.Failure(MessageFailureCategory.NOT_FOUND)

                            else -> MessageStepResult.Failure(MessageFailureCategory.UNKNOWN)
                        }
                    }

                    is com.wire.kalium.common.functional.Either.Left<*> ->
                        MessageStepResult.Failure(categoryFromThrowable(result.value as Throwable))

                    else -> MessageStepResult.Failure(MessageFailureCategory.UNKNOWN)
                }
            } catch (error: Throwable) {
                MessageStepResult.Failure(categoryFromThrowable(error))
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

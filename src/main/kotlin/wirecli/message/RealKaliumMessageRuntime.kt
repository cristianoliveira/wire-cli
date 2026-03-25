package wirecli.message

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageOperationResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import wirecli.auth.AuthSession
import wirecli.domains.message.MessageFailureMapper
import wirecli.domains.message.MessageOperationHelper
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

/**
 * Runtime abstraction for Kalium message operations
 */
internal interface RealKaliumMessageRuntime {
    fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): MessageStepResult<Unit>

    fun fetchMessages(
        session: AuthSession,
        conversationId: String,
    ): MessageStepResult<List<ConversationMessage>>

    fun sendTypingStatus(
        session: AuthSession,
        conversationId: String,
        status: TypingStatus,
    ): MessageStepResult<Unit> = MessageStepResult.Failure(MessageFailureCategory.UNKNOWN)

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
    VALIDATION,
    TIMEOUT,
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    NOT_FOUND,
    UNKNOWN,
}

private const val PREFLIGHT_SYNC_TIMEOUT_MS = 15_000L
private const val FETCH_MESSAGES_LIMIT = 10
internal const val MESSAGE_SEND_TIMEOUT_ENV = "WIRECLI_MESSAGE_SEND_TIMEOUT_MS"
internal const val DEFAULT_SEND_TIMEOUT_MS = 60_000L
internal const val MAX_SEND_TIMEOUT_MS = 300_000L

internal fun resolveSendTimeoutMs(environment: Map<String, String>): Long {
    val rawValue = environment[MESSAGE_SEND_TIMEOUT_ENV]?.trim().orEmpty()

    val parsedValue = rawValue.toLongOrNull()

    return when {
        rawValue.isEmpty() -> DEFAULT_SEND_TIMEOUT_MS
        parsedValue == null || parsedValue <= 0L -> {
            logger.warn {
                "Invalid $MESSAGE_SEND_TIMEOUT_ENV='$rawValue'; using default ${DEFAULT_SEND_TIMEOUT_MS}ms"
            }
            DEFAULT_SEND_TIMEOUT_MS
        }
        parsedValue > MAX_SEND_TIMEOUT_MS -> {
            logger.warn {
                "$MESSAGE_SEND_TIMEOUT_ENV=$parsedValue exceeds max ${MAX_SEND_TIMEOUT_MS}ms; clamping to ${MAX_SEND_TIMEOUT_MS}ms"
            }
            MAX_SEND_TIMEOUT_MS
        }
        else -> parsedValue
    }
}

/**
 * SDK-based implementation of RealKaliumMessageRuntime using CoreLogic
 */
internal class SdkKaliumMessageRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : RealKaliumMessageRuntime {
    private val activeSessionUserIds = mutableSetOf<UserId>()
    private val sendTimeoutMs = resolveSendTimeoutMs(environment)

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
    ): MessageStepResult<Unit> {
        if (conversationId.isBlank()) {
            logger.debug { "sendMessage: Validation failed - conversationId is blank" }
            return MessageStepResult.Failure(MessageFailureCategory.VALIDATION)
        }
        if (text.isBlank()) {
            logger.debug { "sendMessage: Validation failed - text is blank" }
            return MessageStepResult.Failure(MessageFailureCategory.VALIDATION)
        }

        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "sendMessage: Invalid user ID format: ${session.userId}" }
                    return MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)
                }
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                logger.info {
                    "message-send runtime start: conversationId=$conversationId, userId=${session.userId}, textLength=${text.length}"
                }

                val preflightFailureCategory =
                    MessageOperationHelper.executePreflightSync(
                        coreLogic,
                        qualifiedId,
                        conversationId,
                        PREFLIGHT_SYNC_TIMEOUT_MS,
                    )
                if (preflightFailureCategory != null) return@runBlocking MessageStepResult.Failure(preflightFailureCategory)

                val kaliumConvId = MessageOperationHelper.buildQualifiedConversationId(conversationId, session.server)

                val (result, timeoutFailure) =
                    MessageOperationHelper.executeSendWithTimeout(
                        coreLogic,
                        qualifiedId,
                        conversationId,
                        sendTimeoutMs,
                    ) {
                        coreLogic.sessionScope(qualifiedId) {
                            withContext(Dispatchers.Default) {
                                logger.info { "message-send request start: conversationId=$conversationId" }
                                val sendResult =
                                    syncExecutor.request {
                                        logger.info {
                                            "message-send sendTextMessage request body start: conversationId=$conversationId"
                                        }
                                        messages.sendTextMessage(kaliumConvId, text)
                                    }
                                logger.info { "message-send request end: conversationId=$conversationId" }
                                sendResult
                            }
                        }
                    }

                if (timeoutFailure != null) return@runBlocking MessageStepResult.Failure(MessageFailureCategory.TIMEOUT)

                return@runBlocking when (result) {
                    is MessageOperationResult.Success -> {
                        logger.info { "message-send runtime outcome=success conversationId=$conversationId" }
                        MessageStepResult.Success(Unit)
                    }

                    is MessageOperationResult.Failure -> {
                        val mappedCategory = MessageFailureMapper.categoryFromCoreFailure(result.error)
                        logger.warn {
                            "message-send runtime outcome=failure conversationId=$conversationId " +
                                "failureClass=${result.error::class.simpleName} mappedCategory=$mappedCategory"
                        }
                        MessageStepResult.Failure(mappedCategory)
                    }

                    null -> MessageStepResult.Failure(MessageFailureCategory.UNKNOWN)
                }
            } catch (error: Throwable) {
                val mappedCategory = MessageFailureMapper.categoryFromThrowable(error)
                logger.error(error) {
                    "message-send runtime exception: conversationId=$conversationId " +
                        "exceptionClass=${error::class.qualifiedName} message=${error.message} mappedCategory=$mappedCategory"
                }
                MessageStepResult.Failure(mappedCategory)
            }
        }
    }

    override fun fetchMessages(
        session: AuthSession,
        conversationId: String,
    ): MessageStepResult<List<ConversationMessage>> {
        if (conversationId.isBlank()) {
            logger.debug { "fetchMessages: Validation failed - conversationId is blank" }
            return MessageStepResult.Failure(MessageFailureCategory.VALIDATION)
        }

        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "fetchMessages: Invalid user ID format: ${session.userId}" }
                    return MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)
                }
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                logger.info {
                    "message-fetch runtime start: conversationId=$conversationId, userId=${session.userId}"
                }

                val preflightFailureCategory =
                    MessageOperationHelper.executePreflightSync(
                        coreLogic,
                        qualifiedId,
                        conversationId,
                        PREFLIGHT_SYNC_TIMEOUT_MS,
                    )
                if (preflightFailureCategory != null) return@runBlocking MessageStepResult.Failure(preflightFailureCategory)

                val kaliumConvId = MessageOperationHelper.buildQualifiedConversationId(conversationId, session.server)

                val (result, timeoutFailure) =
                    MessageOperationHelper.executeFetchWithTimeout(conversationId, sendTimeoutMs) {
                        coreLogic.sessionScope(qualifiedId) {
                            withContext(Dispatchers.Default) {
                                logger.info { "message-fetch getRecentMessages request body start: conversationId=$conversationId" }
                                syncExecutor.request {
                                    messages.getRecentMessages(kaliumConvId, limit = FETCH_MESSAGES_LIMIT).first()
                                }
                            }
                        }
                    }

                if (timeoutFailure != null) return@runBlocking MessageStepResult.Failure(MessageFailureCategory.TIMEOUT)

                val mappedMessages =
                    result
                        ?.mapNotNull { message -> message.toConversationMessageOrNull() }
                        ?.sortedWith(compareBy<ConversationMessage> { it.timestamp }.thenBy { it.id })
                        ?: emptyList()

                logger.info { "message-fetch runtime outcome=success conversationId=$conversationId" }
                MessageStepResult.Success(mappedMessages)
            } catch (error: Throwable) {
                val mappedCategory = MessageFailureMapper.categoryFromThrowable(error)
                logger.error(error) {
                    "message-fetch runtime exception: conversationId=$conversationId " +
                        "exceptionClass=${error::class.qualifiedName} message=${error.message} mappedCategory=$mappedCategory"
                }
                MessageStepResult.Failure(mappedCategory)
            }
        }
    }

    override fun sendTypingStatus(
        session: AuthSession,
        conversationId: String,
        status: TypingStatus,
    ): MessageStepResult<Unit> {
        if (conversationId.isBlank()) {
            logger.debug { "sendTypingStatus: Validation failed - conversationId is blank" }
            return MessageStepResult.Failure(MessageFailureCategory.VALIDATION)
        }

        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "sendTypingStatus: Invalid user ID format: ${session.userId}" }
                    return MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)
                }
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                val kaliumConvId =
                    ConversationId(
                        value = conversationId,
                        domain = session.server ?: "wire.com",
                    )
                val kaliumStatus =
                    when (status) {
                        TypingStatus.STARTED -> Conversation.TypingIndicatorMode.STARTED
                        TypingStatus.STOPPED -> Conversation.TypingIndicatorMode.STOPPED
                    }

                withTimeout(sendTimeoutMs) {
                    coreLogic.sessionScope(qualifiedId) {
                        withContext(Dispatchers.Default) {
                            syncExecutor.request {
                                conversations.sendTypingEvent(kaliumConvId, kaliumStatus)
                            }
                        }
                    }
                }

                MessageStepResult.Success(Unit)
            } catch (_: TimeoutCancellationException) {
                MessageStepResult.Failure(MessageFailureCategory.TIMEOUT)
            } catch (error: Throwable) {
                MessageStepResult.Failure(MessageFailureMapper.categoryFromThrowable(error))
            }
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
    }
}

private fun Message.toConversationMessageOrNull(): ConversationMessage? {
    val text =
        when (val messageContent = content) {
            is MessageContent.Text -> messageContent.value
            else -> return null
        }

    val senderDisplayName =
        when (this) {
            is Message.Regular -> senderUserName
            is Message.Signaling -> senderUserName
            is Message.System -> senderUserName
        }

    return ConversationMessage(
        id = id,
        senderId = senderUserId.toString(),
        senderName = senderDisplayName ?: sender?.name ?: senderUserId.toString(),
        timestamp = date.toString(),
        content = text,
    )
}

private fun String.toQualifiedIdOrNull(): UserId? {
    val trimmed = trim()
    val atIndex = trimmed.lastIndexOf('@')
    val isValidFormat = atIndex > 0 && atIndex < trimmed.lastIndex

    return if (isValidFormat) {
        val value = trimmed.substring(0, atIndex)
        val domain = trimmed.substring(atIndex + 1)
        if (value.isNotBlank() && domain.isNotBlank()) UserId(value = value, domain = domain) else null
    } else {
        null
    }
}

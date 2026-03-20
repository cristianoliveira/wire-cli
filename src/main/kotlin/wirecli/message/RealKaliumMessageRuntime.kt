package wirecli.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageOperationResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import wirecli.auth.AuthSession
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

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
    TIMEOUT,
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    NOT_FOUND,
    UNKNOWN,
}

private const val PREFLIGHT_SYNC_TIMEOUT_MS = 15_000L
internal const val MESSAGE_SEND_TIMEOUT_ENV = "WIRECLI_MESSAGE_SEND_TIMEOUT_MS"
internal const val DEFAULT_SEND_TIMEOUT_MS = 60_000L
internal const val MAX_SEND_TIMEOUT_MS = 300_000L

internal fun resolveSendTimeoutMs(environment: Map<String, String>): Long {
    val rawValue = environment[MESSAGE_SEND_TIMEOUT_ENV]?.trim().orEmpty()
    if (rawValue.isEmpty()) {
        return DEFAULT_SEND_TIMEOUT_MS
    }

    val parsedValue = rawValue.toLongOrNull()
    if (parsedValue == null || parsedValue <= 0L) {
        logger.warn {
            "Invalid $MESSAGE_SEND_TIMEOUT_ENV='$rawValue'; using default ${DEFAULT_SEND_TIMEOUT_MS}ms"
        }
        return DEFAULT_SEND_TIMEOUT_MS
    }

    if (parsedValue > MAX_SEND_TIMEOUT_MS) {
        logger.warn {
            "$MESSAGE_SEND_TIMEOUT_ENV=$parsedValue exceeds max ${MAX_SEND_TIMEOUT_MS}ms; clamping to ${MAX_SEND_TIMEOUT_MS}ms"
        }
        return MAX_SEND_TIMEOUT_MS
    }

    return parsedValue
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
    ): MessageStepResult {
        // Validate inputs
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

                // 1. MLS sync preflight (MANDATORY before any send operation)
                val preflightStartNanos = System.nanoTime()
                logger.info { "message-send preflight sync start: conversationId=$conversationId" }
                try {
                    withTimeout(PREFLIGHT_SYNC_TIMEOUT_MS) {
                        coreLogic.sessionScope(qualifiedId) {
                            withContext(Dispatchers.Default) {
                                syncExecutor.request { waitUntilLiveOrFailure() }
                            }
                        }
                    }
                } catch (error: TimeoutCancellationException) {
                    logger.warn {
                        "message-send preflight sync timeout: conversationId=$conversationId timeoutMs=$PREFLIGHT_SYNC_TIMEOUT_MS"
                    }
                    return@runBlocking MessageStepResult.Failure(MessageFailureCategory.TIMEOUT)
                }
                val preflightElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - preflightStartNanos)
                logger.info {
                    "message-send preflight sync end: conversationId=$conversationId, elapsedMs=$preflightElapsedMs"
                }

                // 2. Create conversation ID with domain
                val kaliumConvId =
                    ConversationId(
                        value = conversationId,
                        domain = session.server ?: "wire.com",
                    )

                // 3. Send message via Kalium SDK
                val sendStartNanos = System.nanoTime()
                logger.info { "message-send sendTextMessage start: conversationId=$conversationId" }
                val result =
                    try {
                        withTimeout(sendTimeoutMs) {
                            coreLogic.sessionScope(qualifiedId) {
                                withContext(Dispatchers.Default) {
                                    logger.info { "message-send request start: conversationId=$conversationId" }
                                    val sendResult =
                                        syncExecutor.request {
                                            logger.info {
                                                "message-send sendTextMessage request body start: " +
                                                    "conversationId=$conversationId"
                                            }
                                            messages.sendTextMessage(kaliumConvId, text)
                                        }
                                    logger.info { "message-send request end: conversationId=$conversationId" }
                                    sendResult
                                }
                            }
                        }
                    } catch (error: TimeoutCancellationException) {
                        logger.warn {
                            "message-send sendTextMessage timeout: conversationId=$conversationId timeoutMs=$sendTimeoutMs"
                        }
                        return@runBlocking MessageStepResult.Failure(MessageFailureCategory.TIMEOUT)
                    }
                val sendElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sendStartNanos)
                logger.info {
                    "message-send sendTextMessage end: conversationId=$conversationId, elapsedMs=$sendElapsedMs"
                }

                // 4. Map SDK result to our failure categories
                return@runBlocking when (result) {
                    is MessageOperationResult.Success -> {
                        logger.info { "message-send runtime outcome=success conversationId=$conversationId" }
                        MessageStepResult.Success
                    }

                    is MessageOperationResult.Failure -> {
                        val mappedCategory = categoryFromCoreFailure(result.error)
                        logger.warn {
                            "message-send runtime outcome=failure conversationId=$conversationId " +
                                "failureClass=${result.error::class.simpleName} mappedCategory=$mappedCategory"
                        }
                        MessageStepResult.Failure(mappedCategory)
                    }
                }
            } catch (error: Throwable) {
                val mappedCategory = categoryFromThrowable(error)
                logger.error(error) {
                    "message-send runtime exception: conversationId=$conversationId " +
                        "exceptionClass=${error::class.qualifiedName} message=${error.message} mappedCategory=$mappedCategory"
                }
                MessageStepResult.Failure(mappedCategory)
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

        /**
         * Maps Kalium's CoreFailure types to our categorized failure types
         * Uses reflection to categorize based on failure class name and type
         */
        private fun categoryFromCoreFailure(failure: CoreFailure): MessageFailureCategory {
            // Check for network failures first
            if (failure is NetworkFailure) {
                return MessageFailureCategory.NETWORK
            }

            // Use class name and message for categorization as fallback
            val failureClassName = failure::class.simpleName.orEmpty()
            val failureMessage = failure.toString().lowercase()

            return when {
                // Network-related failures
                failureClassName.contains("network", ignoreCase = true) ||
                    failureClassName.contains("connection", ignoreCase = true) ->
                    MessageFailureCategory.NETWORK

                // Authorization/MLS/Sync failures
                failureClassName.contains("unauthorized", ignoreCase = true) ||
                    failureClassName.contains("auth", ignoreCase = true) ||
                    failureClassName.contains("legal", ignoreCase = true) ||
                    failureClassName.contains("sync", ignoreCase = true) ||
                    failureMessage.contains("unauthorized") ||
                    failureMessage.contains("legal hold") ->
                    MessageFailureCategory.UNAUTHORIZED

                // Not found failures
                failureClassName.contains("notfound", ignoreCase = true) ||
                    failureClassName.contains("not_found", ignoreCase = true) ||
                    failureClassName.contains("conversation", ignoreCase = true) ||
                    failureMessage.contains("not found") ->
                    MessageFailureCategory.NOT_FOUND

                // Server-side failures
                failureClassName.contains("server", ignoreCase = true) ||
                    failureClassName.contains("miscommunication", ignoreCase = true) ||
                    failureClassName.contains("invalid", ignoreCase = true) ||
                    failureMessage.contains("server") ||
                    failureMessage.contains("miscommunication") ->
                    MessageFailureCategory.SERVER

                // Validation failures
                failureClassName.contains("validation", ignoreCase = true) ||
                    failureMessage.contains("validation") ||
                    failureMessage.contains("invalid") ->
                    MessageFailureCategory.VALIDATION

                // Default to unknown
                else -> {
                    logger.debug { "categoryFromCoreFailure: Unmapped CoreFailure type: $failureClassName" }
                    MessageFailureCategory.UNKNOWN
                }
            }
        }

        /**
         * Maps generic throwable exceptions to failure categories
         * Used as fallback when SDK raises unexpected exceptions
         */
        private fun categoryFromThrowable(error: Throwable): MessageFailureCategory {
            return when {
                error.message?.contains("Unauthorized") == true ||
                    error.message?.contains("401") == true ||
                    error.message?.contains("UNAUTHORIZED") == true ->
                    MessageFailureCategory.UNAUTHORIZED

                error.message?.contains("Network") == true ||
                    error.message?.contains("Connection") == true ||
                    error.message?.contains("timeout") == true ||
                    error.message?.contains("NETWORK") == true ->
                    if (error.message?.contains("timeout", ignoreCase = true) == true) {
                        MessageFailureCategory.TIMEOUT
                    } else {
                        MessageFailureCategory.NETWORK
                    }

                error.message?.contains("404") == true ||
                    error.message?.contains("Not found") == true ||
                    error.message?.contains("NOT_FOUND") == true ->
                    MessageFailureCategory.NOT_FOUND

                error.message?.contains("Validation") == true ||
                    error.message?.contains("Invalid") == true ||
                    error.message?.contains("VALIDATION") == true ->
                    MessageFailureCategory.VALIDATION

                error.message?.contains("Server") == true ||
                    error.message?.contains("500") == true ||
                    error.message?.contains("SERVER") == true ->
                    MessageFailureCategory.SERVER

                else -> {
                    logger.debug { "categoryFromThrowable: Unmapped exception type: ${error::class.simpleName}, message: ${error.message}" }
                    MessageFailureCategory.UNKNOWN
                }
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

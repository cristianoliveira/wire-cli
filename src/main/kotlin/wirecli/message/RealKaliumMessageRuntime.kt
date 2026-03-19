package wirecli.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageOperationResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthSession
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
                logger.debug { "sendMessage: Starting message send operation for conversation $conversationId" }

                // 1. MLS sync preflight (MANDATORY before any send operation)
                logger.debug { "sendMessage: Executing MLS sync preflight" }
                coreLogic.sessionScope(qualifiedId) {
                    syncExecutor.request { waitUntilLiveOrFailure() }
                }
                logger.debug { "sendMessage: MLS sync preflight completed successfully" }

                // 2. Create conversation ID with domain
                val kaliumConvId =
                    ConversationId(
                        value = conversationId,
                        domain = session.server ?: "wire.com",
                    )

                // 3. Send message via Kalium SDK
                logger.debug { "sendMessage: Calling Kalium SDK sendTextMessage API" }
                val result =
                    coreLogic.sessionScope(qualifiedId) {
                        messages.sendTextMessage(kaliumConvId, text)
                    }

                // 4. Map SDK result to our failure categories
                return@runBlocking when (result) {
                    is MessageOperationResult.Success -> {
                        logger.info { "sendMessage: Message sent successfully to $conversationId" }
                        MessageStepResult.Success
                    }

                    is MessageOperationResult.Failure -> {
                        logger.warn { "sendMessage: Kalium SDK returned failure: ${result.error::class.simpleName}" }
                        MessageStepResult.Failure(categoryFromCoreFailure(result.error))
                    }
                }
            } catch (error: Throwable) {
                logger.error(error) { "sendMessage: Exception during message send operation" }
                MessageStepResult.Failure(categoryFromThrowable(error))
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
                    MessageFailureCategory.NETWORK

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

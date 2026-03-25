package wirecli.domains.message

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import wirecli.message.MessageFailureCategory
import wirecli.message.MessageStepResult
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Helper for message send operations, breaking down complex logic.
 * Reduces cyclomatic complexity and method length of main operations.
 */
internal object MessageOperationHelper {
    /**
     * Executes preflight MLS sync with timeout handling.
     * @return Failure category on timeout, null on success
     */
    suspend fun executePreflightSync(
        coreLogic: CoreLogic,
        qualifiedId: UserId,
        conversationId: String,
        timeoutMs: Long,
    ): MessageFailureCategory? {
        val preflightStartNanos = System.nanoTime()
        logger.info { "message-send preflight sync start: conversationId=$conversationId" }
        return try {
            withTimeout(timeoutMs) {
                coreLogic.sessionScope(qualifiedId) {
                    withContext(Dispatchers.Default) {
                        syncExecutor.request { waitUntilLiveOrFailure() }
                    }
                }
            }
            val preflightElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - preflightStartNanos)
            logger.info {
                "message-send preflight sync end: conversationId=$conversationId, elapsedMs=$preflightElapsedMs"
            }
            null // Success
        } catch (error: TimeoutCancellationException) {
            logger.warn {
                "message-send preflight sync timeout: conversationId=$conversationId timeoutMs=$timeoutMs"
            }
            MessageFailureCategory.TIMEOUT
        }
    }

    /**
     * Builds qualified conversation ID from parts.
     */
    fun buildQualifiedConversationId(
        conversationId: String,
        server: String?,
    ): ConversationId =
        ConversationId(
            value = conversationId,
            domain = server ?: "wire.com",
        )

    /**
     * Executes message send with timeout handling.
     */
    suspend fun <T> executeSendWithTimeout(
        coreLogic: CoreLogic,
        qualifiedId: UserId,
        conversationId: String,
        timeoutMs: Long,
        operation: suspend () -> T,
    ): Pair<T?, MessageStepResult<Unit>?> {
        val sendStartNanos = System.nanoTime()
        logger.info { "message-send sendTextMessage start: conversationId=$conversationId" }
        return try {
            val result =
                withTimeout(timeoutMs) {
                    withContext(Dispatchers.Default) {
                        logger.info { "message-send request start: conversationId=$conversationId" }
                        operation()
                    }
                }
            val sendElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sendStartNanos)
            logger.info {
                "message-send sendTextMessage end: conversationId=$conversationId, elapsedMs=$sendElapsedMs"
            }
            Pair(result, null)
        } catch (error: TimeoutCancellationException) {
            logger.warn {
                "message-send sendTextMessage timeout: conversationId=$conversationId timeoutMs=$timeoutMs"
            }
            Pair(null, MessageStepResult.Failure(MessageFailureCategory.TIMEOUT))
        }
    }

    /**
     * Executes message fetch with timeout handling.
     */
    suspend fun <T> executeFetchWithTimeout(
        conversationId: String,
        timeoutMs: Long,
        operation: suspend () -> T,
    ): Pair<T?, MessageStepResult<Unit>?> {
        val fetchStartNanos = System.nanoTime()
        logger.info { "message-fetch getRecentMessages start: conversationId=$conversationId" }
        return try {
            val result =
                withTimeout(timeoutMs) {
                    withContext(Dispatchers.Default) {
                        logger.info { "message-fetch request start: conversationId=$conversationId" }
                        operation()
                    }
                }
            val fetchElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - fetchStartNanos)
            logger.info {
                "message-fetch getRecentMessages end: conversationId=$conversationId, elapsedMs=$fetchElapsedMs"
            }
            Pair(result, null)
        } catch (error: TimeoutCancellationException) {
            logger.warn {
                "message-fetch getRecentMessages timeout: conversationId=$conversationId timeoutMs=$timeoutMs"
            }
            Pair(null, MessageStepResult.Failure(MessageFailureCategory.TIMEOUT))
        }
    }
}

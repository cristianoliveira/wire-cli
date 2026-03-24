package wirecli.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Maps Kalium's CoreFailure types to categorized failure types.
 * Separates failure categorization logic from runtime operations.
 */
internal object MessageFailureMapper {
    /**
     * Maps CoreFailure exceptions to MessageFailureCategory.
     * Uses class name and message patterns for categorization.
     */
    fun categoryFromCoreFailure(failure: CoreFailure): MessageFailureCategory {
        // Check for network failures first
        if (failure is NetworkFailure) {
            return MessageFailureCategory.NETWORK
        }

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
     * Maps generic throwable exceptions to failure categories.
     * Used as fallback when SDK raises unexpected exceptions.
     */
    fun categoryFromThrowable(error: Throwable): MessageFailureCategory {
        val message = error.message.orEmpty()

        return when {
            message.contains("Unauthorized", ignoreCase = true) ||
                message.contains("401") ||
                message.contains("UNAUTHORIZED") ->
                MessageFailureCategory.UNAUTHORIZED

            message.contains("timeout", ignoreCase = true) ->
                MessageFailureCategory.TIMEOUT

            message.contains("Network", ignoreCase = true) ||
                message.contains("Connection", ignoreCase = true) ||
                message.contains("NETWORK") ->
                MessageFailureCategory.NETWORK

            message.contains("404") ||
                message.contains("Not found", ignoreCase = true) ||
                message.contains("NOT_FOUND") ->
                MessageFailureCategory.NOT_FOUND

            message.contains("Validation", ignoreCase = true) ||
                message.contains("Invalid", ignoreCase = true) ||
                message.contains("VALIDATION") ->
                MessageFailureCategory.VALIDATION

            message.contains("Server", ignoreCase = true) ||
                message.contains("500") ||
                message.contains("SERVER") ->
                MessageFailureCategory.SERVER

            else -> {
                logger.debug { "categoryFromThrowable: Unmapped exception type: ${error::class.simpleName}, message: ${error.message}" }
                MessageFailureCategory.UNKNOWN
            }
        }
    }
}

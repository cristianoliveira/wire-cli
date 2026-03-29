package wirecli.domains.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.message.MessageFailureCategory

private val logger = KotlinLogging.logger {}

/**
 * Maps Kalium's CoreFailure types to categorized failure types.
 * Separates failure categorization logic from runtime operations.
 */
internal object MessageFailureMapper {
    private val unauthorizedClassTokens = listOf("unauthorized", "auth", "legal", "sync")
    private val unauthorizedMessageTokens = listOf("unauthorized", "legal hold")

    private val notFoundClassTokens = listOf("notfound", "not_found", "conversation")
    private val notFoundMessageTokens = listOf("not found")

    private val serverClassTokens = listOf("server", "miscommunication", "invalid")
    private val serverMessageTokens = listOf("server", "miscommunication")

    private val validationClassTokens = listOf("validation")
    private val validationMessageTokens = listOf("validation", "invalid")

    private val throwableCategoryMatchers =
        listOf(
            MessageFailureCategory.UNAUTHORIZED to listOf("unauthorized", "401"),
            MessageFailureCategory.TIMEOUT to listOf("timeout"),
            MessageFailureCategory.NETWORK to listOf("network", "connection"),
            MessageFailureCategory.NOT_FOUND to listOf("404", "not found", "not_found"),
            MessageFailureCategory.VALIDATION to listOf("validation", "invalid"),
            MessageFailureCategory.SERVER to listOf("server", "500"),
        )

    /**
     * Maps CoreFailure exceptions to MessageFailureCategory.
     * Uses class name and message patterns for categorization.
     */
    fun categoryFromCoreFailure(failure: CoreFailure): MessageFailureCategory {
        if (failure is NetworkFailure) {
            return MessageFailureCategory.NETWORK
        }

        val failureClassName = failure::class.simpleName.orEmpty().lowercase()
        val failureMessage = failure.toString().lowercase()

        return when {
            containsAnyToken(failureClassName, "network", "connection") ->
                MessageFailureCategory.NETWORK

            matchesFailureGroup(
                className = failureClassName,
                message = failureMessage,
                classTokens = unauthorizedClassTokens,
                messageTokens = unauthorizedMessageTokens,
            ) ->
                MessageFailureCategory.UNAUTHORIZED

            matchesFailureGroup(
                className = failureClassName,
                message = failureMessage,
                classTokens = notFoundClassTokens,
                messageTokens = notFoundMessageTokens,
            ) ->
                MessageFailureCategory.NOT_FOUND

            matchesFailureGroup(
                className = failureClassName,
                message = failureMessage,
                classTokens = serverClassTokens,
                messageTokens = serverMessageTokens,
            ) ->
                MessageFailureCategory.SERVER

            matchesFailureGroup(
                className = failureClassName,
                message = failureMessage,
                classTokens = validationClassTokens,
                messageTokens = validationMessageTokens,
            ) ->
                MessageFailureCategory.VALIDATION

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
        val message = error.message.orEmpty().lowercase()

        return throwableCategoryMatchers
            .firstOrNull { (_, tokens) -> containsAnyToken(message, tokens) }
            ?.first
            ?: run {
                logger.debug {
                    "categoryFromThrowable: Unmapped exception type: " +
                        "${error::class.simpleName}, message: ${error.message}"
                }
                MessageFailureCategory.UNKNOWN
            }
    }

    private fun matchesFailureGroup(
        className: String,
        message: String,
        classTokens: List<String>,
        messageTokens: List<String>,
    ): Boolean {
        return containsAnyToken(className, classTokens) ||
            containsAnyToken(message, messageTokens)
    }

    private fun containsAnyToken(
        text: String,
        tokens: List<String>,
    ): Boolean {
        return tokens.any(text::contains)
    }

    private fun containsAnyToken(
        text: String,
        vararg tokens: String,
    ): Boolean {
        return containsAnyToken(text, tokens.toList())
    }
}

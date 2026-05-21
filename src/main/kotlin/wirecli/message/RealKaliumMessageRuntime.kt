package wirecli.message

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageOperationResult
import com.wire.kalium.logic.feature.message.SearchMessagesGloballyUseCase
import com.wire.kalium.logic.feature.message.SearchMessagesInConversationUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    fun observeMessages(
        session: AuthSession,
        conversationId: String,
    ): Flow<FetchMessagesResult> =
        flowOf(
            when (val result = fetchMessages(session, conversationId)) {
                is MessageStepResult.Success ->
                    FetchMessagesResult.Success(FetchMessagesView(conversationId, result.value))
                is MessageStepResult.Failure -> mapFetchFailure(result.category)
            },
        )

    fun searchMessages(
        session: AuthSession,
        query: String,
        conversationId: String? = null,
        limit: Int = 10,
    ): MessageStepResult<List<MessageSearchResult>> = MessageStepResult.Failure(MessageFailureCategory.UNKNOWN)

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

    @Suppress("LongMethod")
    override fun sendMessage(
        session: AuthSession,
        conversationId: String,
        text: String,
    ): MessageStepResult<Unit> {
        val validationFailure =
            when {
                conversationId.isBlank() -> {
                    logger.debug { "sendMessage: Validation failed - conversationId is blank" }
                    MessageFailureCategory.VALIDATION
                }

                text.isBlank() -> {
                    logger.debug { "sendMessage: Validation failed - text is blank" }
                    MessageFailureCategory.VALIDATION
                }

                else -> null
            }
        val qualifiedId = session.userId.toQualifiedIdOrNull()
        return when {
            validationFailure != null -> MessageStepResult.Failure(validationFailure)
            qualifiedId == null -> {
                logger.warn { "sendMessage: Invalid user ID format: ${session.userId}" }
                MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)
            }

            else -> {
                activeSessionUserIds += qualifiedId
                runBlocking {
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
                        if (preflightFailureCategory != null) {
                            MessageStepResult.Failure(preflightFailureCategory)
                        } else {
                            val kaliumConvId =
                                MessageOperationHelper.buildQualifiedConversationId(
                                    conversationId,
                                    session.server,
                                )

                            val (result, timeoutFailure) =
                                MessageOperationHelper.executeSendWithTimeout(
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

                            when {
                                timeoutFailure != null -> MessageStepResult.Failure(MessageFailureCategory.TIMEOUT)
                                result is MessageOperationResult.Success -> {
                                    logger.info { "message-send runtime outcome=success conversationId=$conversationId" }
                                    MessageStepResult.Success(Unit)
                                }

                                result is MessageOperationResult.Failure -> {
                                    val mappedCategory = MessageFailureMapper.categoryFromCoreFailure(result.error)
                                    logger.warn {
                                        "message-send runtime outcome=failure conversationId=$conversationId " +
                                            "failureClass=${result.error::class.simpleName} mappedCategory=$mappedCategory"
                                    }
                                    MessageStepResult.Failure(mappedCategory)
                                }

                                else -> MessageStepResult.Failure(MessageFailureCategory.UNKNOWN)
                            }
                        }
                    } catch (
                        @Suppress("TooGenericExceptionCaught") error: Throwable,
                    ) {
                        val mappedCategory = MessageFailureMapper.categoryFromThrowable(error)
                        logger.error(error) {
                            "message-send runtime exception: conversationId=$conversationId " +
                                "exceptionClass=${error::class.qualifiedName} message=${error.message} mappedCategory=$mappedCategory"
                        }
                        MessageStepResult.Failure(mappedCategory)
                    }
                }
            }
        }
    }

    @Suppress("LongMethod")
    override fun fetchMessages(
        session: AuthSession,
        conversationId: String,
    ): MessageStepResult<List<ConversationMessage>> {
        val isConversationBlank = conversationId.isBlank()
        if (isConversationBlank) {
            logger.debug { "fetchMessages: Validation failed - conversationId is blank" }
        }
        val qualifiedId = session.userId.toQualifiedIdOrNull()
        return when {
            isConversationBlank -> MessageStepResult.Failure(MessageFailureCategory.VALIDATION)
            qualifiedId == null -> {
                logger.warn { "fetchMessages: Invalid user ID format: ${session.userId}" }
                MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)
            }

            else -> {
                activeSessionUserIds += qualifiedId
                runBlocking {
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
                        if (preflightFailureCategory != null) {
                            MessageStepResult.Failure(preflightFailureCategory)
                        } else {
                            val kaliumConvId =
                                MessageOperationHelper.buildQualifiedConversationId(
                                    conversationId,
                                    session.server,
                                )

                            val (result, timeoutFailure) =
                                MessageOperationHelper.executeFetchWithTimeout(conversationId, sendTimeoutMs) {
                                    coreLogic.sessionScope(qualifiedId) {
                                        withContext(Dispatchers.Default) {
                                            logger.info {
                                                "message-fetch getRecentMessages request body start: conversationId=$conversationId"
                                            }
                                            syncExecutor.request {
                                                messages.getRecentMessages(kaliumConvId, limit = FETCH_MESSAGES_LIMIT).first()
                                            }
                                        }
                                    }
                                }
                            when {
                                timeoutFailure != null -> MessageStepResult.Failure(MessageFailureCategory.TIMEOUT)
                                else -> {
                                    val mappedMessages = mapFetchedMessages(result)
                                    logger.info { "message-fetch runtime outcome=success conversationId=$conversationId" }
                                    MessageStepResult.Success(mappedMessages)
                                }
                            }
                        }
                    } catch (
                        @Suppress("TooGenericExceptionCaught") error: Throwable,
                    ) {
                        val mappedCategory = MessageFailureMapper.categoryFromThrowable(error)
                        logger.error(error) {
                            "message-fetch runtime exception: conversationId=$conversationId " +
                                "exceptionClass=${error::class.qualifiedName} message=${error.message} mappedCategory=$mappedCategory"
                        }
                        MessageStepResult.Failure(mappedCategory)
                    }
                }
            }
        }
    }

    override fun observeMessages(
        session: AuthSession,
        conversationId: String,
    ): Flow<FetchMessagesResult> {
        val isConversationBlank = conversationId.isBlank()
        val qualifiedId = session.userId.toQualifiedIdOrNull()
        return when {
            isConversationBlank ->
                flowOf(
                    FetchMessagesResult.Failure(
                        MessageUserMessages.VALIDATION_ERROR,
                        MessageExitCodes.VALIDATION_ERROR,
                    ),
                )
            qualifiedId == null ->
                flowOf(
                    FetchMessagesResult.Failure(
                        MessageUserMessages.UNAUTHORIZED,
                        MessageExitCodes.UNAUTHORIZED,
                    ),
                )
            else -> {
                activeSessionUserIds += qualifiedId
                val kaliumConvId = MessageOperationHelper.buildQualifiedConversationId(conversationId, session.server)
                flow {
                    coreLogic.sessionScope(qualifiedId) {
                        emitAll(
                            messages.getRecentMessages(kaliumConvId, limit = FETCH_MESSAGES_LIMIT)
                                .map { messages ->
                                    FetchMessagesResult.Success(
                                        FetchMessagesView(
                                            conversationId = conversationId,
                                            messages = mapFetchedMessages(messages),
                                        ),
                                    ) as FetchMessagesResult
                                },
                        )
                    }
                }.catch { error ->
                    val category = MessageFailureMapper.categoryFromThrowable(error)
                    logger.error(error) {
                        "message-watch runtime exception: conversationId=$conversationId " +
                            "exceptionClass=${error::class.qualifiedName} message=${error.message} " +
                            "mappedCategory=$category"
                    }
                    emit(mapFetchFailure(category))
                }
            }
        }
    }

    override fun searchMessages(
        session: AuthSession,
        query: String,
        conversationId: String?,
        limit: Int,
    ): MessageStepResult<List<MessageSearchResult>> {
        val qualifiedId = session.userId.toQualifiedIdOrNull()
        return when {
            query.isBlank() -> {
                logger.debug { "searchMessages: Validation failed - query is blank" }
                MessageStepResult.Failure(MessageFailureCategory.VALIDATION)
            }
            qualifiedId == null -> {
                logger.warn { "searchMessages: Invalid user ID format: ${session.userId}" }
                MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)
            }
            else -> executeSearchMessages(session, qualifiedId, query, conversationId, limit)
        }
    }

    private fun executeSearchMessages(
        session: AuthSession,
        qualifiedId: UserId,
        query: String,
        conversationId: String?,
        limit: Int,
    ): MessageStepResult<List<MessageSearchResult>> {
        activeSessionUserIds += qualifiedId
        return runBlocking {
            try {
                logger.info {
                    "message-search runtime start: queryLength=${query.length}, " +
                        "conversationId=$conversationId, userId=${session.userId}"
                }

                val result =
                    withTimeout(sendTimeoutMs) {
                        coreLogic.sessionScope(qualifiedId) {
                            withContext(Dispatchers.Default) {
                                syncExecutor.request {
                                    if (conversationId == null) {
                                        val searchResult = messages.searchMessagesGlobally(query, limit)
                                        mapGlobalSearchResult(searchResult, query)
                                    } else {
                                        val kaliumConvId =
                                            MessageOperationHelper.buildQualifiedConversationId(
                                                conversationId,
                                                session.server,
                                            )
                                        mapConversationSearchResult(
                                            messages.searchMessagesInConversation(kaliumConvId, query, limit),
                                            query,
                                        )
                                    }
                                }
                            }
                        }
                    }
                logSearchOutcome(query, result)
                result
            } catch (_: TimeoutCancellationException) {
                logger.warn {
                    "message-search runtime timeout: queryLength=${query.length}, " +
                        "conversationId=$conversationId"
                }
                MessageStepResult.Failure(MessageFailureCategory.TIMEOUT)
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                val mappedCategory = MessageFailureMapper.categoryFromThrowable(error)
                logger.error(error) {
                    "message-search runtime exception: queryLength=${query.length} " +
                        "exceptionClass=${error::class.qualifiedName} message=${error.message} " +
                        "mappedCategory=$mappedCategory"
                }
                MessageStepResult.Failure(mappedCategory)
            }
        }
    }

    private fun mapConversationSearchResult(
        searchResult: SearchMessagesInConversationUseCase.Result,
        query: String,
    ): MessageStepResult<List<MessageSearchResult>> {
        return when (searchResult) {
            is SearchMessagesInConversationUseCase.Result.Success ->
                MessageStepResult.Success(mapSearchResults(searchResult.messages, query))
            is SearchMessagesInConversationUseCase.Result.Failure ->
                MessageStepResult.Failure(MessageFailureMapper.categoryFromCoreFailure(searchResult.cause))
        }
    }

    private fun mapGlobalSearchResult(
        searchResult: SearchMessagesGloballyUseCase.Result,
        query: String,
    ): MessageStepResult<List<MessageSearchResult>> {
        return when (searchResult) {
            is SearchMessagesGloballyUseCase.Result.Success ->
                MessageStepResult.Success(mapSearchResults(searchResult.messages, query))
            is SearchMessagesGloballyUseCase.Result.Failure ->
                MessageStepResult.Failure(MessageFailureMapper.categoryFromCoreFailure(searchResult.cause))
        }
    }

    private fun logSearchOutcome(
        query: String,
        result: MessageStepResult<List<MessageSearchResult>>,
    ) {
        when (result) {
            is MessageStepResult.Success ->
                logger.info {
                    "message-search runtime outcome=success " +
                        "queryLength=${query.length} count=${result.value.size}"
                }
            is MessageStepResult.Failure ->
                logger.warn {
                    "message-search runtime outcome=failure " +
                        "queryLength=${query.length} category=${result.category}"
                }
        }
    }

    override fun sendTypingStatus(
        session: AuthSession,
        conversationId: String,
        status: TypingStatus,
    ): MessageStepResult<Unit> {
        val isConversationBlank = conversationId.isBlank()
        if (isConversationBlank) {
            logger.debug { "sendTypingStatus: Validation failed - conversationId is blank" }
        }
        val qualifiedId = session.userId.toQualifiedIdOrNull()
        return when {
            isConversationBlank -> MessageStepResult.Failure(MessageFailureCategory.VALIDATION)
            qualifiedId == null -> {
                logger.warn { "sendTypingStatus: Invalid user ID format: ${session.userId}" }
                MessageStepResult.Failure(MessageFailureCategory.UNAUTHORIZED)
            }

            else -> {
                activeSessionUserIds += qualifiedId
                runBlocking {
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
                    } catch (
                        @Suppress("TooGenericExceptionCaught") error: Throwable,
                    ) {
                        MessageStepResult.Failure(MessageFailureMapper.categoryFromThrowable(error))
                    }
                }
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

private fun mapFetchFailure(category: MessageFailureCategory): FetchMessagesResult.Failure {
    val (message, exitCode) =
        when (category) {
            MessageFailureCategory.VALIDATION -> MessageUserMessages.VALIDATION_ERROR to MessageExitCodes.VALIDATION_ERROR
            MessageFailureCategory.UNAUTHORIZED -> MessageUserMessages.UNAUTHORIZED to MessageExitCodes.UNAUTHORIZED
            MessageFailureCategory.TIMEOUT -> MessageUserMessages.FETCH_NETWORK_ERROR to MessageExitCodes.NETWORK_ERROR
            MessageFailureCategory.NETWORK -> MessageUserMessages.FETCH_NETWORK_ERROR to MessageExitCodes.NETWORK_ERROR
            MessageFailureCategory.SERVER -> MessageUserMessages.FETCH_SERVER_ERROR to MessageExitCodes.SERVER_ERROR
            MessageFailureCategory.NOT_FOUND -> MessageUserMessages.CONVERSATION_NOT_FOUND to MessageExitCodes.NOT_FOUND
            MessageFailureCategory.UNKNOWN -> MessageUserMessages.FETCH_UNKNOWN_ERROR to MessageExitCodes.SERVER_ERROR
        }
    return FetchMessagesResult.Failure(message = message, exitCode = exitCode)
}

private fun mapFetchedMessages(result: List<Message>?): List<ConversationMessage> {
    return result
        ?.mapNotNull { message -> message.toConversationMessageOrNull() }
        ?.sortedWith(compareBy<ConversationMessage> { it.timestamp }.thenBy { it.id })
        ?: emptyList()
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

private fun mapSearchResults(
    result: List<Message.Standalone>,
    query: String,
): List<MessageSearchResult> {
    return result.mapNotNull { message ->
        val text =
            when (val messageContent = message.content) {
                is MessageContent.Text -> messageContent.value
                is MessageContent.System -> return@mapNotNull null
                else -> return@mapNotNull null
            }

        val senderDisplayName = (message as? Message.Regular)?.senderUserName

        MessageSearchResult(
            conversationId = message.conversationId.toString(),
            messageId = message.id,
            senderId = message.senderUserId.toString(),
            senderName = senderDisplayName ?: message.sender?.name ?: message.senderUserId.toString(),
            timestamp = message.date.toString(),
            content = text,
            matchSnippet = buildMatchSnippet(text, query),
        )
    }
}

private fun buildMatchSnippet(
    content: String,
    query: String,
): String {
    val lowerContent = content.lowercase()
    val lowerQuery = query.lowercase()
    val index = lowerContent.indexOf(lowerQuery)
    if (index < 0) return "..."
    val start = maxOf(0, index - 20)
    val end = minOf(content.length, index + query.length + 20)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < content.length) "…" else ""
    return "$prefix${content.substring(start, end)}$suffix"
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

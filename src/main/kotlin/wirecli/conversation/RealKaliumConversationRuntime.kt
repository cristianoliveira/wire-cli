package wirecli.conversation

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationFilter
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.ObserveConversationDetailsUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthSession
import wirecli.config.KaliumCliMode
import wirecli.config.kaliumCliConfigs
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

internal typealias RealKaliumConversationRuntime = ConversationRuntime

/**
 * SDK-based implementation of ConversationRuntime using CoreLogic
 */
internal class SdkKaliumConversationRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : ConversationRuntime {
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

    override fun listConversations(session: AuthSession): ConversationStepResult<List<ConversationDetails>> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return ConversationStepResult.Failure(ConversationFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                val conversationDetails =
                    coreLogic.sessionScope(qualifiedId) {
                        if (!cliMode.disableSessionSyncWait) {
                            syncExecutor.request { waitUntilLiveOrFailure() }
                        }
                        conversations.observeConversationListDetailsWithEvents(
                            fromArchive = false,
                            conversationFilter = ConversationFilter.All,
                        )
                            .firstOrNull()
                            ?.map { it.conversationDetails }
                            ?: emptyList()
                    }

                ConversationStepResult.Success(conversationDetails)
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                ConversationStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getMembers(
        session: AuthSession,
        conversationId: String,
    ): ConversationStepResult<List<MemberDetails>> {
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

                val members =
                    coreLogic.sessionScope(qualifiedId) {
                        conversations.observeConversationMembers(kaliumConvId)
                            .firstOrNull()
                            ?: emptyList()
                    }

                ConversationStepResult.Success(members)
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                ConversationStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getConversation(
        session: AuthSession,
        conversationId: String,
    ): ConversationStepResult<ConversationDetails> {
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
                        conversations.observeConversationDetails(kaliumConvId)
                            .firstOrNull()
                    }

                if (result is ObserveConversationDetailsUseCase.Result.Success) {
                    ConversationStepResult.Success(result.conversationDetails)
                } else {
                    ConversationStepResult.Failure(ConversationFailureCategory.NOT_FOUND)
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
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
                    } catch (
                        @Suppress("TooGenericExceptionCaught")
                        e: Exception,
                    ) {
                        logger.debug(e) { "Ignoring conversation shutdown cleanup failure for userId=$userId" }
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
    val isValidFormat = atIndex > 0 && atIndex < trimmed.lastIndex

    return if (isValidFormat) {
        val value = trimmed.substring(0, atIndex)
        val domain = trimmed.substring(atIndex + 1)
        if (value.isNotBlank() && domain.isNotBlank()) UserId(value = value, domain = domain) else null
    } else {
        null
    }
}

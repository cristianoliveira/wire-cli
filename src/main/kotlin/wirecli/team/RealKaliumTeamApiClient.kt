package wirecli.team

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.config.KaliumCliMode
import wirecli.config.kaliumCliConfigs

private val logger = KotlinLogging.logger {}

internal class RealKaliumTeamApiClient(
    private val runtime: TeamRuntime,
) : TeamApiClient {
    override fun readTeam(session: AuthSession): TeamReadResult {
        logger.debug { "RealKaliumTeamApiClient: reading team" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session)) {
                is TeamStepResult.Success -> scope.value
                is TeamStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope: ${scope.category}" }
                    return scope.toReadFailure()
                }
            }

        return when (val result = runtime.readTeam(sessionScope)) {
            is TeamStepResult.Success -> {
                logger.info { "Successfully read team ${result.value.id}" }
                TeamReadResult.Success(view = result.value)
            }

            is TeamStepResult.Failure -> {
                logger.warn { "Failed to read team: ${result.category}" }
                result.toReadFailure()
            }
        }
    }
}

internal typealias RealKaliumTeamRuntime = TeamRuntime

internal class SdkKaliumTeamRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : TeamRuntime {
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

    override fun resolveSessionScope(session: AuthSession): TeamStepResult<KaliumTeamSessionScope> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return TeamStepResult.Failure(TeamFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                if (!cliMode.disableSessionSyncWait) {
                    coreLogic.sessionScope(qualifiedId) {
                        syncExecutor.request { waitUntilLiveOrFailure() }
                    }
                }
                TeamStepResult.Success(
                    KaliumTeamSessionScope(
                        userId = session.userId,
                        server = session.server,
                    ),
                )
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to resolve team session scope for ${session.userId}" }
                TeamStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun readTeam(sessionScope: KaliumTeamSessionScope): TeamStepResult<TeamView> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return TeamStepResult.Failure(TeamFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                val (selfUser, team) =
                    coreLogic.sessionScope(qualifiedId) {
                        users.observeSelfUserWithTeam().first()
                    }

                if (team == null) {
                    return@runBlocking TeamStepResult.Failure(TeamFailureCategory.NO_TEAM)
                }

                TeamStepResult.Success(
                    TeamView(
                        id = team.id,
                        name = team.name,
                        icon = team.icon,
                        creator = "",
                        binding = false,
                    ),
                )
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to read team" }
                TeamStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun shutdown() {
        if (!coreLogicLazy.isInitialized()) return

        runBlocking {
            activeSessionUserIds.forEach { userId ->
                coreLogic.sessionScope(userId) { cancel() }
            }
        }
        activeSessionUserIds.clear()
        coreLogic.getGlobalScope().cancel()
        logger.debug { "SdkKaliumTeamRuntime: shutdown complete" }
    }

    private fun categoryFromCoreFailure(failure: CoreFailure): TeamFailureCategory =
        when (failure) {
            is NetworkFailure.NoNetworkConnection,
            is NetworkFailure.ProxyError,
            -> TeamFailureCategory.NETWORK

            is NetworkFailure.ServerMiscommunication -> TeamFailureCategory.SERVER

            is NetworkFailure.FederatedBackendFailure,
            NetworkFailure.FeatureNotSupported,
            is NetworkFailure.MlsMessageRejectedFailure,
            -> TeamFailureCategory.SERVER

            else -> TeamFailureCategory.UNKNOWN
        }

    @Suppress("TooGenericExceptionCaught")
    private fun categoryFromThrowable(error: Throwable): TeamFailureCategory {
        val message = error.message.orEmpty()
        return when {
            message.contains("network", ignoreCase = true) -> TeamFailureCategory.NETWORK
            message.contains("unauthorized", ignoreCase = true) -> TeamFailureCategory.UNAUTHORIZED
            message.contains("auth", ignoreCase = true) -> TeamFailureCategory.UNAUTHORIZED
            message.contains("not found", ignoreCase = true) -> TeamFailureCategory.NOT_FOUND
            message.isNotEmpty() -> TeamFailureCategory.SERVER
            else -> TeamFailureCategory.UNKNOWN
        }
    }

    private fun resolveHomeDirectory(env: Map<String, String>): String {
        val home = env["HOME"]?.trim()
        if (!home.isNullOrEmpty()) return home
        return System.getProperty("user.home")
    }
}

private fun String.toQualifiedIdOrNull(): UserId? {
    val trimmed = trim()
    val atIndex = trimmed.lastIndexOf('@')
    val isValidFormat = atIndex > 0 && atIndex < trimmed.lastIndex

    return if (isValidFormat) {
        val value = trimmed.substring(0, atIndex)
        val domain = trimmed.substring(atIndex + 1)
        if (value.isNotBlank() && domain.isNotBlank()) {
            QualifiedID(value = value, domain = domain)
        } else {
            null
        }
    } else {
        null
    }
}

private fun TeamStepResult.Failure.toReadFailure(): TeamReadResult.Failure {
    val (message, exitCode) = TeamFailureMapper.toReadFailureInfo(category)
    return TeamReadResult.Failure(message = message, exitCode = exitCode)
}

internal object TeamFailureMapper {
    data class FailureInfo(val message: String, val exitCode: Int)

    fun toReadFailureInfo(category: TeamFailureCategory): FailureInfo =
        FailureInfo(
            message =
                when (category) {
                    TeamFailureCategory.NETWORK -> TeamMessages.NETWORK_FAILURE
                    TeamFailureCategory.SERVER -> TeamMessages.SERVER_FAILURE
                    TeamFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
                    TeamFailureCategory.NOT_FOUND -> TeamMessages.TEAM_NOT_FOUND
                    TeamFailureCategory.NO_TEAM -> TeamMessages.NOT_A_TEAM_MEMBER
                    TeamFailureCategory.UNKNOWN -> TeamMessages.UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    private fun categoryToExitCode(category: TeamFailureCategory): Int =
        when (category) {
            TeamFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            TeamFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            TeamFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            TeamFailureCategory.NOT_FOUND -> TeamExitCodes.NOT_FOUND
            TeamFailureCategory.NO_TEAM -> TeamExitCodes.NO_TEAM
            TeamFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }
}

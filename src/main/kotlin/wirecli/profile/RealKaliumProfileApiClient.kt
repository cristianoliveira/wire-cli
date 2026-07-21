package wirecli.profile

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.user.DisplayNameUpdateResult
import com.wire.kalium.logic.feature.user.SetUserHandleResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.config.KaliumCliMode
import wirecli.config.SessionSyncRequirement
import wirecli.config.kaliumCliConfigs

private val logger = KotlinLogging.logger {}
private const val DEFAULT_PROFILE_SYNC_TIMEOUT_MS = 10_000L

internal class RealKaliumProfileApiClient(
    private val runtime: ProfileRuntime,
) : ProfileApiClient {
    override fun updateProfile(
        session: AuthSession,
        update: ProfileUpdate,
    ): ProfileUpdateResult {
        logger.debug { "RealKaliumProfileApiClient: Updating profile for user: ${session.userId}" }
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, requireLiveSync = true)) {
                is ProfileStepResult.Success -> scope.value
                is ProfileStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for profile update: ${scope.category}" }
                    return scope.toProfileUpdateFailure()
                }
            }

        return when (val result = runtime.updateSelf(sessionScope, update.name, update.handle)) {
            is ProfileStepResult.Success -> {
                logger.info { "Profile updated successfully" }
                ProfileUpdateResult.Success(
                    profile =
                        ProfileView(
                            name = update.name,
                            email = null,
                            handle = update.handle,
                        ),
                )
            }

            is ProfileStepResult.Failure -> {
                logger.warn { "Failed to update profile: ${result.category}" }
                result.toProfileUpdateFailure()
            }
        }
    }

    override fun fetchProfile(session: AuthSession): ProfileResult {
        logger.debug { "RealKaliumProfileApiClient: Fetching profile for user: ${session.userId}" }
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, requireLiveSync = false)) {
                is ProfileStepResult.Success -> scope.value
                is ProfileStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for profile: ${scope.category}" }
                    return scope.toProfileFailure()
                }
            }

        return when (val selfUser = runtime.getSelfUser(sessionScope)) {
            is ProfileStepResult.Success -> {
                logger.info {
                    "Successfully retrieved profile: name=${selfUser.value.name}, email=${selfUser.value.email}, handle=${selfUser.value.handle}"
                }
                ProfileResult.Success(
                    profile =
                        ProfileView(
                            name = selfUser.value.name,
                            email = selfUser.value.email,
                            handle = selfUser.value.handle,
                        ),
                )
            }

            is ProfileStepResult.Failure -> {
                logger.warn { "Failed to retrieve self user: ${selfUser.category}" }
                selfUser.toProfileFailure()
            }
        }
    }
}

internal typealias RealKaliumProfileRuntime = ProfileRuntime

internal class SdkKaliumProfileRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
    private val syncTimeoutMs: Long = DEFAULT_PROFILE_SYNC_TIMEOUT_MS,
    private val sessionSyncWait: (suspend (UserId) -> Unit)? = null,
) : ProfileRuntime {
    private val activeSessionUserIds = mutableSetOf<UserId>()

    private val coreLogicLazy =
        lazy {
            logger.debug { "SdkKaliumProfileRuntime: Initializing Kalium CoreLogic for profile runtime" }
            val rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium"
            logger.debug { "SdkKaliumProfileRuntime: Kalium data path: $rootPath" }
            val configs = kaliumCliConfigs(cliMode)
            logger.debug { "SdkKaliumProfileRuntime: Kalium configs loaded for mode: $cliMode" }
            CoreLogic(
                rootPath = rootPath,
                kaliumConfigs = configs,
                userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
            )
        }
    private val coreLogic: CoreLogic by coreLogicLazy

    override fun resolveSessionScope(
        session: AuthSession,
        requireLiveSync: Boolean,
    ): ProfileStepResult<KaliumProfileSessionScope> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "Invalid user ID format: ${session.userId}" }
                    return ProfileStepResult.Failure(ProfileFailureCategory.UNAUTHORIZED)
                }
        activeSessionUserIds += qualifiedId

        return runBlocking {
            try {
                if (requireLiveSync && cliMode.shouldAwaitLiveSessionSync(SessionSyncRequirement.WRITE)) {
                    withTimeout(syncTimeoutMs) {
                        val injectedSyncWait = sessionSyncWait
                        if (injectedSyncWait != null) {
                            injectedSyncWait(qualifiedId)
                        } else {
                            coreLogic.sessionScope(qualifiedId) {
                                syncExecutor.request { waitUntilLiveOrFailure() }
                            }
                        }
                    }
                }
                logger.debug { "Profile session scope resolved successfully for user: ${session.userId}" }
                ProfileStepResult.Success(
                    KaliumProfileSessionScope(
                        userId = session.userId,
                        server = session.server,
                    ),
                )
            } catch (_: TimeoutCancellationException) {
                logger.warn { "Timed out waiting for profile sync for user: ${session.userId}" }
                ProfileStepResult.Failure(ProfileFailureCategory.TIMEOUT)
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to resolve profile session scope for user: ${session.userId}" }
                ProfileStepResult.Failure(categoryFromThrowable(error))
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
        coreLogic.getGlobalScope().cancel()
        logger.debug { "SdkKaliumProfileRuntime: Profile runtime shutdown complete" }
    }

    override fun getSelfUser(sessionScope: KaliumProfileSessionScope): ProfileStepResult<KaliumSelfUser> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "Invalid user ID format for self user: ${sessionScope.userId}" }
                    return ProfileStepResult.Failure(ProfileFailureCategory.UNAUTHORIZED)
                }

        return runBlocking {
            try {
                logger.debug { "Fetching self user data for: $qualifiedId" }
                val selfUser: SelfUser =
                    coreLogic.sessionScope(qualifiedId) {
                        users.getSelfUser()
                    }
                        ?: error(
                            "Self user data is unavailable - this indicates a failure to fetch profile information.",
                        )
                logger.debug { "Self user data retrieved successfully: name=${selfUser.name}, handle=${selfUser.handle}" }
                ProfileStepResult.Success(
                    KaliumSelfUser(
                        name = selfUser.name,
                        email = selfUser.email,
                        handle = selfUser.handle,
                    ),
                )
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Throwable,
            ) {
                logger.error(error) { "Failed to fetch self user for: $qualifiedId" }
                ProfileStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun updateSelf(
        sessionScope: KaliumProfileSessionScope,
        name: String?,
        handle: String?,
    ): ProfileStepResult<Unit> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "Invalid user ID format for profile update: ${sessionScope.userId}" }
                    return ProfileStepResult.Failure(ProfileFailureCategory.UNAUTHORIZED)
                }

        return runBlocking {
            try {
                if (name != null) {
                    logger.debug { "Updating display name to: $name for: $qualifiedId" }
                    val result =
                        coreLogic.sessionScope(qualifiedId) {
                            users.updateDisplayName(name)
                        }
                    when (result) {
                        is DisplayNameUpdateResult.Failure -> {
                            logger.warn { "Failed to update display name: ${result.coreFailure}" }
                            throw IllegalStateException("Failed to update display name")
                        }

                        is DisplayNameUpdateResult.Success -> {
                            logger.info { "Display name updated successfully" }
                        }
                    }
                }
                if (handle != null) {
                    logger.debug { "Setting user handle to: $handle for: $qualifiedId" }
                    val result =
                        coreLogic.sessionScope(qualifiedId) {
                            users.setUserHandle(handle)
                        }
                    when (result) {
                        is SetUserHandleResult.Failure.InvalidHandle -> {
                            logger.warn { "Invalid handle: $handle" }
                            throw IllegalArgumentException("Invalid handle: $handle")
                        }

                        is SetUserHandleResult.Failure.HandleExists -> {
                            logger.warn { "Handle already exists: $handle" }
                            throw IllegalStateException("Handle already exists: $handle")
                        }

                        is SetUserHandleResult.Failure.Generic -> {
                            logger.warn { "Failed to set handle: ${result.error}" }
                            throw IllegalStateException("Failed to set handle")
                        }

                        is SetUserHandleResult.Success -> {
                            logger.info { "Handle updated successfully" }
                        }
                    }
                }
                logger.info { "Profile updated successfully for: $qualifiedId" }
                ProfileStepResult.Success(Unit)
            } catch (error: Throwable) {
                logger.error(error) { "Failed to update profile for: $qualifiedId" }
                ProfileStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    private fun categoryFromThrowable(error: Throwable): ProfileFailureCategory {
        val message = error.message.orEmpty()
        return when {
            message.contains("network", ignoreCase = true) -> ProfileFailureCategory.NETWORK
            message.contains("unauthorized", ignoreCase = true) -> ProfileFailureCategory.UNAUTHORIZED
            message.contains("auth", ignoreCase = true) -> ProfileFailureCategory.UNAUTHORIZED
            message.contains("invalid", ignoreCase = true) -> ProfileFailureCategory.SERVER
            message.contains("exists", ignoreCase = true) -> ProfileFailureCategory.SERVER
            message.isNotEmpty() -> ProfileFailureCategory.SERVER
            else -> ProfileFailureCategory.UNKNOWN
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
        if (value.isNotBlank() && domain.isNotBlank()) UserId(value = value, domain = domain) else null
    } else {
        null
    }
}

private fun ProfileStepResult.Failure.toProfileFailure(): ProfileResult.Failure {
    val message =
        when (category) {
            ProfileFailureCategory.NETWORK -> "Profile fetch failed: network is unreachable. Check your connection and retry."
            ProfileFailureCategory.TIMEOUT -> "Profile fetch timed out waiting for sync. Check your connection and retry."
            ProfileFailureCategory.SERVER -> "Profile service is unavailable. Retry later or check server settings."
            ProfileFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
            ProfileFailureCategory.UNKNOWN -> "Profile fetch failed unexpectedly. Retry and check your setup."
        }

    val exitCode =
        when (category) {
            ProfileFailureCategory.NETWORK,
            ProfileFailureCategory.TIMEOUT,
            -> ExitCodes.NETWORK_ERROR
            ProfileFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            ProfileFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            ProfileFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }

    return ProfileResult.Failure(message = message, exitCode = exitCode)
}

private fun ProfileStepResult.Failure.toProfileUpdateFailure(): ProfileUpdateResult.Failure {
    val message =
        when (category) {
            ProfileFailureCategory.NETWORK -> "Profile update failed: network is unreachable. Check your connection and retry."
            ProfileFailureCategory.TIMEOUT -> "Profile update timed out waiting for sync. Check your connection and retry."
            ProfileFailureCategory.SERVER -> "Profile update service is unavailable. Retry later or check server settings."
            ProfileFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
            ProfileFailureCategory.UNKNOWN -> "Profile update failed unexpectedly. Retry and check your setup."
        }

    val exitCode =
        when (category) {
            ProfileFailureCategory.NETWORK,
            ProfileFailureCategory.TIMEOUT,
            -> ExitCodes.NETWORK_ERROR
            ProfileFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            ProfileFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            ProfileFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }

    return ProfileUpdateResult.Failure(message = message, exitCode = exitCode)
}

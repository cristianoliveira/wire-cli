package wirecli.auth

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.SsoManagedBy
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.RegisterClientParam
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.server.GetServerConfigResult
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlinx.coroutines.runBlocking

internal class RealKaliumAuthClient(
    private val runtime: RealKaliumAuthRuntime
) : AuthApiClient {
    override fun login(input: LoginInput): AuthApiResult {
        return when (val authScope = runtime.resolveAuthScope(input.server)) {
            is AuthStepResult.Success -> continueLogin(input, authScope.value)
            is AuthStepResult.Failure -> authScope.toAuthFailure(action = "Authentication")
        }
    }

    override fun logout(session: AuthSession): AuthApiResult {
        return when (val result = runtime.logout(session)) {
            is AuthStepResult.Success -> AuthApiResult.Success(session)
            is AuthStepResult.Failure -> result.toAuthFailure(action = "Logout")
        }
    }

    private fun continueLogin(input: LoginInput, authScope: KaliumAuthScope): AuthApiResult {
        return when (val login = authScope.login(input.email, input.password)) {
            is AuthStepResult.Success -> persistAuthenticatedAccount(input, login.value)
            is AuthStepResult.Failure -> login.toAuthFailure(action = "Authentication")
        }
    }

    private fun persistAuthenticatedAccount(
        input: LoginInput,
        success: AuthenticatedPrincipal
    ): AuthApiResult {
        return when (
            val persistence = runtime.addAuthenticatedAccount(
                PersistedAccount(
                    userId = success.userId,
                    server = input.server,
                    accessToken = success.accessToken,
                    refreshToken = success.refreshToken,
                    tokenType = success.tokenType,
                    cookieLabel = success.cookieLabel,
                    serverConfigId = success.serverConfigId,
                    ssoId = success.ssoId,
                    managedBy = success.managedBy,
                    proxyCredentials = success.proxyCredentials
                )
            )
        ) {
            is AuthStepResult.Success -> bootstrapSession(input, success)
            is AuthStepResult.Failure -> persistence.toAuthFailure(
                action = "Authentication",
                defaultMessage = AuthMessages.localSessionPersistenceFailed()
            )
        }
    }

    private fun bootstrapSession(input: LoginInput, success: AuthenticatedPrincipal): AuthApiResult {
        val sessionScope = when (val sessionResult = runtime.resolveSessionScope(success.userId)) {
            is AuthStepResult.Success -> sessionResult.value
            is AuthStepResult.Failure -> {
                return sessionResult.toAuthFailure(
                    action = "Authentication",
                    defaultMessage = AuthMessages.sessionBootstrapFailed()
                )
            }
        }

        return when (val clientResult = runtime.ensureClient(sessionScope, input.password)) {
            is AuthStepResult.Success -> AuthApiResult.Success(
                session = AuthSession(
                    userId = success.userId,
                    accessToken = success.accessToken,
                    server = input.server
                )
            )

            is AuthStepResult.Failure -> clientResult.toAuthFailure(
                action = "Authentication",
                defaultMessage = AuthMessages.clientRegistrationFailed()
            )
        }
    }
}

internal interface RealKaliumAuthRuntime {
    fun resolveAuthScope(server: String?): AuthStepResult<KaliumAuthScope>
    fun addAuthenticatedAccount(account: PersistedAccount): AuthStepResult<Unit>
    fun resolveSessionScope(userId: String): AuthStepResult<KaliumSessionScope>
    fun ensureClient(sessionScope: KaliumSessionScope, password: String): AuthStepResult<Unit>
    fun logout(session: AuthSession): AuthStepResult<Unit>
}

internal interface KaliumAuthScope {
    fun login(email: String, password: String): AuthStepResult<AuthenticatedPrincipal>
}

internal data class AuthenticatedPrincipal(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val cookieLabel: String?,
    val serverConfigId: String,
    val ssoId: SsoId?,
    val managedBy: SsoManagedBy?,
    val proxyCredentials: ProxyCredentials?
)

internal data class PersistedAccount(
    val userId: String,
    val server: String?,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val cookieLabel: String?,
    val serverConfigId: String,
    val ssoId: SsoId?,
    val managedBy: SsoManagedBy?,
    val proxyCredentials: ProxyCredentials?
)

internal data class KaliumSessionScope(
    val userId: String
)

internal sealed interface AuthStepResult<out T> {
    data class Success<T>(val value: T) : AuthStepResult<T>
    data class Failure(
        val category: AuthFailureCategory,
        val message: String? = null
    ) : AuthStepResult<Nothing>
}

internal enum class AuthFailureCategory {
    INVALID_CREDENTIALS,
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    UNKNOWN
}

internal class SdkKaliumAuthRuntime(
    private val environment: Map<String, String>
) : RealKaliumAuthRuntime {
    private val coreLogic: CoreLogic by lazy {
        CoreLogic(
            rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium",
            kaliumConfigs = KaliumConfigs(),
            userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}"
        )
    }

    override fun resolveAuthScope(server: String?): AuthStepResult<KaliumAuthScope> {
        return runBlocking {
            when (val links = resolveServerLinks(server)) {
                is AuthStepResult.Failure -> links
                is AuthStepResult.Success -> {
                    when (val authScope = coreLogic.versionedAuthenticationScope(links.value).invoke(null)) {
                        is AutoVersionAuthScopeUseCase.Result.Success -> AuthStepResult.Success(SdkKaliumAuthScope(authScope.authenticationScope))
                        is AutoVersionAuthScopeUseCase.Result.Failure.UnknownServerVersion -> {
                            AuthStepResult.Failure(AuthFailureCategory.SERVER)
                        }

                        is AutoVersionAuthScopeUseCase.Result.Failure.TooNewVersion -> {
                            AuthStepResult.Failure(AuthFailureCategory.SERVER)
                        }

                        is AutoVersionAuthScopeUseCase.Result.Failure.Generic -> {
                            AuthStepResult.Failure(coreFailureToCategory(authScope.genericFailure))
                        }
                    }
                }
            }
        }
    }

    override fun addAuthenticatedAccount(account: PersistedAccount): AuthStepResult<Unit> {
        return runBlocking {
            val userId = account.userId.toQualifiedIdOrNull()
                ?: return@runBlocking AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
            val authTokens = AccountTokens(
                userId = userId,
                accessToken = account.accessToken,
                refreshToken = account.refreshToken,
                tokenType = account.tokenType,
                cookieLabel = account.cookieLabel
            )

            when (
                val result = coreLogic.globalScope {
                    addAuthenticatedAccount(
                        serverConfigId = account.serverConfigId,
                        ssoId = account.ssoId,
                        authTokens = authTokens,
                        proxyCredentials = account.proxyCredentials,
                        isPersistentWebSocketEnabled = false,
                        managedBy = account.managedBy,
                        replace = true
                    )
                }
            ) {
                is com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase.Result.Success -> AuthStepResult.Success(Unit)
                com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists -> {
                    AuthStepResult.Success(Unit)
                }

                is com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase.Result.Failure.Generic -> {
                    AuthStepResult.Failure(coreFailureToCategory(result.genericFailure))
                }
            }
        }
    }

    override fun resolveSessionScope(userId: String): AuthStepResult<KaliumSessionScope> {
        return if (userId.toQualifiedIdOrNull() == null) {
            AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
        } else {
            AuthStepResult.Success(KaliumSessionScope(userId))
        }
    }

    override fun ensureClient(sessionScope: KaliumSessionScope, password: String): AuthStepResult<Unit> {
        val userId = sessionScope.userId.toQualifiedIdOrNull()
            ?: return AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                when (
                    val result = coreLogic.sessionScope(userId) {
                        client.getOrRegister(RegisterClientParam(password, emptyList()))
                    }
                ) {
                    is RegisterClientResult.Success,
                    is RegisterClientResult.E2EICertificateRequired -> AuthStepResult.Success(Unit)

                    is RegisterClientResult.Failure.InvalidCredentials,
                    RegisterClientResult.Failure.PasswordAuthRequired -> AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)

                    RegisterClientResult.Failure.TooManyClients -> AuthStepResult.Failure(AuthFailureCategory.SERVER)
                    is RegisterClientResult.Failure.Generic -> {
                        AuthStepResult.Failure(coreFailureToCategory(result.genericFailure))
                    }
                }
            } catch (error: Throwable) {
                AuthStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun logout(session: AuthSession): AuthStepResult<Unit> {
        val userId = session.userId.toQualifiedIdOrNull()
            ?: return AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                coreLogic.sessionScope(userId) {
                    logout(LogoutReason.SELF_HARD_LOGOUT, waitUntilCompletes = true)
                }
                AuthStepResult.Success(Unit)
            } catch (error: Throwable) {
                AuthStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    private suspend fun resolveServerLinks(server: String?): AuthStepResult<ServerConfig.Links> {
        val target = server?.trim().orEmpty()
        if (target.isEmpty()) {
            return AuthStepResult.Success(ServerConfig.DEFAULT)
        }

        if (target.equals("staging", ignoreCase = true)) {
            return AuthStepResult.Success(ServerConfig.STAGING)
        }

        if (target.equals("production", ignoreCase = true)) {
            return AuthStepResult.Success(ServerConfig.PRODUCTION)
        }

        return when (val result = coreLogic.globalScope { fetchServerConfigFromDeepLink(target) }) {
            is GetServerConfigResult.Success -> AuthStepResult.Success(result.serverConfigLinks)
            is GetServerConfigResult.Failure.Generic -> {
                AuthStepResult.Failure(coreFailureToCategory(result.genericFailure))
            }
        }
    }

    private fun coreFailureToCategory(failure: CoreFailure): AuthFailureCategory {
        return when (failure) {
            is NetworkFailure.NoNetworkConnection,
            is NetworkFailure.ProxyError -> AuthFailureCategory.NETWORK

            is NetworkFailure.ServerMiscommunication -> AuthFailureCategory.SERVER

            is NetworkFailure.FederatedBackendFailure,
            NetworkFailure.FeatureNotSupported,
            is NetworkFailure.MlsMessageRejectedFailure -> AuthFailureCategory.SERVER

            else -> AuthFailureCategory.UNKNOWN
        }
    }

    private fun categoryFromThrowable(error: Throwable): AuthFailureCategory {
        val message = error.message.orEmpty()
        return when {
            message.contains("network", ignoreCase = true) -> AuthFailureCategory.NETWORK
            message.contains("unauthorized", ignoreCase = true) -> AuthFailureCategory.UNAUTHORIZED
            message.contains("auth", ignoreCase = true) -> AuthFailureCategory.UNAUTHORIZED
            message.isNotEmpty() -> AuthFailureCategory.SERVER
            else -> AuthFailureCategory.UNKNOWN
        }
    }

    private class SdkKaliumAuthScope(
        private val authScope: com.wire.kalium.logic.feature.auth.AuthenticationScope
    ) : KaliumAuthScope {
        override fun login(email: String, password: String): AuthStepResult<AuthenticatedPrincipal> {
            return runBlocking {
                when (val login = authScope.login(email, password, shouldPersistClient = false)) {
                    is AuthenticationResult.Success -> {
                        AuthStepResult.Success(
                            AuthenticatedPrincipal(
                                userId = login.authData.userId.serialize(),
                                accessToken = login.authData.accessToken.value,
                                refreshToken = login.authData.refreshToken.value,
                                tokenType = login.authData.tokenType,
                                cookieLabel = login.authData.cookieLabel,
                                serverConfigId = login.serverConfigId,
                                ssoId = login.ssoID,
                                managedBy = login.managedBy,
                                proxyCredentials = login.proxyCredentials
                            )
                        )
                    }

                    AuthenticationResult.Failure.SocketError -> AuthStepResult.Failure(AuthFailureCategory.NETWORK)
                    is AuthenticationResult.Failure.InvalidCredentials,
                    AuthenticationResult.Failure.InvalidUserIdentifier -> {
                        AuthStepResult.Failure(AuthFailureCategory.INVALID_CREDENTIALS)
                    }

                    AuthenticationResult.Failure.AccountPendingActivation,
                    AuthenticationResult.Failure.AccountSuspended -> {
                        AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
                    }

                    is AuthenticationResult.Failure.Generic -> {
                        when (login.genericFailure) {
                            is NetworkFailure.NoNetworkConnection,
                            is NetworkFailure.ProxyError -> AuthStepResult.Failure(AuthFailureCategory.NETWORK)

                            is NetworkFailure.ServerMiscommunication,
                            is NetworkFailure.FederatedBackendFailure,
                            NetworkFailure.FeatureNotSupported,
                            is NetworkFailure.MlsMessageRejectedFailure -> AuthStepResult.Failure(AuthFailureCategory.SERVER)

                            else -> AuthStepResult.Failure(AuthFailureCategory.UNKNOWN)
                        }
                    }
                }
            }
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
    if (atIndex <= 0 || atIndex == trimmed.lastIndex) return null
    val value = trimmed.substring(0, atIndex)
    val domain = trimmed.substring(atIndex + 1)
    if (value.isBlank() || domain.isBlank()) return null
    return UserId(value = value, domain = domain)
}

private fun UserId.serialize(): String = "$value@$domain"

private fun AuthStepResult.Failure.toAuthFailure(
    action: String,
    defaultMessage: String? = null
): AuthApiResult.Failure {
    val resolvedMessage = message ?: defaultMessage ?: when (category) {
        AuthFailureCategory.INVALID_CREDENTIALS -> AuthMessages.invalidCredentials()
        AuthFailureCategory.NETWORK -> AuthMessages.networkFailure(action)
        AuthFailureCategory.SERVER -> AuthMessages.authServiceUnavailable()
        AuthFailureCategory.UNAUTHORIZED -> AuthMessages.unauthorizedAction(action)
        AuthFailureCategory.UNKNOWN -> "Unexpected authentication error. Please retry."
    }

    val exitCode = when (category) {
        AuthFailureCategory.INVALID_CREDENTIALS -> ExitCodes.AUTH_FAILED
        AuthFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
        AuthFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
        AuthFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
        AuthFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
    }

    return AuthApiResult.Failure(
        message = resolvedMessage,
        exitCode = exitCode
    )
}

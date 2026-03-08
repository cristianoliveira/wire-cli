package wirecli.auth

internal class RealAuthApiClient(
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
                    accessToken = success.accessToken
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
    val accessToken: String
)

internal data class PersistedAccount(
    val userId: String,
    val server: String?,
    val accessToken: String
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

internal class EnvironmentKaliumAuthRuntime(
    private val environment: Map<String, String>
) : RealKaliumAuthRuntime {
    private val mode = environment[ENV_REAL_MODE]?.trim().orEmpty()

    override fun resolveAuthScope(server: String?): AuthStepResult<KaliumAuthScope> {
        val failure = failureForStep("scope")
        if (failure != null) return failure

        return AuthStepResult.Success(EnvironmentKaliumAuthScope(mode, server))
    }

    override fun addAuthenticatedAccount(account: PersistedAccount): AuthStepResult<Unit> {
        return failureForStep("persist") ?: AuthStepResult.Success(Unit)
    }

    override fun resolveSessionScope(userId: String): AuthStepResult<KaliumSessionScope> {
        val failure = failureForStep("session")
        if (failure != null) return failure

        return AuthStepResult.Success(KaliumSessionScope(userId))
    }

    override fun ensureClient(sessionScope: KaliumSessionScope, password: String): AuthStepResult<Unit> {
        return failureForStep("client") ?: AuthStepResult.Success(Unit)
    }

    override fun logout(session: AuthSession): AuthStepResult<Unit> {
        return failureForStep("logout") ?: AuthStepResult.Success(Unit)
    }

    private fun failureForStep(step: String): AuthStepResult.Failure? {
        val explicit = environment[ENV_REAL_FAIL_STEP]?.trim()?.lowercase()
        if (explicit == step) {
            return categoryForMode(mode)
        }

        if (mode == "login_$step") {
            return AuthStepResult.Failure(AuthFailureCategory.SERVER)
        }

        return when (mode) {
            "invalid", "invalid_credentials" -> AuthStepResult.Failure(AuthFailureCategory.INVALID_CREDENTIALS)
            "network", "network_error" -> AuthStepResult.Failure(AuthFailureCategory.NETWORK)
            "server", "server_error" -> AuthStepResult.Failure(AuthFailureCategory.SERVER)
            "unauthorized" -> AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
            "unknown" -> AuthStepResult.Failure(AuthFailureCategory.UNKNOWN)
            else -> null
        }
    }

    private fun categoryForMode(rawMode: String): AuthStepResult.Failure {
        return when (rawMode) {
            "invalid", "invalid_credentials" -> AuthStepResult.Failure(AuthFailureCategory.INVALID_CREDENTIALS)
            "network", "network_error" -> AuthStepResult.Failure(AuthFailureCategory.NETWORK)
            "unauthorized" -> AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
            "unknown" -> AuthStepResult.Failure(AuthFailureCategory.UNKNOWN)
            else -> AuthStepResult.Failure(AuthFailureCategory.SERVER)
        }
    }

    private class EnvironmentKaliumAuthScope(
        private val mode: String,
        private val server: String?
    ) : KaliumAuthScope {
        override fun login(email: String, password: String): AuthStepResult<AuthenticatedPrincipal> {
            val normalizedEmail = email.trim()
            if (normalizedEmail.isEmpty() || password.isEmpty()) {
                return AuthStepResult.Failure(AuthFailureCategory.INVALID_CREDENTIALS)
            }

            return if (mode == "success" || mode == "" || mode == "login_ok") {
                AuthStepResult.Success(
                    AuthenticatedPrincipal(
                        userId = normalizedEmail.substringBefore('@').ifEmpty { "user" },
                        accessToken = "kalium-token-${server ?: "default"}"
                    )
                )
            } else {
                when (mode) {
                    "invalid", "invalid_credentials" -> AuthStepResult.Failure(AuthFailureCategory.INVALID_CREDENTIALS)
                    "network", "network_error" -> AuthStepResult.Failure(AuthFailureCategory.NETWORK)
                    "unauthorized" -> AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
                    "unknown" -> AuthStepResult.Failure(AuthFailureCategory.UNKNOWN)
                    else -> AuthStepResult.Failure(AuthFailureCategory.SERVER)
                }
            }
        }
    }

    companion object {
        const val ENV_REAL_MODE = "WIRE_REAL_MODE"
        const val ENV_REAL_FAIL_STEP = "WIRE_REAL_FAIL_STEP"
    }
}

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

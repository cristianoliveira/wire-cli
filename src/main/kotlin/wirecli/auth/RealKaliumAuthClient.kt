package wirecli.auth

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.StoreSessionParam
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.SsoManagedBy
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.RegisterClientParam
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.server.GetServerConfigResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthResult
import wirecli.auth.AuthSession
import wirecli.shared.AuthError
import wirecli.shared.Result

private val logger = KotlinLogging.logger {}

/**
 * Real Kalium-backed implementation of the authentication client.
 *
 * This class delegates authentication operations to the Kalium SDK through the [RealKaliumAuthRuntime]
 * interface, handling login and logout flows with proper error mapping.
 *
 * @invariant runtime is never null and properly initialized
 * @invariant All public methods return non-null AuthApiResult
 */
internal class RealKaliumAuthClient(
    private val runtime: RealKaliumAuthRuntime,
) : AuthApiClient {
    /**
     * Authenticates a user with email and password against a Wire backend.
     *
     * @param input Login credentials and server configuration
     * @return Result.Success with authenticated session if successful; Result.Failure with error details otherwise
     * @throws Nothing - All errors are wrapped in AuthApiResult
     *
     * @pre input.email must be non-null and non-empty
     * @pre input.password must be non-null and non-empty
     * @post result is either Success with valid AuthSession or Failure with appropriate error code
     * @post If Success, returned session has non-null userId and accessToken
     *
     * @see Result.Success
     * @see Result.Failure
     */
    override fun login(input: LoginInput): AuthApiResult<AuthSession> {
        require(input.email.isNotBlank()) { "Login email must not be blank." }
        require(input.password.isNotBlank()) { "Login password must not be blank." }

        val result =
            when (val authScope = runtime.resolveAuthScope(input.server)) {
                is AuthStepResult.Success -> continueLogin(input, authScope.value)
                is AuthStepResult.Failure -> authScope.toAuthFailure(action = "Authentication")
            }

        if (result is Result.Success) {
            check(result.value.userId.isNotBlank()) {
                "Authentication success must include a non-blank user ID."
            }
            check(result.value.accessToken.isNotBlank()) {
                "Authentication success must include a non-blank access token."
            }
        }
        return result
    }

    /**
     * Logs out the currently authenticated user, invalidating their session.
     *
     * @param session The authenticated session to logout (must be valid and active)
     * @return Result.Success if logout completed successfully; Result.Failure with error details otherwise
     * @throws Nothing - All errors are wrapped in AuthApiResult
     *
     * @pre session must be non-null with valid userId and accessToken
     * @pre session must represent an active authenticated state
     * @post result is either Success (even if logout already happened) or Failure with error details
     * @post If Success, the session tokens are invalidated on the server
     *
     * @see Result.Success
     * @see Result.Failure
     */
    override fun logout(session: AuthSession): AuthApiResult<String> {
        require(session.userId.isNotBlank()) { "Logout session user ID must not be blank." }
        require(session.accessToken.isNotBlank()) { "Logout session access token must not be blank." }

        return when (val logoutResult = runtime.logout(session)) {
            is AuthStepResult.Success -> Result.Success(value = session.userId)
            is AuthStepResult.Failure -> logoutResult.toAuthFailure(action = "Logout")
        }
    }

    /**
     * Continues login flow after authentication scope is established.
     *
     * @param input The login credentials and server configuration
     * @param authScope The resolved authentication scope from Kalium
     * @return AuthApiResult with success or failure details
     *
     * @pre input must have non-null email and password
     * @pre authScope must be properly initialized and functional
     * @post Returns either Result.Success or Result.Failure
     */
    private fun continueLogin(
        input: LoginInput,
        authScope: KaliumAuthScope,
    ): AuthApiResult<AuthSession> {
        return when (val login = authScope.login(input.email, input.password)) {
            is AuthStepResult.Success -> persistAuthenticatedAccount(input, login.value)
            is AuthStepResult.Failure -> login.toAuthFailure(action = "Authentication")
        }
    }

    /**
     * Persists the authenticated principal to local storage for future session resumption.
     *
     * @param input The original login input with server configuration
     * @param success The authenticated principal from the server
     * @return AuthApiResult with success or failure details
     * @throws Nothing - All errors wrapped in AuthApiResult
     *
     * @pre success must have valid userId, tokens, and server config
     * @post If successful, account is persisted and session bootstrap begins
     * @post Account can be recovered from local storage on subsequent CLI invocations
     */
    private fun persistAuthenticatedAccount(
        input: LoginInput,
        success: AuthenticatedPrincipal,
    ): AuthApiResult<AuthSession> {
        return when (
            val persistence =
                runtime.addAuthenticatedAccount(
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
                        proxyCredentials = success.proxyCredentials,
                    ),
                )
        ) {
            is AuthStepResult.Success -> bootstrapSession(input, success)
            is AuthStepResult.Failure ->
                persistence.toAuthFailure(
                    action = "Authentication",
                    defaultMessage = AuthMessages.localSessionPersistenceFailed(),
                )
        }
    }

    /**
     * Bootstraps a session by registering a client device and initializing session state.
     *
     * @param input The original login input containing password for client registration
     * @param success The authenticated principal with tokens
     * @return AuthApiResult with complete authenticated session or failure details
     * @throws Nothing - All errors wrapped in AuthApiResult
     *
     * @pre success must have valid userId and accessToken
     * @pre input.password must be non-null and match user's credentials
     * @post If successful, client device is registered and AuthSession is returned
     * @post Returned AuthSession contains non-null userId, accessToken, and server
     */
    private fun bootstrapSession(
        input: LoginInput,
        success: AuthenticatedPrincipal,
    ): AuthApiResult<AuthSession> {
        val sessionScope =
            when (val sessionResult = runtime.resolveSessionScope(success.userId)) {
                is AuthStepResult.Success -> sessionResult.value
                is AuthStepResult.Failure -> {
                    return sessionResult.toAuthFailure(
                        action = "Authentication",
                        defaultMessage = AuthMessages.sessionBootstrapFailed(),
                    )
                }
            }

        return when (val clientResult = runtime.ensureClient(sessionScope, input.password)) {
            is AuthStepResult.Success ->
                Result.Success(
                    session =
                        AuthSession(
                            userId = success.userId,
                            accessToken = success.accessToken,
                            server = input.server,
                        ),
                )

            is AuthStepResult.Failure ->
                clientResult.toAuthFailure(
                    action = "Authentication",
                    defaultMessage =
                        if (clientResult.category == AuthFailureCategory.PASSWORD_REQUIRED) {
                            null // Let PASSWORD_REQUIRED use its own message
                        } else {
                            AuthMessages.clientRegistrationFailed()
                        },
                )
        }
    }
}

/**
 * Contract for Kalium SDK authentication runtime operations.
 *
 * This interface abstracts Kalium SDK interactions for authentication flows,
 * enabling testability and separation between CLI and SDK concerns.
 *
 * @invariant All methods return non-null AuthStepResult
 * @invariant Shutdown must be called to cleanup resources
 */
internal interface RealKaliumAuthRuntime {
    /**
     * Resolves the authentication scope for the given server.
     *
     * @param server Optional server URL/domain (null uses default server)
     * @return AuthStepResult with KaliumAuthScope for login operations or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre server must be null or a valid URL/domain string
     * @post Result is either Success with functional KaliumAuthScope or Failure
     */
    fun resolveAuthScope(server: String?): AuthStepResult<KaliumAuthScope>

    /**
     * Persists an authenticated account to local Kalium storage.
     *
     * @param account The account details with tokens and configuration to persist
     * @return AuthStepResult with Unit on success or Failure with error details
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre account must have valid userId, tokens, and serverConfigId
     * @post If successful, account can be recovered by subsequent CLI invocations
     */
    fun addAuthenticatedAccount(account: PersistedAccount): AuthStepResult<Unit>

    /**
     * Resolves the session scope for a specific user.
     *
     * @param userId The qualified user ID (format: value@domain)
     * @return AuthStepResult with KaliumSessionScope or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre userId must be non-null and in qualified format (value@domain)
     * @post Result is either Success with functional session scope or Failure
     */
    fun resolveSessionScope(userId: String): AuthStepResult<KaliumSessionScope>

    /**
     * Registers or retrieves a client device for the authenticated session.
     *
     * @param sessionScope The session context for the authenticated user
     * @param password The user's password for client registration
     * @return AuthStepResult with Unit on success or Failure with error details
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre sessionScope must represent a valid authenticated user
     * @pre password must match the user's credentials
     * @post If successful, a device is registered and ready for use
     */
    fun ensureClient(
        sessionScope: KaliumSessionScope,
        password: String,
    ): AuthStepResult<Unit>

    /**
     * Logs out the authenticated user and invalidates session state.
     *
     * @param session The session to logout
     * @return AuthStepResult with Unit on success or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre session must represent a valid authenticated user
     * @post If successful, session tokens are invalidated server-side
     */
    fun logout(session: AuthSession): AuthStepResult<Unit>

    /**
     * Closes the runtime and releases all resources.
     * Delegates to shutdown() for implementation.
     */
    fun close() {
        shutdown()
    }

    /**
     * Shuts down the Kalium runtime, cleaning up resources and active sessions.
     */
    fun shutdown()
}

/**
 * Contract for Kalium authentication scope operations.
 *
 * This interface represents an established authentication context with the Kalium SDK,
 * allowing login operations without re-establishing connection to the server.
 *
 * @invariant Represents a single authentication attempt/scope
 */
internal interface KaliumAuthScope {
    /**
     * Performs email/password login within this authentication scope.
     *
     * @param email User email address (non-null, valid format)
     * @param password User password (non-null, ≥8 chars recommended)
     * @return AuthStepResult with AuthenticatedPrincipal on success or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre email must be non-null and non-empty
     * @pre password must be non-null and non-empty
     * @post Result is either Success with valid tokens or Failure with error details
     */
    fun login(
        email: String,
        password: String,
    ): AuthStepResult<AuthenticatedPrincipal>
}

/**
 * Principal returned after successful authentication against the Wire server.
 *
 * Contains all tokens and metadata required to establish a user session and
 * register a client device.
 *
 * @invariant userId is in qualified format (value@domain)
 * @invariant accessToken and refreshToken are non-empty and valid JWT
 * @invariant serverConfigId matches a valid Kalium server configuration
 */
internal data class AuthenticatedPrincipal(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val cookieLabel: String?,
    val serverConfigId: String,
    val ssoId: SsoId?,
    val managedBy: SsoManagedBy?,
    val proxyCredentials: ProxyCredentials?,
)

/**
 * Account information persisted to local storage for session resumption.
 *
 * All fields from the authenticated principal plus server configuration,
 * allowing the CLI to resume sessions without re-authenticating.
 *
 * @invariant userId is in qualified format (value@domain)
 * @invariant All token fields are non-empty strings
 */
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
    val proxyCredentials: ProxyCredentials?,
)

/**
 * Scoped context for session operations on a specific authenticated user.
 *
 * @invariant userId is in qualified format (value@domain)
 */
internal data class KaliumSessionScope(
    val userId: String,
)

/**
 * Result type for authentication step operations (sealed interface).
 *
 * Represents either a successful step result with typed value or a failure
 * with category and optional diagnostic message.
 *
 * @invariant Each operation returns exactly one sealed subtype
 */
internal sealed interface AuthStepResult<out T> {
    /**
     * Successful step result with associated value.
     *
     * @param value The result value from the successful step
     */
    data class Success<T>(val value: T) : AuthStepResult<T>

    /**
     * Failed step result with error details.
     *
     * @param category The category of failure (determines error message and exit code)
     * @param message Optional diagnostic message providing additional context
     */
    data class Failure(
        val category: AuthFailureCategory,
        val message: String? = null,
    ) : AuthStepResult<Nothing>
}

/**
 * Enumeration of possible authentication failure categories.
 *
 * Each category maps to specific user-facing messages and exit codes.
 * INVALID_CREDENTIALS: Email/password mismatch
 * PASSWORD_REQUIRED: Device registration requires password re-entry
 * NETWORK: Network connectivity issues
 * SERVER: Server errors (5xx, unavailable)
 * UNAUTHORIZED: Account suspended, pending activation, or other auth failures
 * UNKNOWN: Unexpected/unclassifiable errors
 */
internal enum class AuthFailureCategory {
    INVALID_CREDENTIALS,
    PASSWORD_REQUIRED,
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    NOMAD_SINGLE_USER_VIOLATION,
    UNKNOWN,
}

/**
 * Kalium SDK-based implementation of the authentication runtime.
 *
 * This class manages CoreLogic initialization and delegates authentication
 * operations to the Kalium SDK, handling all Kalium-specific error mappings
 * and session lifecycle management.
 *
 * @invariant coreLogic is lazily initialized and properly shut down
 * @invariant activeSessionUserIds tracks all open sessions for cleanup
 * @invariant All methods return non-null AuthStepResult
 */
internal class SdkKaliumAuthRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : RealKaliumAuthRuntime {
    private val activeSessionUserIds = mutableSetOf<UserId>()

    init {
        logger.debug { "SdkKaliumAuthRuntime initialized with CLI mode: $cliMode" }
    }

    private val coreLogicLazy =
        lazy {
            logger.debug { "Initializing Kalium CoreLogic for auth runtime" }
            val rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium"
            logger.debug { "Kalium data path: $rootPath" }
            val configs = kaliumCliConfigs(cliMode)
            logger.debug { "Kalium configs loaded for mode: $cliMode" }
            CoreLogic(
                rootPath = rootPath,
                kaliumConfigs = configs,
                userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
            )
        }
    private val coreLogic: CoreLogic by coreLogicLazy

    /**
     * Resolves the authentication scope for the specified server.
     *
     * @param server Optional server domain or URL (null uses default production server)
     * @return AuthStepResult with SdkKaliumAuthScope on success or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre server must be null, empty, "staging", "production", or valid URL
     * @post Result is either Success with functional auth scope or Failure
     * @post On Success, scope can perform login operations without re-connecting
     */
    override fun resolveAuthScope(server: String?): AuthStepResult<KaliumAuthScope> {
        require(server == null || server.trim().isNotEmpty()) {
            "Server value must be null or a non-blank identifier."
        }
        logger.info { "SdkKaliumAuthRuntime: Resolving auth scope for server: ${server ?: "default"}" }
        val result =
            runBlocking {
                logger.debug { "Resolving server configuration links" }
                when (val links = resolveServerLinks(server)) {
                    is AuthStepResult.Failure -> {
                        logger.warn { "Failed to resolve server links: ${links.category}" }
                        links
                    }
                    is AuthStepResult.Success -> {
                        logger.debug { "Server links resolved successfully - creating auth scope" }
                        when (val authScope = coreLogic.versionedAuthenticationScope(links.value).invoke(null)) {
                            is AutoVersionAuthScopeUseCase.Result.Success -> {
                                logger.debug { "Auth scope created successfully" }
                                AuthStepResult.Success(
                                    SdkKaliumAuthScope(authScope.authenticationScope),
                                )
                            }
                            is AutoVersionAuthScopeUseCase.Result.Failure.UnknownServerVersion -> {
                                logger.error { "Unknown server version - auth scope creation failed" }
                                AuthStepResult.Failure(AuthFailureCategory.SERVER)
                            }

                            is AutoVersionAuthScopeUseCase.Result.Failure.TooNewVersion -> {
                                logger.error { "Server version too new - auth scope creation failed" }
                                AuthStepResult.Failure(AuthFailureCategory.SERVER)
                            }

                            is AutoVersionAuthScopeUseCase.Result.Failure.Generic -> {
                                logger.error { "Generic error creating auth scope: ${authScope.genericFailure}" }
                                AuthStepResult.Failure(coreFailureToCategory(authScope.genericFailure))
                            }
                        }
                    }
                }
            }

        when (result) {
            is AuthStepResult.Success -> Unit

            is AuthStepResult.Failure -> {
                check(result.category in AuthFailureCategory.entries) {
                    "Auth scope resolution failure must map to a known AuthFailureCategory."
                }
            }
        }
        return result
    }

    /**
     * Adds an authenticated account to the Kalium persistence storage.
     *
     * @param account The account details to persist
     * @return AuthStepResult with Unit on success or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre account.userId must be in qualified format (value@domain)
     * @pre account.accessToken and refreshToken must be non-empty valid JWTs
     * @pre account.serverConfigId must match a valid server configuration
     * @post If successful, account is persisted and can be retrieved by CLI
     * @post Subsequent addAuthenticatedAccount calls with same userId replace previous account
     */
    override fun addAuthenticatedAccount(account: PersistedAccount): AuthStepResult<Unit> {
        require(account.userId.isNotBlank()) { "Persisted account user ID must not be blank." }
        require(account.accessToken.isNotBlank()) { "Persisted account access token must not be blank." }
        require(account.refreshToken.isNotBlank()) { "Persisted account refresh token must not be blank." }
        require(account.serverConfigId.isNotBlank()) { "Persisted account server config ID must not be blank." }

        logger.info { "SdkKaliumAuthRuntime: Adding authenticated account for user: ${account.userId}" }
        val result =
            runBlocking {
                val userId =
                    account.userId.toQualifiedIdOrNull()
                        ?: run {
                            logger.warn { "Invalid user ID format for persisting account: ${account.userId}" }
                            return@runBlocking AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
                        }
                logger.debug { "User ID qualified: $userId" }

                val authTokens =
                    AccountTokens(
                        userId = userId,
                        accessToken = account.accessToken,
                        refreshToken = account.refreshToken,
                        tokenType = account.tokenType,
                        cookieLabel = account.cookieLabel,
                    )

                logger.debug { "Persisting authenticated account to storage" }
                when (
                    val result =
                        coreLogic.globalScope {
                            addAuthenticatedAccount(
                                session =
                                    StoreSessionParam(
                                        serverConfigId = account.serverConfigId,
                                        ssoId = account.ssoId,
                                        accountTokens = authTokens,
                                        proxyCredentials = account.proxyCredentials,
                                        isPersistentWebSocketEnabled = false,
                                        managedBy = account.managedBy,
                                    ),
                                replace = true,
                            )
                        }
                ) {
                    is com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase.Result.Success -> {
                        logger.info { "Account persisted successfully for user: $userId" }
                        AuthStepResult.Success(Unit)
                    }
                    com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase.Result.Failure.UserAlreadyExists -> {
                        logger.debug { "Account already exists for user: $userId - replacing" }
                        AuthStepResult.Success(Unit)
                    }
                    com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase.Result.Failure.NomadSingleUserViolation -> {
                        logger.error { "Nomad single user violation for user $userId" }
                        AuthStepResult.Failure(AuthFailureCategory.NOMAD_SINGLE_USER_VIOLATION)
                    }
                    is com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase.Result.Failure.Generic -> {
                        logger.error { "Failed to persist account for user $userId: ${result.genericFailure}" }
                        AuthStepResult.Failure(coreFailureToCategory(result.genericFailure))
                    }
                }
            }

        when (result) {
            is AuthStepResult.Success -> {
                check(account.userId.toQualifiedIdOrNull() != null) {
                    "Persist account success requires a qualified user ID."
                }
            }

            is AuthStepResult.Failure -> {
                check(result.category in AuthFailureCategory.entries) {
                    "Persist account failure must map to a known AuthFailureCategory."
                }
            }
        }
        return result
    }

    /**
     * Resolves a session scope for the specified user ID.
     *
     * This is a lightweight operation that validates the user ID format and
     * creates a session context without connecting to the server.
     *
     * @param userId The qualified user ID (format: value@domain)
     * @return AuthStepResult with KaliumSessionScope on success or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre userId must be in qualified format (value@domain)
     * @post Result is either Success with functional session scope or Failure
     */
    override fun resolveSessionScope(userId: String): AuthStepResult<KaliumSessionScope> {
        require(userId.isNotBlank()) { "Session scope user ID must not be blank." }

        val result =
            if (userId.toQualifiedIdOrNull() == null) {
                AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
            } else {
                AuthStepResult.Success(KaliumSessionScope(userId))
            }

        when (result) {
            is AuthStepResult.Success -> {
                check(result.value.userId == userId) {
                    "Resolved session scope must preserve the provided user ID."
                }
            }

            is AuthStepResult.Failure -> {
                check(result.category == AuthFailureCategory.UNAUTHORIZED) {
                    "Invalid session scope user IDs must map to UNAUTHORIZED."
                }
            }
        }
        return result
    }

    /**
     * Ensures a client device is registered for the session.
     *
     * @param sessionScope The session context for the authenticated user
     * @param password The user's password for device registration
     * @return AuthStepResult with Unit on success or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre sessionScope.userId must be valid and authenticated
     * @pre password must match the user's credentials
     * @post If successful, a device is registered and ready for API calls
     * @post If PASSWORD_REQUIRED, user must re-enter password and retry
     * @post activeSessionUserIds is updated to track this session for cleanup
     */
    override fun ensureClient(
        sessionScope: KaliumSessionScope,
        password: String,
    ): AuthStepResult<Unit> {
        require(sessionScope.userId.isNotBlank()) { "Session scope user ID must not be blank." }
        require(password.isNotBlank()) { "Client registration password must not be blank." }

        val userId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += userId

        val result =
            runBlocking {
                try {
                    when (
                        val result =
                            coreLogic.sessionScope(userId) {
                                client.getOrRegister(RegisterClientParam(password, emptyList()))
                            }
                    ) {
                        is RegisterClientResult.Success,
                        is RegisterClientResult.E2EICertificateRequired,
                        -> AuthStepResult.Success(Unit)

                        is RegisterClientResult.Failure.InvalidCredentials ->
                            AuthStepResult.Failure(AuthFailureCategory.INVALID_CREDENTIALS)

                        RegisterClientResult.Failure.PasswordAuthRequired ->
                            AuthStepResult.Failure(AuthFailureCategory.PASSWORD_REQUIRED)

                        RegisterClientResult.Failure.TooManyClients -> AuthStepResult.Failure(AuthFailureCategory.SERVER)
                        is RegisterClientResult.Failure.Generic -> {
                            AuthStepResult.Failure(coreFailureToCategory(result.genericFailure))
                        }
                    }
                } catch (error: Throwable) {
                    AuthStepResult.Failure(categoryFromThrowable(error))
                }
            }

        check(activeSessionUserIds.contains(userId)) {
            "Ensuring a client must register the session user for runtime cleanup."
        }
        if (result is AuthStepResult.Failure) {
            check(result.category in AuthFailureCategory.entries) {
                "Client registration failure must map to a known AuthFailureCategory."
            }
        }
        return result
    }

    /**
     * Logs out the authenticated user, invalidating their session.
     *
     * @param session The session to logout
     * @return AuthStepResult with Unit on success or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre session.userId must be in qualified format and represent active user
     * @post If successful, user is logged out server-side and tokens invalidated
     * @post activeSessionUserIds is updated to track this session for cleanup
     */
    override fun logout(session: AuthSession): AuthStepResult<Unit> {
        require(session.userId.isNotBlank()) { "Logout user ID must not be blank." }
        require(session.accessToken.isNotBlank()) { "Logout access token must not be blank." }

        logger.info { "SdkKaliumAuthRuntime: Logging out user: ${session.userId}" }
        val userId =
            session.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "Invalid user ID format for logout: ${session.userId}" }
                    return AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
                }
        activeSessionUserIds += userId
        logger.debug { "User ID qualified: $userId" }

        val result =
            runBlocking {
                try {
                    logger.debug { "Initiating logout for user: $userId" }
                    coreLogic.sessionScope(userId) {
                        logout(LogoutReason.SELF_HARD_LOGOUT, waitUntilCompletes = true)
                    }
                    logger.info { "Logout completed successfully for user: $userId" }
                    AuthStepResult.Success(Unit)
                } catch (error: Throwable) {
                    logger.error(error) { "Failed to logout user: $userId" }
                    AuthStepResult.Failure(categoryFromThrowable(error))
                }
            }

        check(activeSessionUserIds.contains(userId)) {
            "Logout must track the user session for runtime cleanup."
        }
        if (result is AuthStepResult.Failure) {
            check(result.category in AuthFailureCategory.entries) {
                "Logout failure must map to a known AuthFailureCategory."
            }
        }
        return result
    }

    /**
     * Shuts down the Kalium runtime and releases all resources.
     *
     * Cancels all active session scopes and the global scope, ensuring proper
     * cleanup of network connections and file handles.
     *
     * @throws Nothing - Silently handles errors during cleanup
     *
     * @post All active sessions are cancelled
     * @post CoreLogic is cleaned up and resources released
     * @post Safe to call multiple times (checks lazy initialization)
     */
    override fun shutdown() {
        logger.debug { "SdkKaliumAuthRuntime: Shutting down auth runtime" }
        if (!coreLogicLazy.isInitialized()) {
            logger.debug { "CoreLogic not initialized - nothing to shutdown" }
            return
        }

        logger.debug { "Cancelling ${activeSessionUserIds.size} active session scopes" }
        runBlocking {
            activeSessionUserIds.forEach { userId ->
                logger.debug { "Cancelling session scope for user: $userId" }
                coreLogic.sessionScope(userId) { cancel() }
            }
        }
        activeSessionUserIds.clear()
        check(activeSessionUserIds.isEmpty()) {
            "Auth runtime shutdown must clear tracked active sessions."
        }
        logger.debug { "Cancelling global scope" }
        coreLogic.getGlobalScope().cancel()
        check(coreLogicLazy.isInitialized()) {
            "Auth runtime shutdown expects CoreLogic to be initialized when cancelling scopes."
        }
        logger.info { "Auth runtime shutdown complete" }
    }

    /**
     * Resolves server configuration links from the server identifier.
     *
     * @param server Optional server identifier (null/empty=default, "staging", "production", or URL)
     * @return AuthStepResult with ServerConfig.Links or Failure
     * @throws Nothing - All errors wrapped in AuthStepResult
     *
     * @pre server must be null, empty, "staging", "production", or valid deeplink URL
     * @post Result contains server configuration for connection setup
     */
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

    /**
     * Maps Kalium CoreFailure to authentication failure category.
     *
     * @param failure The CoreFailure from Kalium SDK
     * @return AuthFailureCategory appropriate for the failure type
     *
     * @post Returns a non-null AuthFailureCategory
     */
    private fun coreFailureToCategory(failure: CoreFailure): AuthFailureCategory {
        return when (failure) {
            is NetworkFailure.NoNetworkConnection,
            is NetworkFailure.ProxyError,
            -> AuthFailureCategory.NETWORK

            is NetworkFailure.ServerMiscommunication -> AuthFailureCategory.SERVER

            is NetworkFailure.FederatedBackendFailure,
            NetworkFailure.FeatureNotSupported,
            is NetworkFailure.MlsMessageRejectedFailure,
            -> AuthFailureCategory.SERVER

            else -> AuthFailureCategory.UNKNOWN
        }
    }

    /**
     * Maps a Throwable exception to an authentication failure category.
     *
     * Uses heuristic analysis of exception message to categorize error.
     *
     * @param error The Throwable to categorize
     * @return AuthFailureCategory based on error message content
     *
     * @post Returns a non-null AuthFailureCategory
     */
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

    /**
     * Kalium SDK-based implementation of KaliumAuthScope.
     *
     * Wraps the Kalium SDK's AuthenticationScope to provide login operations
     * within a single authentication context.
     */
    private class SdkKaliumAuthScope(
        private val authScope: com.wire.kalium.logic.feature.auth.AuthenticationScope,
    ) : KaliumAuthScope {
        /**
         * Performs email/password authentication against the Wire server.
         *
         * @param email User email address
         * @param password User password
         * @return AuthStepResult with AuthenticatedPrincipal on success or Failure
         * @throws Nothing - All errors wrapped in AuthStepResult
         *
         * @pre email and password must be non-null and non-empty
         * @post Result contains auth tokens and user ID on success
         */
        override fun login(
            email: String,
            password: String,
        ): AuthStepResult<AuthenticatedPrincipal> {
            require(email.isNotBlank()) { "Authentication email must not be blank." }
            require(password.isNotBlank()) { "Authentication password must not be blank." }

            val result =
                runBlocking {
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
                                    proxyCredentials = login.proxyCredentials,
                                ),
                            )
                        }

                        AuthenticationResult.Failure.SocketError -> AuthStepResult.Failure(AuthFailureCategory.NETWORK)
                        is AuthenticationResult.Failure.InvalidCredentials,
                        AuthenticationResult.Failure.InvalidUserIdentifier,
                        -> {
                            AuthStepResult.Failure(AuthFailureCategory.INVALID_CREDENTIALS)
                        }

                        AuthenticationResult.Failure.AccountPendingActivation,
                        AuthenticationResult.Failure.AccountSuspended,
                        -> {
                            AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
                        }

                        is AuthenticationResult.Failure.Generic -> {
                            when (login.genericFailure) {
                                is NetworkFailure.NoNetworkConnection,
                                is NetworkFailure.ProxyError,
                                -> AuthStepResult.Failure(AuthFailureCategory.NETWORK)

                                is NetworkFailure.ServerMiscommunication,
                                is NetworkFailure.FederatedBackendFailure,
                                NetworkFailure.FeatureNotSupported,
                                is NetworkFailure.MlsMessageRejectedFailure,
                                -> AuthStepResult.Failure(AuthFailureCategory.SERVER)

                                else -> AuthStepResult.Failure(AuthFailureCategory.UNKNOWN)
                            }
                        }
                    }
                }

            if (result is AuthStepResult.Success) {
                check(result.value.userId.isNotBlank()) {
                    "Authentication success must include a non-blank user ID."
                }
                check(result.value.accessToken.isNotBlank()) {
                    "Authentication success must include a non-blank access token."
                }
            }
            return result
        }
    }

    /**
     * Resolves the home directory from environment or system properties.
     *
     * @param env The environment map to check for HOME variable
     * @return The home directory path
     *
     * @post Returns non-null valid directory path
     */
    private fun resolveHomeDirectory(env: Map<String, String>): String {
        val home = env["HOME"]?.trim()
        if (!home.isNullOrEmpty()) return home
        return System.getProperty("user.home")
    }
}

/**
 * Parses a qualified user ID string into a UserId or returns null if invalid.
 *
 * Expected format: "value@domain" where both value and domain are non-blank.
 *
 * @receiver The string to parse
 * @return UserId if valid qualified format, null otherwise
 *
 * @pre receiver must be non-null
 * @post If successful, result.value and result.domain are both non-blank
 */
private fun String.toQualifiedIdOrNull(): UserId? {
    val trimmed = trim()
    val atIndex = trimmed.lastIndexOf('@')
    if (atIndex <= 0 || atIndex == trimmed.lastIndex) return null
    val value = trimmed.substring(0, atIndex)
    val domain = trimmed.substring(atIndex + 1)
    if (value.isBlank() || domain.isBlank()) return null
    return UserId(value = value, domain = domain)
}

/**
 * Serializes a UserId into qualified string format.
 *
 * @receiver The UserId to serialize
 * @return Qualified string in format "value@domain"
 *
 * @post Returns non-null non-empty string in "value@domain" format
 */
private fun UserId.serialize(): String = "$value@$domain"

/**
 * Converts an AuthStepResult.Failure to an Result.Failure with appropriate messaging.
 *
 * Maps failure categories to user-facing messages and exit codes.
 *
 * @receiver The failure result to convert
 * @param action The action being performed (used in error messages)
 * @param defaultMessage Optional message override for specific failure categories
 * @return Result.Failure with message and exit code
 *
 * @pre action must be non-null and non-empty
 * @post Result contains appropriate user-facing message and exit code
 */
private fun AuthStepResult.Failure.toAuthFailure(
    action: String,
    defaultMessage: String? = null,
): Result.Failure {
    val resolvedMessage =
        message ?: defaultMessage ?: when (category) {
            AuthFailureCategory.INVALID_CREDENTIALS -> AuthMessages.invalidCredentials()
            AuthFailureCategory.PASSWORD_REQUIRED -> AuthMessages.passwordRequired()
            AuthFailureCategory.NETWORK -> AuthMessages.networkFailure(action)
            AuthFailureCategory.SERVER -> AuthMessages.authServiceUnavailable()
            AuthFailureCategory.UNAUTHORIZED -> AuthMessages.unauthorizedAction(action)
            AuthFailureCategory.NOMAD_SINGLE_USER_VIOLATION -> "Nomad single user mode violation: cannot add additional users."
            AuthFailureCategory.UNKNOWN -> "Unexpected authentication error. Please retry."
        }

    val exitCode =
        when (category) {
            AuthFailureCategory.INVALID_CREDENTIALS -> ExitCodes.AUTH_FAILED
            AuthFailureCategory.PASSWORD_REQUIRED -> ExitCodes.PASSWORD_REQUIRED
            AuthFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            AuthFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            AuthFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            AuthFailureCategory.NOMAD_SINGLE_USER_VIOLATION -> ExitCodes.NOMAD_SINGLE_USER_VIOLATION
            AuthFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }

    return Result.Failure(
        error = AuthError(
            message = AuthRedactor.redact(resolvedMessage),
            exitCode = exitCode,
        ),
    )
}

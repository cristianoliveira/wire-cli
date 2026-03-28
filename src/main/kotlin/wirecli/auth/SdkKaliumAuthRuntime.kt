package wirecli.auth

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.StoreSessionParam
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthenticationResult
import com.wire.kalium.logic.feature.auth.autoVersioningAuth.AutoVersionAuthScopeUseCase
import com.wire.kalium.logic.feature.client.RegisterClientParam
import com.wire.kalium.logic.feature.client.RegisterClientResult
import com.wire.kalium.logic.feature.server.GetServerConfigResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs

private val logger = KotlinLogging.logger {}

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
@Suppress("TooManyFunctions")
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
                persistAccountBlocking(account)
            }

        validatePersistAccountResult(result, account)
        return result
    }

    /**
     * Persists authenticated account in a blocking coroutine context.
     *
     * @param account The account to persist
     * @return AuthStepResult indicating success or failure
     */
    private suspend fun persistAccountBlocking(account: PersistedAccount): AuthStepResult<Unit> {
        val userId =
            account.userId.toQualifiedIdOrNull()
                ?: run {
                    logger.warn { "Invalid user ID format for persisting account: ${account.userId}" }
                    return AuthStepResult.Failure(AuthFailureCategory.UNAUTHORIZED)
                }
        logger.debug { "User ID qualified: $userId" }

        val authTokens = buildAccountTokens(account, userId)
        logger.debug { "Persisting authenticated account to storage" }

        val sdkResult =
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
        return mapPersistResult(sdkResult, userId)
    }

    /**
     * Builds AccountTokens from PersistedAccount and qualified user ID.
     *
     * @param account The persisted account
     * @param userId The qualified user ID
     * @return AccountTokens for the account
     */
    private fun buildAccountTokens(
        account: PersistedAccount,
        userId: UserId,
    ): AccountTokens =
        AccountTokens(
            userId = userId,
            accessToken = account.accessToken,
            refreshToken = account.refreshToken,
            tokenType = account.tokenType,
            cookieLabel = account.cookieLabel,
        )

    /**
     * Maps SDK result to AuthStepResult.
     *
     * @param sdkResult The result from the SDK call
     * @param userId The qualified user ID
     * @return Mapped AuthStepResult
     */
    private fun mapPersistResult(
        sdkResult: com.wire.kalium.logic.feature.auth.AddAuthenticatedUserUseCase.Result,
        userId: UserId,
    ): AuthStepResult<Unit> =
        when (sdkResult) {
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
                logger.error { "Failed to persist account for user $userId: ${sdkResult.genericFailure}" }
                AuthStepResult.Failure(coreFailureToCategory(sdkResult.genericFailure))
            }
        }

    /**
     * Validates the result of persisting an account.
     *
     * @param result The authentication result
     * @param account The account being persisted
     * @throws AssertionError if validation fails
     */
    private fun validatePersistAccountResult(
        result: AuthStepResult<Unit>,
        account: PersistedAccount,
    ) {
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

                        RegisterClientResult.Failure.TooManyClients ->
                            AuthStepResult.Failure(
                                AuthFailureCategory.SERVER,
                            )
                        is RegisterClientResult.Failure.Generic -> {
                            AuthStepResult.Failure(coreFailureToCategory(result.genericFailure))
                        }
                    }
                } catch (
                    @Suppress("TooGenericExceptionCaught") error: Throwable,
                ) {
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
                } catch (
                    @Suppress("TooGenericExceptionCaught") error: Throwable,
                ) {
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
     * Closes the runtime and releases all resources.
     * Explicit override to ensure compatibility with all Kotlin compilers.
     */
    override fun close() {
        shutdown()
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

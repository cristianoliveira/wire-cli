package wirecli.auth

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Contract for orchestrating authentication flows.
 *
 * This interface coordinates the full authentication process, including
 * login, session persistence, client registration, and logout.
 *
 * @invariant All methods return non-null AuthApiResult
 * @invariant Successful login results include valid session data
 */
internal interface AuthenticationOrchestrator : AuthApiClient {
    // Inherits login(input: LoginInput): AuthApiResult
    // Inherits logout(session: AuthSession): AuthApiResult
}

/**
 * Standard implementation of AuthenticationOrchestrator.
 *
 * Coordinates the authentication flow by:
 * 1. Resolving the authentication scope for the server
 * 2. Performing email/password login
 * 3. Persisting the authenticated account
 * 4. Bootstrapping the session (registering client device)
 *
 * Uses AuthResponseParser for result transformation, keeping
 * orchestration logic separate from parsing logic.
 *
 * @property runtime The low-level Kalium auth runtime for API calls
 * @property parser The response parser for result transformation
 *
 * @invariant runtime is never null and properly initialized
 * @invariant parser is never null
 * @invariant All public methods return non-null AuthApiResult
 */
internal class StandardAuthenticationOrchestrator(
    private val runtime: RealKaliumAuthRuntime,
    private val parser: AuthResponseParser,
) : AuthenticationOrchestrator {
    /**
     * Authenticates a user with email and password against a Wire backend.
     *
     * Orchestrates the full login flow:
     * 1. Resolve auth scope for server
     * 2. Perform login with credentials
     * 3. Persist authenticated account
     * 4. Bootstrap session (register client)
     *
     * @param input Login credentials and server configuration
     * @return AuthApiResult.Success with authenticated session if successful;
     *   AuthApiResult.Failure with error details otherwise
     * @throws Nothing - All errors are wrapped in AuthApiResult
     *
     * @pre input.email must be non-null and non-empty
     * @pre input.password must be non-null and non-empty
     * @post result is either Success with valid AuthSession or Failure with appropriate error code
     * @post If Success, returned session has non-null userId and accessToken
     *
     * @see AuthApiResult.Success
     * @see AuthApiResult.Failure
     */
    override fun login(input: LoginInput): AuthApiResult {
        require(input.email.isNotBlank()) { "Login email must not be blank." }
        require(input.password.isNotBlank()) { "Login password must not be blank." }

        logger.debug { "Starting login orchestration for email: ${input.email}" }

        val result =
            when (val authScope = runtime.resolveAuthScope(input.server)) {
                is AuthStepResult.Success -> continueLogin(input, authScope.value)
                is AuthStepResult.Failure -> parser.parseFailure(authScope, action = "Authentication")
            }

        if (result is AuthApiResult.Success) {
            check(result.session.userId.isNotBlank()) {
                "Authentication success must include a non-blank user ID."
            }
            check(result.session.accessToken.isNotBlank()) {
                "Authentication success must include a non-blank access token."
            }
            logger.info { "Login orchestration completed successfully for userId: ${result.session.userId}" }
        } else {
            logger.warn { "Login orchestration failed" }
        }
        return result
    }

    /**
     * Logs out the currently authenticated user, invalidating their session.
     *
     * @param session The authenticated session to logout (must be valid and active)
     * @return AuthApiResult.Success if logout completed successfully;
     *   AuthApiResult.Failure with error details otherwise
     * @throws Nothing - All errors are wrapped in AuthApiResult
     *
     * @pre session must be non-null with valid userId and accessToken
     * @pre session must represent an active authenticated state
     * @post result is either Success (even if logout already happened) or Failure with error details
     * @post If Success, the session tokens are invalidated on the server
     *
     * @see AuthApiResult.Success
     * @see AuthApiResult.Failure
     */
    override fun logout(session: AuthSession): AuthApiResult {
        require(session.userId.isNotBlank()) { "Logout session user ID must not be blank." }
        require(session.accessToken.isNotBlank()) { "Logout session access token must not be blank." }

        logger.debug { "Starting logout orchestration for userId: ${session.userId}" }

        val result =
            when (val logoutResult = runtime.logout(session)) {
                is AuthStepResult.Success -> AuthApiResult.Success(session)
                is AuthStepResult.Failure -> parser.parseFailure(logoutResult, action = "Logout")
            }

        if (result is AuthApiResult.Success) {
            check(result.session.userId == session.userId) {
                "Logout success must preserve the original session user ID."
            }
            check(result.session.accessToken.isNotBlank()) {
                "Logout success must preserve a non-blank access token."
            }
            logger.info { "Logout orchestration completed successfully for userId: ${session.userId}" }
        } else {
            logger.warn { "Logout orchestration failed for userId: ${session.userId}" }
        }
        return result
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
     * @post Returns either AuthApiResult.Success or AuthApiResult.Failure
     */
    private fun continueLogin(
        input: LoginInput,
        authScope: KaliumAuthScope,
    ): AuthApiResult {
        logger.debug { "Continuing login with auth scope" }
        return when (val login = authScope.login(input.email, input.password)) {
            is AuthStepResult.Success -> persistAuthenticatedAccount(input, login.value)
            is AuthStepResult.Failure -> parser.parseFailure(login, action = "Authentication")
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
    ): AuthApiResult {
        logger.debug { "Persisting authenticated account for userId: ${success.userId}" }
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
                parser.parseFailure(
                    failure = persistence,
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
    ): AuthApiResult {
        logger.debug { "Bootstrapping session for userId: ${success.userId}" }
        val sessionScope =
            when (val sessionResult = runtime.resolveSessionScope(success.userId)) {
                is AuthStepResult.Success -> sessionResult.value
                is AuthStepResult.Failure -> {
                    return parser.parseFailure(
                        failure = sessionResult,
                        action = "Authentication",
                        defaultMessage = AuthMessages.sessionBootstrapFailed(),
                    )
                }
            }

        return when (val clientResult = runtime.ensureClient(sessionScope, input.password)) {
            is AuthStepResult.Success -> {
                logger.debug { "Client registered successfully, parsing success result" }
                parser.parseSuccess(success, input.server)
            }

            is AuthStepResult.Failure ->
                parser.parseFailure(
                    failure = clientResult,
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

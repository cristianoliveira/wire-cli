package wirecli.auth

/**
 * Contract for parsing and transforming authentication API responses.
 *
 * This interface handles the transformation of low-level authentication results
 * into the public API result types, including error mapping and message formatting.
 *
 * @invariant All methods return non-null results
 * @invariant Failure messages are always redacted
 */
internal interface AuthResponseParser {
    /**
     * Parses a successful authentication into an API result.
     *
     * @param principal The authenticated principal with tokens and metadata
     * @param server The server URL used for authentication
     * @return AuthApiResult.Success with the session data
     *
     * @pre principal.userId must be non-blank
     * @pre principal.accessToken must be non-blank
     * @post Result contains valid AuthSession
     */
    fun parseSuccess(
        principal: AuthenticatedPrincipal,
        server: String?,
    ): AuthApiResult.Success

    /**
     * Parses an authentication failure into an API result.
     *
     * @param failure The step failure with category and optional message
     * @param action The action being performed (used in error messages)
     * @param defaultMessage Optional override message for specific failure categories
     * @return AuthApiResult.Failure with appropriate message and exit code
     *
     * @pre action must be non-null and non-empty
     * @post Result message is redacted
     * @post Exit code matches failure category
     */
    fun parseFailure(
        failure: AuthStepResult.Failure,
        action: String,
        defaultMessage: String? = null,
    ): AuthApiResult.Failure
}

/**
 * Standard implementation of AuthResponseParser.
 *
 * Handles transformation of authentication results including:
 * - Converting AuthenticatedPrincipal to AuthSession
 * - Mapping failure categories to user-facing messages
 * - Mapping failure categories to exit codes
 * - Redacting sensitive information from messages
 *
 * @invariant All output messages are redacted
 * @invariant Exit codes are deterministic based on category
 */
internal class StandardAuthResponseParser : AuthResponseParser {
    /**
     * Transforms an authenticated principal into a session result.
     *
     * @param principal The authenticated user data
     * @param server The server configuration used
     * @return Success result with AuthSession
     *
     * @post session.userId matches principal.userId
     * @post session.accessToken matches principal.accessToken
     */
    override fun parseSuccess(
        principal: AuthenticatedPrincipal,
        server: String?,
    ): AuthApiResult.Success {
        check(principal.userId.isNotBlank()) {
            "Authentication success must include a non-blank user ID."
        }
        check(principal.accessToken.isNotBlank()) {
            "Authentication success must include a non-blank access token."
        }

        return AuthApiResult.Success(
            session =
                AuthSession(
                    userId = principal.userId,
                    accessToken = principal.accessToken,
                    server = server,
                ),
        )
    }

    /**
     * Transforms a step failure into an API failure result.
     *
     * Maps failure categories to appropriate user-facing messages and exit codes.
     * If the failure includes an explicit message, it takes precedence over
     * category-based defaults.
     *
     * @param failure The authentication step failure
     * @param action The action context for message formatting
     * @param defaultMessage Optional message override
     * @return Failure result with redacted message and appropriate exit code
     *
     * @post Message is always redacted
     * @post Exit code corresponds to failure category
     */
    override fun parseFailure(
        failure: AuthStepResult.Failure,
        action: String,
        defaultMessage: String?,
    ): AuthApiResult.Failure {
        val resolvedMessage =
            failure.message ?: defaultMessage ?: resolveFailureMessage(failure, action)

        val exitCode = resolveExitCode(failure)

        return AuthApiResult.Failure(
            message = AuthRedactor.redact(resolvedMessage),
            exitCode = exitCode,
        )
    }

    /**
     * Maps failure category to appropriate user-facing message.
     *
     * @param failure The authentication step failure
     * @param action The action context for message formatting
     * @return User-facing message for the failure
     */
    private fun resolveFailureMessage(
        failure: AuthStepResult.Failure,
        action: String,
    ): String =
        when (failure.category) {
            AuthFailureCategory.INVALID_CREDENTIALS -> AuthMessages.INVALID_CREDENTIALS
            AuthFailureCategory.PASSWORD_REQUIRED -> AuthMessages.PASSWORD_REQUIRED
            AuthFailureCategory.NETWORK -> AuthMessages.networkFailure(action)
            AuthFailureCategory.SERVER -> AuthMessages.AUTH_SERVICE_UNAVAILABLE
            AuthFailureCategory.UNAUTHORIZED -> AuthMessages.unauthorizedAction(action)
            AuthFailureCategory.NOMAD_SINGLE_USER_VIOLATION ->
                "Nomad single user mode violation: cannot add additional users."
            AuthFailureCategory.UNKNOWN -> "Unexpected authentication error. Please retry."
        }

    /**
     * Maps failure category to appropriate exit code.
     *
     * @param failure The authentication step failure
     * @return Exit code corresponding to failure category
     */
    private fun resolveExitCode(failure: AuthStepResult.Failure): Int =
        when (failure.category) {
            AuthFailureCategory.INVALID_CREDENTIALS -> ExitCodes.AUTH_FAILED
            AuthFailureCategory.PASSWORD_REQUIRED -> ExitCodes.PASSWORD_REQUIRED
            AuthFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            AuthFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            AuthFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            AuthFailureCategory.NOMAD_SINGLE_USER_VIOLATION -> ExitCodes.NOMAD_SINGLE_USER_VIOLATION
            AuthFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }
}

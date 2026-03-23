package wirecli.auth

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Real Kalium-backed implementation of the authentication client.
 *
 * This class serves as a thin facade that delegates to [AuthenticationOrchestrator]
 * for authentication operations, maintaining backward compatibility with the
 * [AuthApiClient] interface while separating concerns:
 * - [AuthenticationOrchestrator] handles flow orchestration
 * - [StandardAuthResponseParser] handles response transformation
 * - [RealKaliumAuthRuntime] handles low-level API communication
 *
 * @param orchestrator The authentication orchestrator for login/logout operations
 *
 * @invariant orchestrator is never null and properly initialized
 * @invariant All public methods return non-null AuthApiResult
 */
internal class RealKaliumAuthClient(
    orchestrator: AuthenticationOrchestrator,
) : AuthApiClient by orchestrator

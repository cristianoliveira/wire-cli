package wirecli.connection

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

/**
 * Deterministic in-memory connection API client used by the stub backend
 * (`WIRE_BACKEND=stub`) and unit tests.
 *
 * Failure modes are selected via the `WIRE_STUB_MODE` environment variable.
 */
class StubConnectionApiClient(
    private val environment: Map<String, String>,
) : ConnectionApiClient {
    override fun sendRequest(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "connection_not_found" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.USER_NOT_FOUND,
                    exitCode = ConnectionExitCodes.NOT_FOUND,
                )

            "connection_request_network_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.REQUEST_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "connection_request_server_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.REQUEST_SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "connection_unauthorized" ->
                ConnectionActionResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else -> ConnectionActionResult.Success(message = ConnectionMessages.REQUEST_SUCCESS)
        }
    }

    override fun blockUser(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "connection_block_network_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.BLOCK_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "connection_block_server_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.BLOCK_SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "connection_unauthorized" ->
                ConnectionActionResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else -> ConnectionActionResult.Success(message = ConnectionMessages.BLOCK_SUCCESS)
        }
    }

    override fun unblockUser(
        session: AuthSession,
        userId: String,
    ): ConnectionActionResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "connection_unblock_network_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.UNBLOCK_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "connection_unblock_server_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.UNBLOCK_SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "connection_unauthorized" ->
                ConnectionActionResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else -> ConnectionActionResult.Success(message = ConnectionMessages.UNBLOCK_SUCCESS)
        }
    }
}

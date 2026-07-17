package wirecli.connection

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.user.UserConnectionState

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

    override fun acceptRequest(
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

            "connection_accept_network_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.ACCEPT_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "connection_accept_server_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.ACCEPT_SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "connection_unauthorized" ->
                ConnectionActionResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else -> ConnectionActionResult.Success(message = ConnectionMessages.ACCEPT_SUCCESS)
        }
    }

    override fun ignoreRequest(
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

            "connection_ignore_network_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.IGNORE_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "connection_ignore_server_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.IGNORE_SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "connection_unauthorized" ->
                ConnectionActionResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else -> ConnectionActionResult.Success(message = ConnectionMessages.IGNORE_SUCCESS)
        }
    }

    override fun cancelRequest(
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

            "connection_cancel_network_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.CANCEL_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "connection_cancel_server_error" ->
                ConnectionActionResult.Failure(
                    message = ConnectionMessages.CANCEL_SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "connection_unauthorized" ->
                ConnectionActionResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else -> ConnectionActionResult.Success(message = ConnectionMessages.CANCEL_SUCCESS)
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

    override fun listConnections(session: AuthSession): ConnectionListResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "connection_list_network_error" ->
                ConnectionListResult.Failure(
                    message = ConnectionMessages.LIST_NETWORK_FAILURE,
                    exitCode = ExitCodes.NETWORK_ERROR,
                )

            "connection_list_server_error" ->
                ConnectionListResult.Failure(
                    message = ConnectionMessages.LIST_SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "connection_unauthorized" ->
                ConnectionListResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else ->
                ConnectionListResult.Success(
                    view =
                        ConnectionListView(
                            connections =
                                listOf(
                                    ConnectionView(
                                        userId = "alice-uuid@example.wire.com",
                                        userName = "Alice",
                                        handle = "@alice",
                                        status = UserConnectionState.ACCEPTED,
                                        lastUpdate = "2025-01-15T10:30:00Z",
                                    ),
                                    ConnectionView(
                                        userId = "bob-uuid@example.wire.com",
                                        userName = "Bob",
                                        handle = "@bob",
                                        status = UserConnectionState.PENDING,
                                        lastUpdate = "2025-01-14T08:00:00Z",
                                    ),
                                ),
                        ),
                )
        }
    }
}

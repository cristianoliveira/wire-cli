package wirecli.connection

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StubConnectionApiClientTest {
    private val session =
        AuthSession(
            userId = "self-uuid@example.wire.com",
            accessToken = "token",
            server = null,
        )
    private val target = "bob-uuid@example.wire.com"

    @Test
    fun `accept succeeds by default`() {
        val client = StubConnectionApiClient(emptyMap())

        val result = client.acceptRequest(session, target)

        val success = assertIs<ConnectionActionResult.Success>(result)
        assertEquals(ConnectionMessages.ACCEPT_SUCCESS, success.message)
    }

    @Test
    fun `accept maps network and server error modes`() {
        val network =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_accept_network_error"))
                .acceptRequest(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.ACCEPT_NETWORK_FAILURE, network.message)
        assertEquals(ExitCodes.NETWORK_ERROR, network.exitCode)

        val server =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_accept_server_error"))
                .acceptRequest(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.ACCEPT_SERVER_FAILURE, server.message)
        assertEquals(ExitCodes.SERVER_ERROR, server.exitCode)
    }

    @Test
    fun `ignore succeeds by default`() {
        val client = StubConnectionApiClient(emptyMap())

        val result = client.ignoreRequest(session, target)

        val success = assertIs<ConnectionActionResult.Success>(result)
        assertEquals(ConnectionMessages.IGNORE_SUCCESS, success.message)
    }

    @Test
    fun `ignore maps network and server error modes`() {
        val network =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_ignore_network_error"))
                .ignoreRequest(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.IGNORE_NETWORK_FAILURE, network.message)

        val server =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_ignore_server_error"))
                .ignoreRequest(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.IGNORE_SERVER_FAILURE, server.message)
    }

    @Test
    fun `cancel succeeds by default`() {
        val client = StubConnectionApiClient(emptyMap())

        val result = client.cancelRequest(session, target)

        val success = assertIs<ConnectionActionResult.Success>(result)
        assertEquals(ConnectionMessages.CANCEL_SUCCESS, success.message)
    }

    @Test
    fun `cancel maps network and server error modes`() {
        val network =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_cancel_network_error"))
                .cancelRequest(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.CANCEL_NETWORK_FAILURE, network.message)

        val server =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_cancel_server_error"))
                .cancelRequest(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.CANCEL_SERVER_FAILURE, server.message)
    }

    @Test
    fun `request succeeds by default`() {
        val client = StubConnectionApiClient(emptyMap())

        val result = client.sendRequest(session, target)

        val success = assertIs<ConnectionActionResult.Success>(result)
        assertEquals(ConnectionMessages.REQUEST_SUCCESS, success.message)
    }

    @Test
    fun `request reports not found`() {
        val client = StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_not_found"))

        val result = client.sendRequest(session, target)

        val failure = assertIs<ConnectionActionResult.Failure>(result)
        assertEquals(ConnectionMessages.USER_NOT_FOUND, failure.message)
        assertEquals(ConnectionExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `request maps network and server error modes`() {
        val network =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_request_network_error"))
                .sendRequest(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.REQUEST_NETWORK_FAILURE, network.message)
        assertEquals(ExitCodes.NETWORK_ERROR, network.exitCode)

        val server =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_request_server_error"))
                .sendRequest(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.REQUEST_SERVER_FAILURE, server.message)
        assertEquals(ExitCodes.SERVER_ERROR, server.exitCode)
    }

    @Test
    fun `request maps unauthorized mode`() {
        val client = StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_unauthorized"))

        val result = client.sendRequest(session, target)

        val failure = assertIs<ConnectionActionResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `block succeeds by default`() {
        val client = StubConnectionApiClient(emptyMap())

        val result = client.blockUser(session, target)

        val success = assertIs<ConnectionActionResult.Success>(result)
        assertEquals(ConnectionMessages.BLOCK_SUCCESS, success.message)
    }

    @Test
    fun `block maps network and server error modes`() {
        val network =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_block_network_error"))
                .blockUser(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.BLOCK_NETWORK_FAILURE, network.message)

        val server =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_block_server_error"))
                .blockUser(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.BLOCK_SERVER_FAILURE, server.message)
    }

    @Test
    fun `unblock succeeds by default`() {
        val client = StubConnectionApiClient(emptyMap())

        val result = client.unblockUser(session, target)

        val success = assertIs<ConnectionActionResult.Success>(result)
        assertEquals(ConnectionMessages.UNBLOCK_SUCCESS, success.message)
    }

    @Test
    fun `unblock maps network and server error modes`() {
        val network =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_unblock_network_error"))
                .unblockUser(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.UNBLOCK_NETWORK_FAILURE, network.message)

        val server =
            StubConnectionApiClient(mapOf("WIRE_STUB_MODE" to "connection_unblock_server_error"))
                .unblockUser(session, target) as ConnectionActionResult.Failure
        assertEquals(ConnectionMessages.UNBLOCK_SERVER_FAILURE, server.message)
    }
}

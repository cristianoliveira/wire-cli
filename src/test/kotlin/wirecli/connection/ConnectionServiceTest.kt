package wirecli.connection

import wirecli.auth.AuthMessages
import wirecli.auth.AuthResult
import wirecli.auth.AuthSession
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import wirecli.auth.LoginInput
import wirecli.auth.SessionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectionServiceTest {
    private val session =
        AuthSession(
            userId = "self-uuid@example.wire.com",
            accessToken = "token",
            server = null,
        )
    private val target = "bob-uuid@example.wire.com"

    @Test
    fun `SessionBacked request delegates to api client`() {
        val apiClient = RecordingConnectionApiClient()
        val service = SessionBackedConnectionService(SessionStore(session), apiClient)

        val result = service.sendRequest(target)

        assertIs<ConnectionActionResult.Success>(result)
        assertEquals(target, apiClient.lastRequestUserId)
    }

    @Test
    fun `SessionBacked request returns unauthorized when no session`() {
        val service = SessionBackedConnectionService(NoSessionStore, RecordingConnectionApiClient())

        val result = service.sendRequest(target)

        val failure = assertIs<ConnectionActionResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `SessionBacked block delegates userId`() {
        val apiClient = RecordingConnectionApiClient()
        val service = SessionBackedConnectionService(SessionStore(session), apiClient)

        service.blockUser(target)

        assertEquals(target, apiClient.lastBlockUserId)
    }

    @Test
    fun `SessionBacked unblock returns unauthorized when no session`() {
        val service = SessionBackedConnectionService(NoSessionStore, RecordingConnectionApiClient())

        val result = service.unblockUser(target)

        assertIs<ConnectionActionResult.Failure>(result)
        assertEquals(ExitCodes.UNAUTHORIZED, result.exitCode)
    }

    @Test
    fun `AuthGuarded request returns auth failure when session missing`() {
        val service =
            AuthGuardedConnectionService(
                FailingAuthSessionService,
                SessionBackedConnectionService(NoSessionStore, RecordingConnectionApiClient()),
            )

        val result = service.sendRequest(target)

        assertIs<ConnectionActionResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), result.message)
    }

    @Test
    fun `AuthGuarded block returns auth failure when session missing`() {
        val service =
            AuthGuardedConnectionService(
                FailingAuthSessionService,
                SessionBackedConnectionService(NoSessionStore, RecordingConnectionApiClient()),
            )

        val result = service.blockUser(target)

        assertIs<ConnectionActionResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), result.message)
    }

    @Test
    fun `SessionBacked list delegates to api client`() {
        val apiClient = RecordingConnectionApiClient()
        val service = SessionBackedConnectionService(SessionStore(session), apiClient)

        val result = service.listConnections()

        assertIs<ConnectionListResult.Success>(result)
        assertTrue(result.view.connections.isEmpty())
    }

    @Test
    fun `SessionBacked list returns unauthorized when no session`() {
        val service = SessionBackedConnectionService(NoSessionStore, RecordingConnectionApiClient())

        val result = service.listConnections()

        val failure = assertIs<ConnectionListResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `AuthGuarded list returns auth failure when session missing`() {
        val service =
            AuthGuardedConnectionService(
                FailingAuthSessionService,
                SessionBackedConnectionService(NoSessionStore, RecordingConnectionApiClient()),
            )

        val result = service.listConnections()

        assertIs<ConnectionListResult.Failure>(result)
        assertEquals(AuthMessages.noActiveSession(), result.message)
    }

    private object NoSessionStore : SessionProvider {
        override fun readActiveSession(): AuthSession? = null
    }

    private class SessionStore(private val active: AuthSession) : SessionProvider {
        override fun readActiveSession(): AuthSession? = active
    }

    private object FailingAuthSessionService : AuthSessionService {
        override fun login(input: LoginInput): AuthResult = AuthResult.Failure("nope", ExitCodes.UNAUTHORIZED)

        override fun logout(): AuthResult = AuthResult.Failure("nope", ExitCodes.UNAUTHORIZED)

        override fun requireActiveSession(): AuthResult = AuthResult.Failure(AuthMessages.noActiveSession(), ExitCodes.UNAUTHORIZED)
    }

    private class RecordingConnectionApiClient : ConnectionApiClient {
        var lastRequestUserId: String? = null
            private set
        var lastBlockUserId: String? = null
            private set
        var lastUnblockUserId: String? = null
            private set

        override fun sendRequest(
            session: AuthSession,
            userId: String,
        ): ConnectionActionResult {
            lastRequestUserId = userId
            return ConnectionActionResult.Success(ConnectionMessages.REQUEST_SUCCESS)
        }

        override fun blockUser(
            session: AuthSession,
            userId: String,
        ): ConnectionActionResult {
            lastBlockUserId = userId
            return ConnectionActionResult.Success(ConnectionMessages.BLOCK_SUCCESS)
        }

        override fun unblockUser(
            session: AuthSession,
            userId: String,
        ): ConnectionActionResult {
            lastUnblockUserId = userId
            return ConnectionActionResult.Success(ConnectionMessages.UNBLOCK_SUCCESS)
        }

        override fun listConnections(session: AuthSession): ConnectionListResult =
            ConnectionListResult.Success(
                ConnectionListView(emptyList()),
            )
    }
}

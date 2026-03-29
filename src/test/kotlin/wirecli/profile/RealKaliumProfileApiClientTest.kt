package wirecli.profile

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RealKaliumProfileApiClientTest {
    private val session = AuthSession(userId = "jane@example.com", accessToken = "token", server = null)

    @Test
    fun `maps unauthorized session scope failure to re-auth guidance`() {
        val runtime =
            FakeRuntime(
                scopeResult = ProfileStepResult.Failure(ProfileFailureCategory.UNAUTHORIZED),
                selfUserResult = ProfileStepResult.Success(KaliumSelfUser("Jane Doe", "jane@example.com", "jane")),
            )
        val client = RealKaliumProfileApiClient(runtime)

        val result = client.fetchProfile(session)

        val failure = assertIs<ProfileResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `maps network self-user failure to retry semantics`() {
        val runtime =
            FakeRuntime(
                scopeResult = ProfileStepResult.Success(KaliumProfileSessionScope(session.userId, session.server)),
                selfUserResult = ProfileStepResult.Failure(ProfileFailureCategory.NETWORK),
            )
        val client = RealKaliumProfileApiClient(runtime)

        val result = client.fetchProfile(session)

        val failure = assertIs<ProfileResult.Failure>(result)
        assertEquals(
            "Profile fetch failed: network is unreachable. Check your connection and retry.",
            failure.message,
        )
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `maps server self-user failure to server semantics`() {
        val runtime =
            FakeRuntime(
                scopeResult = ProfileStepResult.Success(KaliumProfileSessionScope(session.userId, session.server)),
                selfUserResult = ProfileStepResult.Failure(ProfileFailureCategory.SERVER),
            )
        val client = RealKaliumProfileApiClient(runtime)

        val result = client.fetchProfile(session)

        val failure = assertIs<ProfileResult.Failure>(result)
        assertEquals("Profile service is unavailable. Retry later or check server settings.", failure.message)
        assertEquals(ExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `maps unknown failures to unknown exit code`() {
        val runtime =
            FakeRuntime(
                scopeResult = ProfileStepResult.Success(KaliumProfileSessionScope(session.userId, session.server)),
                selfUserResult = ProfileStepResult.Failure(ProfileFailureCategory.UNKNOWN),
            )
        val client = RealKaliumProfileApiClient(runtime)

        val result = client.fetchProfile(session)

        val failure = assertIs<ProfileResult.Failure>(result)
        assertEquals("Profile fetch failed unexpectedly. Retry and check your setup.", failure.message)
        assertEquals(ExitCodes.UNKNOWN_ERROR, failure.exitCode)
    }

    private class FakeRuntime(
        private val scopeResult: ProfileStepResult<KaliumProfileSessionScope>,
        private val selfUserResult: ProfileStepResult<KaliumSelfUser>,
    ) : RealKaliumProfileRuntime {
        override fun resolveSessionScope(session: AuthSession) = scopeResult

        override fun getSelfUser(sessionScope: KaliumProfileSessionScope): ProfileStepResult<KaliumSelfUser> = selfUserResult

        override fun shutdown() {
            // No-op for test stub
        }
    }
}

package wirecli.download

import wirecli.auth.AuthSession
import wirecli.auth.SessionProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionBackedDownloadServiceTest {
    @Test
    fun `delegates to API client when session is active`() {
        var capturedSession: AuthSession? = null
        var capturedConvId: String? = null
        var capturedMsgId: String? = null
        var capturedOutputDir: String? = null

        val apiClient =
            object : DownloadApiClient {
                override fun downloadAsset(
                    session: AuthSession,
                    conversationId: String,
                    messageId: String,
                    outputDir: String,
                ): DownloadAssetResult {
                    capturedSession = session
                    capturedConvId = conversationId
                    capturedMsgId = messageId
                    capturedOutputDir = outputDir
                    return DownloadAssetResult.Success(
                        DownloadedAsset(path = "/out/file.bin", size = 100, name = "file.bin"),
                    )
                }
            }

        val service =
            SessionBackedDownloadService(
                sessionStore = FakeSessionStore(activeSession),
                apiClient = apiClient,
            )

        val result = service.downloadAsset("conv-1", "msg-1", "/out")

        assertTrue(result is DownloadAssetResult.Success)
        assertEquals(activeSession, capturedSession)
        assertEquals("conv-1", capturedConvId)
        assertEquals("msg-1", capturedMsgId)
        assertEquals("/out", capturedOutputDir)
    }

    @Test
    fun `returns failure when no active session`() {
        val service =
            SessionBackedDownloadService(
                sessionStore = FakeSessionStore(activeSession = null),
                apiClient = FakeDownloadApiClient(),
            )

        val result = service.downloadAsset("conv-1", "msg-1", "/out")

        assertTrue(result is DownloadAssetResult.Failure)
        val failure = result as DownloadAssetResult.Failure
        assertEquals(DownloadExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    private companion object {
        val activeSession =
            AuthSession(
                userId = "user@wire.com",
                accessToken = "token",
                server = "wire.com",
            )
    }
}

private class FakeSessionStore(
    private val activeSession: AuthSession?,
) : SessionProvider {
    override fun readActiveSession(): AuthSession? = activeSession
}

private class FakeDownloadApiClient : DownloadApiClient {
    override fun downloadAsset(
        session: AuthSession,
        conversationId: String,
        messageId: String,
        outputDir: String,
    ): DownloadAssetResult =
        DownloadAssetResult.Success(
            DownloadedAsset(path = "/tmp/file.bin", size = 0, name = "file.bin"),
        )
}

package wirecli.download

import wirecli.auth.AuthSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealKaliumDownloadApiClientTest {
    @Test
    fun `maps runtime success to DownloadAssetResult Success`() {
        val asset = DownloadedAsset(path = "/out/photo.jpg", size = 100, name = "photo.jpg")
        val runtime = FakeDownloadRuntime(result = DownloadStepResult.Success(asset))
        val client = RealKaliumDownloadApiClient(runtime)

        val result = client.downloadAsset(session, "conv-1", "msg-1", "/out")

        assertTrue(result is DownloadAssetResult.Success)
        val success = result as DownloadAssetResult.Success
        assertEquals("/out/photo.jpg", success.asset.path)
        assertEquals(100, success.asset.size)
        assertEquals("photo.jpg", success.asset.name)
    }

    @Test
    fun `maps runtime unauthorized to DownloadAssetResult Failure`() {
        val runtime =
            FakeDownloadRuntime(
                result = DownloadStepResult.Failure(DownloadFailureCategory.UNAUTHORIZED),
            )
        val client = RealKaliumDownloadApiClient(runtime)

        val result = client.downloadAsset(session, "conv-1", "msg-1", "/out")

        assertTrue(result is DownloadAssetResult.Failure)
        val failure = result as DownloadAssetResult.Failure
        assertEquals(DownloadExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    @Test
    fun `maps runtime not found to DownloadAssetResult Failure`() {
        val runtime =
            FakeDownloadRuntime(
                result = DownloadStepResult.Failure(DownloadFailureCategory.NOT_FOUND),
            )
        val client = RealKaliumDownloadApiClient(runtime)

        val result = client.downloadAsset(session, "conv-1", "msg-1", "/out")

        assertTrue(result is DownloadAssetResult.Failure)
        val failure = result as DownloadAssetResult.Failure
        assertEquals(DownloadExitCodes.NOT_FOUND, failure.exitCode)
    }

    @Test
    fun `maps runtime network error to DownloadAssetResult Failure`() {
        val runtime =
            FakeDownloadRuntime(
                result = DownloadStepResult.Failure(DownloadFailureCategory.NETWORK),
            )
        val client = RealKaliumDownloadApiClient(runtime)

        val result = client.downloadAsset(session, "conv-1", "msg-1", "/out")

        assertTrue(result is DownloadAssetResult.Failure)
        val failure = result as DownloadAssetResult.Failure
        assertEquals(DownloadExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `maps runtime timeout to DownloadAssetResult Failure`() {
        val runtime =
            FakeDownloadRuntime(
                result = DownloadStepResult.Failure(DownloadFailureCategory.TIMEOUT),
            )
        val client = RealKaliumDownloadApiClient(runtime)

        val result = client.downloadAsset(session, "conv-1", "msg-1", "/out")

        assertTrue(result is DownloadAssetResult.Failure)
        val failure = result as DownloadAssetResult.Failure
        assertEquals(DownloadExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    private companion object {
        val session =
            AuthSession(
                userId = "user@wire.com",
                accessToken = "token",
                server = "wire.com",
            )
    }
}

private class FakeDownloadRuntime(
    private val result: DownloadStepResult<DownloadedAsset>,
) : DownloadRuntime {
    override fun downloadAsset(
        session: AuthSession,
        conversationId: String,
        messageId: String,
        outputDir: String,
    ): DownloadStepResult<DownloadedAsset> = result

    override fun shutdown() {}
}

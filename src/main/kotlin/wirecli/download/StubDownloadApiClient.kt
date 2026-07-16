package wirecli.download

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

class StubDownloadApiClient(
    private val environment: Map<String, String> = emptyMap(),
) : DownloadApiClient {
    companion object {
        private const val DEFAULT_ASSET_PATH = "/stub/downloads/file.bin"
        private const val DEFAULT_ASSET_SIZE = 1024L
        private const val DEFAULT_ASSET_NAME = "file.bin"
    }

    private val mode: StubDownloadMode by lazy {
        val modeString =
            environment["WIRE_STUB_MODE"]
                ?: environment["__mode__"]
                ?: return@lazy StubDownloadMode.SUCCESS
        StubDownloadMode.entries.firstOrNull {
            it.name.equals(modeString, ignoreCase = true) ||
                it.name.replace("_", "-").equals(modeString, ignoreCase = true)
        } ?: StubDownloadMode.SUCCESS
    }

    override fun downloadAsset(
        session: AuthSession,
        conversationId: String,
        messageId: String,
        outputDir: String,
    ): DownloadAssetResult {
        return when (mode) {
            StubDownloadMode.SUCCESS ->
                DownloadAssetResult.Success(
                    DownloadedAsset(
                        path = "$outputDir/$DEFAULT_ASSET_NAME",
                        size = DEFAULT_ASSET_SIZE,
                        name = DEFAULT_ASSET_NAME,
                    ),
                )

            StubDownloadMode.UNAUTHORIZED ->
                DownloadAssetResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            StubDownloadMode.NETWORK_ERROR ->
                DownloadAssetResult.Failure(
                    message = DownloadUserMessages.NETWORK_ERROR,
                    exitCode = DownloadExitCodes.NETWORK_ERROR,
                )

            StubDownloadMode.NOT_FOUND ->
                DownloadAssetResult.Failure(
                    message = DownloadUserMessages.NOT_FOUND,
                    exitCode = DownloadExitCodes.NOT_FOUND,
                )

            StubDownloadMode.SERVER_ERROR ->
                DownloadAssetResult.Failure(
                    message = DownloadUserMessages.SERVER_ERROR,
                    exitCode = DownloadExitCodes.SERVER_ERROR,
                )
        }
    }
}

enum class StubDownloadMode {
    SUCCESS,
    UNAUTHORIZED,
    NETWORK_ERROR,
    NOT_FOUND,
    SERVER_ERROR,
}

package wirecli.download

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

private val logger = KotlinLogging.logger {}

internal class RealKaliumDownloadApiClient(
    private val runtime: DownloadRuntime,
) : DownloadApiClient {
    override fun downloadAsset(
        session: AuthSession,
        conversationId: String,
        messageId: String,
        outputDir: String,
    ): DownloadAssetResult {
        logger.info {
            "download api start: conversationId=$conversationId messageId=$messageId"
        }

        return when (val result = runtime.downloadAsset(session, conversationId, messageId, outputDir)) {
            is DownloadStepResult.Success -> {
                logger.info {
                    "download api outcome=success conversationId=$conversationId messageId=$messageId"
                }
                DownloadAssetResult.Success(result.value)
            }

            is DownloadStepResult.Failure -> {
                val (message, exitCode) =
                    when (result.category) {
                        DownloadFailureCategory.VALIDATION ->
                            DownloadUserMessages.VALIDATION_ERROR to DownloadExitCodes.VALIDATION_ERROR

                        DownloadFailureCategory.UNAUTHORIZED ->
                            AuthMessages.invalidOrExpiredSession() to ExitCodes.UNAUTHORIZED

                        DownloadFailureCategory.TIMEOUT ->
                            DownloadUserMessages.DOWNLOAD_TIMEOUT to ExitCodes.NETWORK_ERROR

                        DownloadFailureCategory.NETWORK ->
                            DownloadUserMessages.NETWORK_ERROR to ExitCodes.NETWORK_ERROR

                        DownloadFailureCategory.SERVER ->
                            DownloadUserMessages.SERVER_ERROR to ExitCodes.SERVER_ERROR

                        DownloadFailureCategory.NOT_FOUND ->
                            DownloadUserMessages.NOT_FOUND to DownloadExitCodes.NOT_FOUND

                        DownloadFailureCategory.UNKNOWN ->
                            DownloadUserMessages.UNKNOWN_ERROR to ExitCodes.UNKNOWN_ERROR
                    }

                logger.warn {
                    "download category mapping: category=${result.category} -> exitCode=$exitCode " +
                        "conversationId=$conversationId messageId=$messageId"
                }
                DownloadAssetResult.Failure(message = message, exitCode = exitCode)
            }
        }
    }
}

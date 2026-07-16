package wirecli.download

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider

private val logger = KotlinLogging.logger {}

class SessionBackedDownloadService(
    private val sessionStore: SessionProvider,
    private val apiClient: DownloadApiClient,
) : DownloadService {
    override fun downloadAsset(
        conversationId: String,
        messageId: String,
        outputDir: String,
    ): DownloadAssetResult {
        logger.debug {
            "Service operation: downloadAsset(conversationId=$conversationId, messageId=$messageId) started"
        }

        val session =
            sessionStore.readActiveSession()
                ?: return DownloadAssetResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for downloadAsset($conversationId)" } }

        logger.info {
            "download session resolved: userId=${session.userId}, conversationId=$conversationId"
        }
        return apiClient.downloadAsset(session, conversationId, messageId, outputDir).also { result ->
            when (result) {
                is DownloadAssetResult.Success ->
                    logger.info {
                        "download service outcome=success conversationId=$conversationId " +
                            "messageId=$messageId path=${result.asset.path}"
                    }
                is DownloadAssetResult.Failure ->
                    logger.warn {
                        "download service outcome=failure conversationId=$conversationId " +
                            "messageId=$messageId exitCode=${result.exitCode}"
                    }
            }
        }
    }
}

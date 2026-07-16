package wirecli.download

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class AuthGuardedDownloadService(
    private val authSessionService: AuthSessionService,
    private val delegate: DownloadService,
) : DownloadService {
    override fun downloadAsset(
        conversationId: String,
        messageId: String,
        outputDir: String,
    ): DownloadAssetResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.downloadAsset(conversationId, messageId, outputDir)
            is AuthResult.Failure ->
                DownloadAssetResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

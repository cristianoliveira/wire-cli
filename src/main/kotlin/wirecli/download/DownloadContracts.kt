package wirecli.download

import wirecli.auth.AuthSession

data class DownloadedAsset(
    val path: String,
    val size: Long,
    val name: String,
)

sealed interface DownloadAssetResult {
    data class Success(val asset: DownloadedAsset) : DownloadAssetResult

    data class Failure(val message: String, val exitCode: Int) : DownloadAssetResult
}

interface DownloadService {
    fun downloadAsset(
        conversationId: String,
        messageId: String,
        outputDir: String,
    ): DownloadAssetResult
}

// Exit codes for download operations following standard CLI conventions
object DownloadExitCodes {
    const val OK = 0
    const val VALIDATION_ERROR = 14
    const val UNAUTHORIZED = 11
    const val NETWORK_ERROR = 12
    const val SERVER_ERROR = 13
    const val NOT_FOUND = 13
}

// User-friendly error messages for download operations
internal object DownloadUserMessages {
    const val UNAUTHORIZED = "you must be logged in to download assets"
    const val VALIDATION_ERROR = "invalid conversation ID or message ID"
    const val NETWORK_ERROR = "network error while downloading asset"
    const val DOWNLOAD_TIMEOUT = "asset download timed out"
    const val SERVER_ERROR = "server error while downloading asset"
    const val NOT_FOUND = "message not found or is not an asset"
    const val NOT_ASSET = "message is not an asset"
    const val UNKNOWN_ERROR = "unknown error while downloading asset"
}

// Step result for runtime-level operations (SDK adapter layer)
internal sealed interface DownloadStepResult<out T> {
    data class Success<T>(val value: T) : DownloadStepResult<T>

    data class Failure(val category: DownloadFailureCategory) : DownloadStepResult<Nothing>
}

internal enum class DownloadFailureCategory {
    VALIDATION,
    TIMEOUT,
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    NOT_FOUND,
    UNKNOWN,
}

// Runtime-level interface for SDK adapters
internal interface DownloadRuntime {
    fun downloadAsset(
        session: AuthSession,
        conversationId: String,
        messageId: String,
        outputDir: String,
    ): DownloadStepResult<DownloadedAsset>

    fun shutdown()
}

// Low-level API client interface - works with AuthSession directly
interface DownloadApiClient {
    fun downloadAsset(
        session: AuthSession,
        conversationId: String,
        messageId: String,
        outputDir: String,
    ): DownloadAssetResult
}

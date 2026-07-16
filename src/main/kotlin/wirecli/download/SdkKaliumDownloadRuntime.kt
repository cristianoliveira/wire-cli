package wirecli.download

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.asset.MessageAssetResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import wirecli.auth.AuthSession
import wirecli.config.KaliumCliMode
import wirecli.config.kaliumCliConfigs
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

private val logger = KotlinLogging.logger {}

/**
 * SDK-based implementation of DownloadRuntime using CoreLogic.
 */
internal class SdkKaliumDownloadRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : DownloadRuntime {
    private val coreLogicLazy =
        lazy {
            CoreLogic(
                rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium",
                kaliumConfigs = kaliumCliConfigs(cliMode),
                userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
            )
        }
    private val coreLogic: CoreLogic by coreLogicLazy

    override fun downloadAsset(
        session: AuthSession,
        conversationId: String,
        messageId: String,
        outputDir: String,
    ): DownloadStepResult<DownloadedAsset> {
        val qualifiedId =
            session.userId.trim().let { raw ->
                val atIndex = raw.lastIndexOf('@')
                if (atIndex <= 0 || atIndex >= raw.lastIndex) {
                    logger.warn { "downloadAsset: Invalid user ID format: ${session.userId}" }
                    return DownloadStepResult.Failure(DownloadFailureCategory.UNAUTHORIZED)
                }
                com.wire.kalium.logic.data.user.UserId(
                    value = raw.substring(0, atIndex),
                    domain = raw.substring(atIndex + 1),
                )
            }

        val kaliumConvId =
            ConversationId(
                value = conversationId,
                domain = session.server ?: "wire.com",
            )

        return runBlocking {
            try {
                val assetResult: MessageAssetResult =
                    coreLogic.sessionScope(qualifiedId) {
                        withContext(Dispatchers.Default) {
                            val deferred =
                                messages.getAssetMessage(
                                    kaliumConvId,
                                    messageId,
                                )
                            deferred.await()
                        }
                    }

                when (assetResult) {
                    is MessageAssetResult.Success -> {
                        val decodedPath = assetResult.decodedAssetPath
                        val sourcePath = Paths.get(decodedPath.toString())
                        val outputDirPath = Paths.get(outputDir)
                        Files.createDirectories(outputDirPath)

                        val fileName =
                            assetResult.assetName.ifBlank {
                                val sourceName = sourcePath.fileName.toString()
                                if (sourceName.contains('.')) {
                                    sourceName
                                } else {
                                    "${messageId.take(8)}.bin"
                                }
                            }
                        val outputPath = outputDirPath.resolve(fileName)

                        logger.info {
                            "downloadAsset: copying from $sourcePath to $outputPath"
                        }

                        Files.copy(
                            sourcePath,
                            outputPath,
                            StandardCopyOption.REPLACE_EXISTING,
                        )

                        logger.info {
                            "downloadAsset: success conversationId=$conversationId " +
                                "messageId=$messageId assetName=$fileName " +
                                "outputPath=$outputPath"
                        }

                        DownloadStepResult.Success(
                            DownloadedAsset(
                                path = outputPath.toString(),
                                size = assetResult.assetSize,
                                name = fileName,
                            ),
                        )
                    }

                    is MessageAssetResult.Failure -> {
                        val failure = assetResult.coreFailure
                        val category = mapCoreFailure(failure)
                        logger.warn {
                            "downloadAsset: failure conversationId=$conversationId " +
                                "messageId=$messageId category=$category failure=$failure"
                        }
                        DownloadStepResult.Failure(category)
                    }
                }
            } catch (error: Throwable) {
                val category = mapThrowable(error)
                logger.error(error) {
                    "downloadAsset: exception conversationId=$conversationId " +
                        "messageId=$messageId category=$category"
                }
                DownloadStepResult.Failure(category)
            }
        }
    }

    private fun mapCoreFailure(failure: com.wire.kalium.common.error.CoreFailure): DownloadFailureCategory {
        val className = failure::class.simpleName.orEmpty().lowercase()
        val message = failure.toString().lowercase()

        return when {
            failure is com.wire.kalium.common.error.NetworkFailure -> {
                when {
                    message.contains("no network") || message.contains("connection") ->
                        DownloadFailureCategory.NETWORK
                    message.contains("not found") || className.contains("notfound") ->
                        DownloadFailureCategory.NOT_FOUND
                    else -> DownloadFailureCategory.SERVER
                }
            }
            className.contains("storage") && message.contains("not found") ->
                DownloadFailureCategory.NOT_FOUND
            className.contains("unauthorized") || message.contains("unauthorized") ->
                DownloadFailureCategory.UNAUTHORIZED
            message.contains("not an asset") ->
                DownloadFailureCategory.NOT_FOUND
            else -> DownloadFailureCategory.UNKNOWN
        }
    }

    private fun mapThrowable(error: Throwable): DownloadFailureCategory {
        val message = error.message.orEmpty().lowercase()
        return when {
            message.contains("timeout") -> DownloadFailureCategory.TIMEOUT
            message.contains("network") || message.contains("connection") ->
                DownloadFailureCategory.NETWORK
            message.contains("unauthorized") || message.contains("401") ->
                DownloadFailureCategory.UNAUTHORIZED
            message.contains("not found") || message.contains("404") ->
                DownloadFailureCategory.NOT_FOUND
            else -> DownloadFailureCategory.UNKNOWN
        }
    }

    override fun shutdown() {
        // CoreLogic is lazy; nothing to clean up unless it was initialized.
    }

    private companion object {
        private fun resolveHomeDirectory(environment: Map<String, String>): String {
            return environment["HOME"]
                ?: System.getProperty("user.home")
                ?: Paths.get("").toAbsolutePath().toString()
        }
    }
}

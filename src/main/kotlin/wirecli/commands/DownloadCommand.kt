package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import wirecli.download.DownloadAssetResult
import wirecli.download.DownloadService

class DownloadCommand(
    private val downloadServiceProvider: () -> DownloadService,
) : CliktCommand(
        name = "download",
        help = "Download an asset from a conversation message.",
    ) {
    private val conversationId by argument(
        name = "CONVERSATION_ID",
        help = "The conversation ID containing the asset message.",
    )

    private val messageId by argument(
        name = "MESSAGE_ID",
        help = "The message ID of the asset to download.",
    )

    private val outputDir by option(
        "--output",
        "-o",
        help = "Directory to save the downloaded file (default: current directory).",
    ).default(".")

    override fun run() {
        val validatedConversationId =
            requireValueOrExit(
                value = conversationId,
                fieldName = "Conversation ID",
                errorMessage = "conversation required",
            )

        val validatedMessageId =
            requireValueOrExit(
                value = messageId,
                fieldName = "Message ID",
                errorMessage = "message ID required",
            )

        val downloadService = downloadServiceProvider()
        when (val result = downloadService.downloadAsset(validatedConversationId, validatedMessageId, outputDir)) {
            is DownloadAssetResult.Success -> {
                val sizeLabel = formatSize(result.asset.size)
                echo("Downloaded \"${result.asset.name}\" ($sizeLabel) -> ${result.asset.path}")
            }

            is DownloadAssetResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private companion object {
        fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> {
                    val mb = bytes.toDouble() / (1024 * 1024)
                    "%.1f MB".format(java.util.Locale.US, mb)
                }
                else -> {
                    val gb = bytes.toDouble() / (1024 * 1024 * 1024)
                    "%.1f GB".format(java.util.Locale.US, gb)
                }
            }
        }
    }
}

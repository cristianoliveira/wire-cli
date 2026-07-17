package wirecli.exporting

import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupMessageContent
import com.wire.backup.data.toLongMilliseconds
import com.wire.backup.ingest.BackupImportResult
import com.wire.backup.ingest.ImportResultPager
import com.wire.backup.ingest.MPBackupImporter
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import wirecli.importing.ImportSource
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class WireBackupJsonExporter(private val json: Json = Json) : Exporter {
    override val source = ImportSource.WIRE_BACKUP

    override fun export(
        input: Path,
        destination: Path,
        password: String?,
        options: ExportOptions,
    ): ExportResult {
        if (!input.exists()) return ExportResult.Failure("backup file not found: $input")
        return runCatching {
            Files.createDirectories(destination)
            val work = Files.createTempDirectory("wire-backup-export")
            try {
                val importer =
                    MPBackupImporter(work.absolutePathString()) { archive ->
                        unzip(archive, work.resolve("unzipped"))
                        work.resolve("unzipped").absolutePathString()
                    }
                when (val result = runBlocking { importer.importFromFile(input.absolutePathString(), password) }) {
                    is BackupImportResult.Success -> write(result.pager, destination, options)
                    BackupImportResult.Failure.MissingOrWrongPassphrase -> ExportResult.Failure("missing or wrong backup password")
                    BackupImportResult.Failure.ParsingFailure -> ExportResult.Failure("invalid or unsupported backup")
                    is BackupImportResult.Failure.UnzippingError -> ExportResult.Failure("failed to unzip backup")
                    is BackupImportResult.Failure.UnknownError -> ExportResult.Failure("failed to export backup: ${result.message}")
                }
            } finally {
                work.toFile().deleteRecursively()
            }
        }.getOrElse { ExportResult.Failure("failed to export backup: ${it.message}") }
    }

    private fun unzip(
        archive: String,
        destination: Path,
    ) {
        Files.createDirectories(destination)
        ZipInputStream(FileInputStream(archive)).use { stream ->
            var entry = stream.nextEntry
            while (entry != null) {
                val output = destination.resolve(entry.name).normalize()
                require(output.startsWith(destination)) { "backup contains an unsafe path" }
                if (entry.isDirectory) {
                    Files.createDirectories(output)
                } else {
                    Files.createDirectories(output.parent)
                    Files.copy(stream, output, StandardCopyOption.REPLACE_EXISTING)
                }
                entry = stream.nextEntry
            }
        }
    }

    internal fun write(
        pager: ImportResultPager,
        destination: Path,
        options: ExportOptions = ExportOptions.DEFAULT,
    ): ExportResult.Success {
        var conversations = 0
        var messages = 0
        var users = 0
        val names = if (options.includeNames) NameIndex() else null
        Files.newBufferedWriter(destination.resolve("conversations.jsonl")).use { writer ->
            while (pager.conversationsPager.hasMorePages()) pager.conversationsPager.nextPage().forEach {
                names?.conversations[it.id.toString()] = it.name
                writer.appendLine(
                    json.encodeToString(
                        buildJsonObject {
                            put("id", it.id.toString())
                            put("name", it.name)
                            it.lastModifiedTime?.let { time -> put("lastModifiedTime", time.toLongMilliseconds()) }
                        },
                    ),
                )
                conversations++
            }
        }
        Files.newBufferedWriter(destination.resolve("users.jsonl")).use { writer ->
            while (pager.usersPager.hasMorePages()) pager.usersPager.nextPage().forEach {
                names?.userNames[it.id.toString()] = it.name
                names?.userHandles[it.id.toString()] = it.handle
                writer.appendLine(
                    json.encodeToString(
                        buildJsonObject {
                            put("id", it.id.toString())
                            put("name", it.name)
                            put("handle", it.handle)
                        },
                    ),
                )
                users++
            }
        }
        Files.newBufferedWriter(destination.resolve("messages.jsonl")).use { writer ->
            while (pager.messagesPager.hasMorePages()) pager.messagesPager.nextPage().forEach {
                writer.appendLine(json.encodeToString(messageJson(it, names)))
                messages++
            }
        }
        return ExportResult.Success(conversations, messages, users, destination)
    }

    private class NameIndex {
        val conversations = mutableMapOf<String, String>()
        val userNames = mutableMapOf<String, String>()
        val userHandles = mutableMapOf<String, String>()
    }

    private fun messageJson(
        message: BackupMessage,
        names: NameIndex? = null,
    ) = buildJsonObject {
        put("id", message.id)
        put("conversationId", message.conversationId.toString())
        put("senderId", message.senderUserId.toString())
        put("senderClientId", message.senderClientId)
        put("creationTime", message.creationDate.toLongMilliseconds())
        val conversationKey = message.conversationId.toString()
        val senderKey = message.senderUserId.toString()
        names?.conversations?.get(conversationKey)?.let { put("conversationName", it) }
        names?.userNames?.get(senderKey)?.let { put("senderName", it) }
        names?.userHandles?.get(senderKey)?.let { put("senderHandle", it) }
        when (val content = message.content) {
            is BackupMessageContent.Text -> {
                put("type", "text")
                put("content", content.text)
            }
            is BackupMessageContent.Asset -> {
                put("type", "asset")
                put("content", content.name ?: content.assetId)
            }
            is BackupMessageContent.Location -> {
                put("type", "location")
                put("content", content.name ?: "${content.latitude},${content.longitude}")
            }
        }
    }
}

package wirecli.exporting

import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import wirecli.importing.ImportSource
import java.nio.file.Files
import java.nio.file.Path

sealed interface ExportInput {
    data class ExternalBackup(val path: Path) : ExportInput

    data object LocalCache : ExportInput
}

sealed interface LocalCacheBackupResult {
    data class Success(val path: Path) : LocalCacheBackupResult

    data class Failure(val message: String) : LocalCacheBackupResult
}

interface LocalCacheBackupRuntime {
    fun create(
        session: AuthSession,
        password: String?,
    ): LocalCacheBackupResult
}

sealed interface LocalBackupResult {
    data class Success(val destination: Path) : LocalBackupResult

    data class Failure(val message: String, val exitCode: Int = 1) : LocalBackupResult
}

interface LocalBackupService {
    fun create(
        destination: Path,
        password: String?,
    ): LocalBackupResult
}

class DefaultLocalBackupService(
    private val sessionProvider: SessionProvider,
    private val runtime: LocalCacheBackupRuntime,
) : LocalBackupService {
    override fun create(
        destination: Path,
        password: String?,
    ): LocalBackupResult {
        val session =
            sessionProvider.readActiveSession()
                ?: return LocalBackupResult.Failure(
                    "No active session. Please login with 'wire auth login'.",
                    ExitCodes.UNAUTHORIZED,
                )
        return when (val backup = runtime.create(session, password)) {
            is LocalCacheBackupResult.Failure -> LocalBackupResult.Failure(backup.message)
            is LocalCacheBackupResult.Success ->
                runCatching {
                    destination.parent?.let(Files::createDirectories)
                    Files.move(backup.path, destination)
                    LocalBackupResult.Success(destination)
                }.getOrElse {
                    Files.deleteIfExists(backup.path)
                    LocalBackupResult.Failure("failed to save backup: ${it.message}")
                }
        }
    }
}

sealed interface ExportResult {
    data class Success(
        val conversations: Int,
        val messages: Int,
        val users: Int,
        val destination: Path,
    ) : ExportResult

    data class Failure(val message: String, val exitCode: Int = 1) : ExportResult
}

/**
 * Tuning for [ExportService]/[Exporter] exports.
 *
 * [includeNames] resolves the UUIDs in messages.jsonl against the conversation/user pages
 * so each message also carries conversationName, senderName and senderHandle.
 */
data class ExportOptions(
    val includeNames: Boolean = false,
) {
    companion object {
        val DEFAULT = ExportOptions()
    }
}

interface Exporter {
    val source: ImportSource

    fun export(
        input: Path,
        destination: Path,
        password: String?,
        options: ExportOptions = ExportOptions.DEFAULT,
    ): ExportResult
}

interface ExportService {
    fun export(
        input: ExportInput,
        source: ImportSource,
        destination: Path,
        password: String?,
        options: ExportOptions = ExportOptions.DEFAULT,
    ): ExportResult
}

class DefaultExportService(
    private val sessionProvider: SessionProvider,
    private val localCacheBackupRuntime: LocalCacheBackupRuntime,
    private val exporters: List<Exporter>,
) : ExportService {
    override fun export(
        input: ExportInput,
        source: ImportSource,
        destination: Path,
        password: String?,
        options: ExportOptions,
    ): ExportResult {
        val exporter =
            exporters.firstOrNull { it.source == source }
                ?: return ExportResult.Failure("unsupported export source: ${source.cliName}")

        if (input is ExportInput.ExternalBackup) return exporter.export(input.path, destination, password, options)

        val session =
            sessionProvider.readActiveSession()
                ?: return ExportResult.Failure(
                    "No active session. Please login with 'wire auth login'.",
                    ExitCodes.UNAUTHORIZED,
                )
        return when (val backup = localCacheBackupRuntime.create(session, null)) {
            is LocalCacheBackupResult.Failure -> ExportResult.Failure(backup.message)
            is LocalCacheBackupResult.Success -> {
                try {
                    exporter.export(backup.path, destination, null, options)
                } finally {
                    Files.deleteIfExists(backup.path)
                }
            }
        }
    }
}

package wirecli.importing

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import java.nio.file.Path

enum class ImportSource(val cliName: String) {
    WIRE_BACKUP("wire-backup"),
    ;

    companion object {
        fun fromCliName(value: String): ImportSource? = entries.firstOrNull { it.cliName == value }
    }
}

sealed interface ImportResult {
    data object Success : ImportResult

    data class Failure(val message: String, val exitCode: Int = ExitCodes.UNKNOWN_ERROR) : ImportResult
}

interface Importer {
    val source: ImportSource

    fun import(
        session: AuthSession,
        input: Path,
        password: String?,
    ): ImportResult
}

interface ImportService {
    fun import(
        input: Path,
        source: ImportSource,
        password: String?,
    ): ImportResult
}

class DefaultImportService(
    private val sessionProvider: SessionProvider,
    private val importers: List<Importer>,
) : ImportService {
    override fun import(
        input: Path,
        source: ImportSource,
        password: String?,
    ): ImportResult {
        val session =
            sessionProvider.readActiveSession()
                ?: return ImportResult.Failure(AuthMessages.noActiveSession(), ExitCodes.UNAUTHORIZED)
        val importer =
            importers.firstOrNull { it.source == source }
                ?: return ImportResult.Failure("unsupported import source: ${source.cliName}")
        return importer.import(session, input, password)
    }
}

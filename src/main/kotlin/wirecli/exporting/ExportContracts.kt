package wirecli.exporting

import wirecli.importing.ImportSource
import java.nio.file.Path

sealed interface ExportResult {
    data class Success(
        val conversations: Int,
        val messages: Int,
        val users: Int,
        val destination: Path,
    ) : ExportResult

    data class Failure(val message: String, val exitCode: Int = 1) : ExportResult
}

interface Exporter {
    val source: ImportSource

    fun export(
        input: Path,
        destination: Path,
        password: String?,
    ): ExportResult
}

interface ExportService {
    fun export(
        input: Path,
        source: ImportSource,
        destination: Path,
        password: String?,
    ): ExportResult
}

class DefaultExportService(private val exporters: List<Exporter>) : ExportService {
    override fun export(
        input: Path,
        source: ImportSource,
        destination: Path,
        password: String?,
    ): ExportResult {
        val exporter =
            exporters.firstOrNull { it.source == source }
                ?: return ExportResult.Failure("unsupported export source: ${source.cliName}")
        return exporter.export(input, destination, password)
    }
}

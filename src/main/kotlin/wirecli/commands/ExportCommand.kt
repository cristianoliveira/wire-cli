package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import wirecli.exporting.ExportInput
import wirecli.exporting.ExportOptions
import wirecli.exporting.ExportResult
import wirecli.exporting.ExportService
import wirecli.importing.ImportSource
import kotlin.io.path.Path

class ExportCommand(private val serviceProvider: () -> ExportService) : CliktCommand(
    name = "export",
    help = "Export Wire client data for analysis.",
) {
    private val input by argument("BACKUP", help = "Wire backup file; omit to export authenticated local cache").optional()
    private val sourceName by option("--from", help = "Source client format (default: wire-backup)")
        .default(ImportSource.WIRE_BACKUP.cliName)
    private val format by option("--format", help = "Output format (jsonl)").required()
    private val destination by option("--destination", help = "Output directory").required()
    private val password by option("--password")
    private val includeNames by option(
        "--include-names",
        help = "Resolve conversation and sender names in messages.jsonl",
    ).flag(default = false)

    override fun run() {
        val source = ImportSource.fromCliName(sourceName)
        if (source == null || format != "jsonl") {
            echo("unsupported export source or format", err = true)
            throw ProgramResult(1)
        }
        val exportInput = input?.let { ExportInput.ExternalBackup(Path(it)) } ?: ExportInput.LocalCache
        when (val result = serviceProvider().export(exportInput, source, Path(destination), password, ExportOptions(includeNames))) {
            is ExportResult.Success ->
                echo(
                    "Exported ${result.conversations} conversations, ${result.messages} messages, " +
                        "and ${result.users} users into ${result.destination}",
                )
            is ExportResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }
}

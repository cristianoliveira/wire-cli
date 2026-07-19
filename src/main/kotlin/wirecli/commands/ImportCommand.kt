package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import wirecli.importing.ImportResult
import wirecli.importing.ImportService
import wirecli.importing.ImportSource
import kotlin.io.path.Path

class ImportCommand(
    private val importServiceProvider: () -> ImportService,
) : CliktCommand(
        name = "import",
        help = "Import conversations and messages from another Wire client into the local cache.",
    ) {
    private val input by argument("BACKUP", help = "Path to source backup")

    private val sourceName by option(
        "--from",
        help = "Source client format (default: wire-backup)",
    ).default(ImportSource.WIRE_BACKUP.cliName)

    private val password by option(
        "--password",
        help = "Backup password when encrypted",
    )

    override fun run() {
        val source = ImportSource.fromCliName(sourceName)
        if (source == null) {
            echo("unsupported import source: $sourceName", err = true)
            throw ProgramResult(1)
        }

        when (val result = importServiceProvider().import(Path(input), source, password)) {
            ImportResult.Success -> echo("Imported backup into local cache")
            is ImportResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }
}

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import wirecli.exporting.ExportService
import wirecli.importing.ImportService

class BackupCommand(
    importServiceProvider: () -> ImportService,
    exportServiceProvider: () -> ExportService,
) : CliktCommand(
        name = "backup",
        help = "Import, export, and inspect Wire backups.",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            ImportCommand(importServiceProvider),
            ExportCommand(exportServiceProvider),
        )
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            echo("No subcommand specified. Use 'wire backup --help' for available commands.")
            throw ProgramResult(0)
        }
    }
}

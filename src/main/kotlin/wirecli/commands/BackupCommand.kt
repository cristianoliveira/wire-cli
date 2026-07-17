package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import wirecli.exporting.ExportService
import wirecli.exporting.LocalBackupService
import wirecli.importing.ImportService

class BackupCommand(
    importServiceProvider: () -> ImportService,
    exportServiceProvider: () -> ExportService,
    localBackupServiceProvider: () -> LocalBackupService,
) : CliktCommand(
        name = "backup",
        help = "Import, export, and inspect Wire backups.",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            ImportCommand(importServiceProvider),
            ExportCommand(exportServiceProvider),
            CreateBackupCommand(localBackupServiceProvider),
        )
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            failWithUsage()
        }
    }
}

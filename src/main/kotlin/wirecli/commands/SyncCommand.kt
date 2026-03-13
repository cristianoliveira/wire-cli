package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.sync.DiagnosticsReport
import wirecli.sync.DiagnosticsResult
import wirecli.sync.SyncExitCodes
import wirecli.sync.SyncOutputFormatter
import wirecli.sync.SyncService
import wirecli.sync.SyncStatusResult

class SyncCommand(
    private val syncServiceProvider: () -> SyncService,
) : CliktCommand(
        name = "sync",
        help = "Check sync status and diagnostics.",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            SyncStatusCommand(syncServiceProvider),
        )
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            val syncService = syncServiceProvider()
            outputSyncStatusResult(syncService.getCurrentSyncStatus())
        }
    }

    private fun outputSyncStatusResult(result: SyncStatusResult) {
        when (result) {
            is SyncStatusResult.Success -> {
                echo(SyncOutputFormatter.formatStatusHuman(result))
                throw ProgramResult(getExitCodeForStatus(result.view.status.value))
            }
            is SyncStatusResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun getExitCodeForStatus(status: String): Int =
        when (status) {
            "ready" -> SyncExitCodes.OK
            "initializing", "degraded" -> SyncExitCodes.DEGRADED
            "error" -> SyncExitCodes.DEGRADED
            else -> SyncExitCodes.OK
        }
}

private class SyncStatusCommand(
    private val syncServiceProvider: () -> SyncService,
) : CliktCommand(
        name = "status",
        help = "Get current sync status.",
    ) {
    private val verbose: Boolean by option(
        "--verbose",
        "-v",
        help = "Show detailed metrics (lag, pending, MLS%, key packages).",
    ).flag(default = false)
    private val diagnose: Boolean by option(
        "--diagnose",
        "-d",
        help = "Run diagnostic checks with recovery hints.",
    ).flag(default = false)
    private val json: Boolean by option(
        "--json",
        help = "Output as valid JSON.",
    ).flag(default = false)

    override fun run() {
        val syncService = syncServiceProvider()

        when {
            diagnose -> runDiagnostics(syncService)
            else -> runStatus(syncService)
        }
    }

    private fun runStatus(syncService: SyncService) {
        val result = syncService.getCurrentSyncStatus()

        val output =
            when {
                json -> SyncOutputFormatter.formatStatusJson(result)
                verbose -> SyncOutputFormatter.formatStatusVerbose(result)
                else -> SyncOutputFormatter.formatStatusHuman(result)
            }

        when (result) {
            is SyncStatusResult.Success -> {
                echo(output)
                throw ProgramResult(getExitCodeForStatus(result.view.status.value))
            }
            is SyncStatusResult.Failure -> {
                echo(AuthRedactor.redact(output), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun runDiagnostics(syncService: SyncService) {
        val result = syncService.getDiagnosticsReport()

        val output =
            if (json) {
                SyncOutputFormatter.formatDiagnosticsJson(result)
            } else {
                SyncOutputFormatter.formatDiagnosticsHuman(result)
            }

        when (result) {
            is DiagnosticsResult.Success -> {
                echo(output)
                throw ProgramResult(getExitCodeForDiagnosticsReport(result.report))
            }
            is DiagnosticsResult.Failure -> {
                echo(AuthRedactor.redact(output), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun getExitCodeForStatus(status: String): Int =
        when (status) {
            "ready" -> SyncExitCodes.OK
            "initializing", "degraded", "error" -> SyncExitCodes.DEGRADED
            else -> SyncExitCodes.OK
        }

    private fun getExitCodeForDiagnosticsReport(report: DiagnosticsReport): Int {
        // Check if any check is in error state
        val hasErrors = report.checks.any { it.status == "error" }
        return if (hasErrors) SyncExitCodes.DEGRADED else SyncExitCodes.OK
    }
}

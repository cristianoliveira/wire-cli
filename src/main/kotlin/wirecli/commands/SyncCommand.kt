package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import wirecli.auth.AuthRedactor
import wirecli.sync.DiagnosticsResult
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
            SyncDiagnosticsCommand(syncServiceProvider),
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
                echo("Status: ${result.view.status}")
                echo("Lag: ${result.view.metrics.lag_ms}ms")
                echo("Pending: ${result.view.metrics.pending_messages}")
                echo("MLS: ${result.view.metrics.mls_pct}%")
            }
            is SyncStatusResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

private class SyncStatusCommand(
    private val syncServiceProvider: () -> SyncService,
) : CliktCommand(name = "status", help = "Get current sync status.") {
    override fun run() {
        val syncService = syncServiceProvider()
        when (val result = syncService.getCurrentSyncStatus()) {
            is SyncStatusResult.Success -> {
                echo("Status: ${result.view.status}")
                echo("Lag: ${result.view.metrics.lag_ms}ms")
                echo("Pending: ${result.view.metrics.pending_messages}")
                echo("MLS: ${result.view.metrics.mls_pct}%")
                echo("Timestamp: ${result.view.metrics.timestamp}")
            }
            is SyncStatusResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

private class SyncDiagnosticsCommand(
    private val syncServiceProvider: () -> SyncService,
) : CliktCommand(name = "diagnostics", help = "Get sync diagnostics report.") {
    override fun run() {
        val syncService = syncServiceProvider()
        when (val result = syncService.getDiagnosticsReport()) {
            is DiagnosticsResult.Success -> {
                echo("Diagnostics Summary: ${result.report.summary}")
                echo("")
                echo("Checks:")
                result.report.checks.forEach { check ->
                    echo("  - ${check.name}: ${check.status}")
                    echo("    ${check.details}")
                }
            }
            is DiagnosticsResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

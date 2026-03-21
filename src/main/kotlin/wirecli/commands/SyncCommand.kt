package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.auth.ExitCodes
import wirecli.sync.DiagnosticsReport
import wirecli.sync.DiagnosticsResult
import wirecli.sync.SyncExitCodes
import wirecli.sync.SyncOutputFormatter
import wirecli.sync.SyncService
import wirecli.sync.SyncStatusResult
import wirecli.validation.InputValidator

class SyncCommand(
    private val syncServiceProvider: () -> SyncService,
) : CliktCommand(
        name = "doctor",
        help = "Check account health and sync status.",
        invokeWithoutSubcommand = true,
    ) {
    private val deviceId: String? by option("--device-id", help = "Optional device ID context")
    private val conversationId: String? by option("--conversation-id", help = "Optional conversation ID (UUID) context")

    init {
        subcommands(
            SyncStatusCommand(syncServiceProvider),
            DoctorDiagnoseCommand(syncServiceProvider),
            DoctorSyncCommand(syncServiceProvider),
        )
    }

    override fun run() {
        validateSyncContextOrExit(deviceId, conversationId)

        if (currentContext.invokedSubcommand == null) {
            val syncService = syncServiceProvider()
            outputSyncStatusResult(
                runWithLoading("Collecting account health") {
                    syncService.getCurrentSyncStatus()
                },
            )
        }
    }

    private fun <T> runWithLoading(
        message: String,
        block: () -> T,
    ): T {
        if (System.console() == null) return block()

        val startedAt = System.currentTimeMillis()
        echo("$message...", err = true)
        return try {
            block()
        } finally {
            val elapsed = System.currentTimeMillis() - startedAt
            echo("Done (${elapsed}ms)", err = true)
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

private class DoctorSyncCommand(
    private val syncServiceProvider: () -> SyncService,
) : CliktCommand(
        name = "sync",
        help = "Force sync and wait until live state.",
    ) {
    private val deviceId: String? by option("--device-id", help = "Optional device ID context")
    private val conversationId: String? by option("--conversation-id", help = "Optional conversation ID (UUID) context")

    override fun run() {
        validateSyncContextOrExit(deviceId, conversationId)

        val syncService = syncServiceProvider()
        val result =
            runWithLoading("Forcing sync and waiting for live state") {
                syncService.forceSyncAndWait()
            }

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
            "initializing", "degraded", "error" -> SyncExitCodes.DEGRADED
            else -> SyncExitCodes.OK
        }

    private fun <T> runWithLoading(
        message: String,
        block: () -> T,
    ): T {
        if (System.console() == null) return block()

        val startedAt = System.currentTimeMillis()
        echo("$message...", err = true)
        return try {
            block()
        } finally {
            val elapsed = System.currentTimeMillis() - startedAt
            echo("Done (${elapsed}ms)", err = true)
        }
    }
}

private class SyncStatusCommand(
    private val syncServiceProvider: () -> SyncService,
) : CliktCommand(
        name = "status",
        help = "Get current sync status.",
    ) {
    private val deviceId: String? by option("--device-id", help = "Optional device ID context")
    private val conversationId: String? by option("--conversation-id", help = "Optional conversation ID (UUID) context")
    private val verbose: Boolean by option(
        "--verbose",
        "-v",
        help = "Show detailed metrics (lag, pending, MLS%, key packages).",
    ).flag(default = false)
    private val json: Boolean by option(
        "--json",
        help = "Output as valid JSON.",
    ).flag(default = false)
    private val diagnose: Boolean by option(
        "--diagnose",
        help = "Run diagnostic checks with recovery hints.",
    ).flag(default = false)

    override fun run() {
        validateSyncContextOrExit(deviceId, conversationId)

        val syncService = syncServiceProvider()
        if (diagnose) {
            runDiagnose(syncService)
        } else {
            runStatus(syncService)
        }
    }

    private fun runStatus(syncService: SyncService) {
        val result =
            runWithLoading("Collecting account health") {
                syncService.getCurrentSyncStatus()
            }

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

    private fun runDiagnose(syncService: SyncService) {
        val result =
            runWithLoading("Running diagnostics") {
                syncService.getDiagnosticsReport()
            }

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
        // Check if any check is in error/fail state (degraded is not an error)
        val hasErrors = report.checks.any { it.status in listOf("error", "fail") }
        return if (hasErrors) SyncExitCodes.DEGRADED else SyncExitCodes.OK
    }

    private fun <T> runWithLoading(
        message: String,
        block: () -> T,
    ): T {
        if (System.console() == null || json) return block()

        val startedAt = System.currentTimeMillis()
        echo("$message...", err = true)
        return try {
            block()
        } finally {
            val elapsed = System.currentTimeMillis() - startedAt
            echo("Done (${elapsed}ms)", err = true)
        }
    }
}

private class DoctorDiagnoseCommand(
    private val syncServiceProvider: () -> SyncService,
) : CliktCommand(
        name = "diagnose",
        help = "Run diagnostic checks with recovery hints.",
    ) {
    private val deviceId: String? by option("--device-id", help = "Optional device ID context")
    private val conversationId: String? by option("--conversation-id", help = "Optional conversation ID (UUID) context")
    private val verbose: Boolean by option(
        "--verbose",
        "-v",
        help = "Show detailed check information.",
    ).flag(default = false)
    private val json: Boolean by option(
        "--json",
        help = "Output as valid JSON.",
    ).flag(default = false)

    override fun run() {
        validateSyncContextOrExit(deviceId, conversationId)

        val syncService = syncServiceProvider()
        val result =
            runWithLoading("Running diagnostics") {
                syncService.getDiagnosticsReport()
            }

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

    private fun getExitCodeForDiagnosticsReport(report: DiagnosticsReport): Int {
        // Check if any check is in error/fail state (degraded is not an error)
        val hasErrors = report.checks.any { it.status in listOf("error", "fail") }
        return if (hasErrors) SyncExitCodes.DEGRADED else SyncExitCodes.OK
    }

    private fun <T> runWithLoading(
        message: String,
        block: () -> T,
    ): T {
        if (System.console() == null || json) return block()

        val startedAt = System.currentTimeMillis()
        echo("$message...", err = true)
        return try {
            block()
        } finally {
            val elapsed = System.currentTimeMillis() - startedAt
            echo("Done (${elapsed}ms)", err = true)
        }
    }
}

private fun CliktCommand.validateSyncContextOrExit(
    deviceId: String?,
    conversationId: String?,
) {
    try {
        if (deviceId != null) {
            InputValidator.validateDeviceId(deviceId)
        }
        if (conversationId != null) {
            InputValidator.validateConversationId(conversationId)
        }
    } catch (error: IllegalArgumentException) {
        echo(error.message ?: "Invalid input.", err = true)
        throw ProgramResult(ExitCodes.VALIDATION_ERROR)
    }
}

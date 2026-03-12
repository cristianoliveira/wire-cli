package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import wirecli.auth.ExitCodes
import wirecli.presence.PresenceResult
import wirecli.presence.PresenceService
import wirecli.presence.PresenceStatusContract

class PresenceCommand(
    private val presenceService: PresenceService,
) : CliktCommand(
        name = "presence",
        help = "Get or set current user presence.",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            PresenceGetCommand(presenceService),
            PresenceSetCommand(presenceService),
        )
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            outputPresenceResult(presenceService.getCurrentPresence())
        }
    }

    private fun outputPresenceResult(result: PresenceResult) {
        when (result) {
            is PresenceResult.Success -> echo(result.presence.state.value)
            is PresenceResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

private class PresenceGetCommand(
    private val presenceService: PresenceService,
) : CliktCommand(name = "get", help = "Get current user presence.") {
    override fun run() {
        when (val result = presenceService.getCurrentPresence()) {
            is PresenceResult.Success -> echo(result.presence.state.value)
            is PresenceResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

private class PresenceSetCommand(
    private val presenceService: PresenceService,
) : CliktCommand(name = "set", help = "Set current user presence.") {
    private val status by argument(name = "status", help = "online|busy|away|offline")

    override fun run() {
        val writableState = PresenceStatusContract.parseWritable(status)
        if (writableState == null) {
            echo(
                "Invalid status '$status'. Allowed values: online, busy, away, offline.",
                err = true,
            )
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }

        when (val result = presenceService.setCurrentPresence(writableState)) {
            is PresenceResult.Success -> echo(result.presence.state.value)
            is PresenceResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthRedactor
import wirecli.auth.ExitCodes
import wirecli.presence.PresenceResult
import wirecli.presence.PresenceService
import wirecli.presence.PresenceStatusContract

private val logger = KotlinLogging.logger {}

class PresenceCommand(
    private val presenceServiceProvider: () -> PresenceService,
) : CliktCommand(
        name = "presence",
        help = "Get or set current user presence.",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            PresenceGetCommand(presenceServiceProvider),
            PresenceSetCommand(presenceServiceProvider),
        )
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            logger.info { "Presence command started (get)" }
            val presenceService = presenceServiceProvider()
            outputPresenceResult(presenceService.getCurrentPresence())
        }
    }

    private fun outputPresenceResult(result: PresenceResult) {
        when (result) {
            is PresenceResult.Success -> {
                logger.info { "Presence retrieved successfully: ${result.presence.state}" }
                echo(result.presence.state.value)
            }
            is PresenceResult.Failure -> {
                logger.warn { "Failed to retrieve presence: ${AuthRedactor.redact(result.message)}" }
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

private class PresenceGetCommand(
    private val presenceServiceProvider: () -> PresenceService,
) : CliktCommand(name = "get", help = "Get current user presence.") {
    override fun run() {
        logger.info { "Presence get command started" }
        val presenceService = presenceServiceProvider()
        when (val result = presenceService.getCurrentPresence()) {
            is PresenceResult.Success -> {
                logger.info { "Presence retrieved successfully: ${result.presence.state}" }
                echo(result.presence.state.value)
            }
            is PresenceResult.Failure -> {
                logger.warn { "Failed to retrieve presence: ${AuthRedactor.redact(result.message)}" }
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

private class PresenceSetCommand(
    private val presenceServiceProvider: () -> PresenceService,
) : CliktCommand(name = "set", help = "Set current user presence.") {
    private val status by argument(name = "status", help = "online|busy|away|offline")

    override fun run() {
        logger.info { "Presence set command started: status=$status" }
        val presenceService = presenceServiceProvider()
        val writableState = PresenceStatusContract.parseWritable(status)
        if (writableState == null) {
            logger.warn { "Invalid presence status requested: $status" }
            echo(
                "Invalid status '$status'. Allowed values: online, busy, away, offline.",
                err = true,
            )
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }

        when (val result = presenceService.setCurrentPresence(writableState)) {
            is PresenceResult.Success -> {
                logger.info { "Presence set successfully: ${result.presence.state}" }
                echo(result.presence.state.value)
            }
            is PresenceResult.Failure -> {
                logger.warn { "Failed to set presence: ${AuthRedactor.redact(result.message)}" }
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

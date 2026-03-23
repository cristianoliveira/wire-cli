package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
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
    private val conversationId by option("--conversation-id", help = "Optional conversation ID (UUID)")

    init {
        subcommands(
            PresenceGetCommand(presenceServiceProvider),
            PresenceSetCommand(presenceServiceProvider),
        )
    }

    override fun run() {
        if (conversationId != null) {
            validateConversationIdOrExit(conversationId!!)
        }

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
                logger.warn { "Failed to set presence: ${AuthRedactor.redact(result.error.message)}" }
                echo(AuthRedactor.redact(result.error.message), err = true)
                throw ProgramResult(result.error.exitCode)
            }
        }
    }
}

// Uses validateConversationIdOrExit from DeviceCommand.kt (same package)

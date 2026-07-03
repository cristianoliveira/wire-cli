package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthRedactor
import wirecli.auth.ExitCodes
import wirecli.connection.ConnectionActionResult
import wirecli.connection.ConnectionService

private val logger = KotlinLogging.logger {}

class ConnectionCommand(
    private val connectionServiceProvider: () -> ConnectionService,
) : CliktCommand(
        name = "connection",
        help = "Manage connection lifecycle (request, block, unblock).",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            ConnectionRequestCommand(connectionServiceProvider),
            ConnectionBlockCommand(connectionServiceProvider),
            ConnectionUnblockCommand(connectionServiceProvider),
        )
    }

    override fun run() {
        logger.debug { "Current subcommand: ${currentContext.invokedSubcommand}" }
        if (currentContext.invokedSubcommand == null) {
            echo("No subcommand specified. Use 'wire connection --help' for available commands.")
            throw ProgramResult(0)
        }
    }
}

class ConnectionRequestCommand(
    private val connectionServiceProvider: () -> ConnectionService,
) : CliktCommand(
        name = "request",
        help = "Send a connection request to a user by qualified ID (value@domain).",
    ) {
    private val userId by argument(name = "user-id", help = "Qualified user ID (value@domain)")
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val validatedUserId = validateUserIdOrExit(userId)
        val result = connectionServiceProvider().sendRequest(validatedUserId)
        emitActionResult(result, json)
    }
}

class ConnectionBlockCommand(
    private val connectionServiceProvider: () -> ConnectionService,
) : CliktCommand(
        name = "block",
        help = "Block a user. Requires --yes to confirm this destructive action.",
    ) {
    private val userId by argument(name = "user-id", help = "Qualified user ID (value@domain)")
    private val yes by option("--yes", "-y", help = "Confirm the block action").flag(default = false)
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        if (!yes) {
            echo("Blocking a user is destructive. Re-run with --yes to confirm.", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }
        val validatedUserId = validateUserIdOrExit(userId)
        val result = connectionServiceProvider().blockUser(validatedUserId)
        emitActionResult(result, json)
    }
}

class ConnectionUnblockCommand(
    private val connectionServiceProvider: () -> ConnectionService,
) : CliktCommand(
        name = "unblock",
        help = "Unblock a previously blocked user by qualified ID (value@domain).",
    ) {
    private val userId by argument(name = "user-id", help = "Qualified user ID (value@domain)")
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val validatedUserId = validateUserIdOrExit(userId)
        val result = connectionServiceProvider().unblockUser(validatedUserId)
        emitActionResult(result, json)
    }
}

private fun CliktCommand.emitActionResult(
    result: ConnectionActionResult,
    json: Boolean,
) {
    when (result) {
        is ConnectionActionResult.Success -> {
            if (json) {
                echo("""{"ok":true,"message":"${escapeJson(result.message)}"}""")
            } else {
                echo(result.message)
            }
        }

        is ConnectionActionResult.Failure -> {
            echo(AuthRedactor.redact(result.message), err = true)
            throw ProgramResult(result.exitCode)
        }
    }
}

internal fun escapeJson(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

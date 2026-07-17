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
import wirecli.connection.ConnectionListResult
import wirecli.connection.ConnectionService
import wirecli.connection.ConnectionView

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
            ConnectionAcceptCommand(connectionServiceProvider),
            ConnectionIgnoreCommand(connectionServiceProvider),
            ConnectionCancelCommand(connectionServiceProvider),
            ConnectionBlockCommand(connectionServiceProvider),
            ConnectionUnblockCommand(connectionServiceProvider),
            ConnectionListCommand(connectionServiceProvider),
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

class ConnectionAcceptCommand(
    private val connectionServiceProvider: () -> ConnectionService,
) : CliktCommand(
        name = "accept",
        help = "Accept a connection request from a user by qualified ID (value@domain).",
    ) {
    private val userId by argument(name = "user-id", help = "Qualified user ID (value@domain)")
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val validatedUserId = validateUserIdOrExit(userId)
        val result = connectionServiceProvider().acceptRequest(validatedUserId)
        emitActionResult(result, json)
    }
}

class ConnectionIgnoreCommand(
    private val connectionServiceProvider: () -> ConnectionService,
) : CliktCommand(
        name = "ignore",
        help = "Ignore a connection request from a user by qualified ID (value@domain).",
    ) {
    private val userId by argument(name = "user-id", help = "Qualified user ID (value@domain)")
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val validatedUserId = validateUserIdOrExit(userId)
        val result = connectionServiceProvider().ignoreRequest(validatedUserId)
        emitActionResult(result, json)
    }
}

class ConnectionCancelCommand(
    private val connectionServiceProvider: () -> ConnectionService,
) : CliktCommand(
        name = "cancel",
        help = "Cancel a connection request sent to a user by qualified ID (value@domain).",
    ) {
    private val userId by argument(name = "user-id", help = "Qualified user ID (value@domain)")
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val validatedUserId = validateUserIdOrExit(userId)
        val result = connectionServiceProvider().cancelRequest(validatedUserId)
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

class ConnectionListCommand(
    private val connectionServiceProvider: () -> ConnectionService,
) : CliktCommand(
        name = "list",
        help = "List all connections.",
    ) {
    private val json by option("--json", help = "Output as JSON").flag(default = false)
    private val jsonLines by option("--json-lines", help = "Output as JSON lines").flag(default = false)

    override fun run() {
        val connectionService = connectionServiceProvider()

        when (val result = connectionService.listConnections()) {
            is ConnectionListResult.Success -> {
                val connections = result.view.connections
                val output =
                    when {
                        jsonLines -> toJsonLines(connections)
                        json -> toJson(connections)
                        else -> toTable(connections)
                    }
                echo(output)
            }

            is ConnectionListResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun toTable(connections: List<ConnectionView>): String {
        if (connections.isEmpty()) return "No connections found."

        val header = "%-42s %-20s %-22s %-14s".format("USER ID", "NAME", "HANDLE", "STATUS")
        val rows =
            connections.joinToString("\n") { conn ->
                "%-42s %-20s %-22s %-14s".format(
                    conn.userId,
                    conn.userName ?: "-",
                    conn.handle ?: "-",
                    conn.status.value,
                )
            }
        return "$header\n$rows"
    }

    private fun toJson(connections: List<ConnectionView>): String {
        val items =
            connections.joinToString(",") { conn ->
                """{"userId":"${escapeJson(
                    conn.userId,
                )}","userName":"${escapeJson(
                    conn.userName ?: "",
                )}","handle":"${escapeJson(conn.handle ?: "")}","status":"${conn.status.value}"}"""
            }
        return """{"schemaVersion":1,"connections":[$items]}"""
    }

    private fun toJsonLines(connections: List<ConnectionView>): String {
        return connections.joinToString("\n") { conn ->
            """{"userId":"${escapeJson(
                conn.userId,
            )}","userName":"${escapeJson(
                conn.userName ?: "",
            )}","handle":"${escapeJson(conn.handle ?: "")}","status":"${conn.status.value}"}"""
        }
    }
}

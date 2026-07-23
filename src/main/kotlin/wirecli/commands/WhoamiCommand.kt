package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AccountService
import wirecli.auth.ExitCodes

private val logger = KotlinLogging.logger {}

class WhoamiCommand(
    private val accountServiceProvider: () -> AccountService,
) : CliktCommand(name = "whoami", help = "Show the currently active account.") {
    override fun run() {
        logger.info { "Whoami command executed" }
        val current = accountServiceProvider().currentAccount()
        if (current == null) {
            echo("No active account. Run `wire account use <user-id>` or `wire login`.", err = true)
            throw ProgramResult(processExitCode(ExitCodes.UNAUTHORIZED))
        }
        val server = current.server?.let { "  ($it)" }.orEmpty()
        val identity = if (current.label != null) "${current.label}  ${current.userId}" else current.userId
        echo("$identity$server")
    }
}

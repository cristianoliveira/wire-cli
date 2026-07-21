package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AccountsService
import wirecli.auth.ExitCodes

private val logger = KotlinLogging.logger {}

class WhoamiCommand(
    private val accountsServiceProvider: () -> AccountsService,
) : CliktCommand(name = "whoami", help = "Show the currently active account.") {
    override fun run() {
        logger.info { "Whoami command executed" }
        val current = accountsServiceProvider().currentAccount()
        if (current == null) {
            echo("No active account. Run `wire accounts use <user-id>` or `wire login`.", err = true)
            throw ProgramResult(processExitCode(ExitCodes.UNAUTHORIZED))
        }
        val server = current.server?.let { "  ($it)" }.orEmpty()
        echo("${current.userId}$server")
    }
}

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AccountsService
import wirecli.auth.ExitCodes

private val logger = KotlinLogging.logger {}

class AccountsCommand(
    private val accountsServiceProvider: () -> AccountsService,
) : CliktCommand(
        name = "accounts",
        help = "Manage stored accounts (list, use, remove).",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            AccountsListCommand(accountsServiceProvider),
            AccountsUseCommand(accountsServiceProvider),
            AccountsRemoveCommand(accountsServiceProvider),
        )
    }

    override fun run() {
        logger.info { "Accounts command executed" }
        if (currentContext.invokedSubcommand == null) {
            failWithUsage()
        }
    }
}

class AccountsListCommand(
    private val accountsServiceProvider: () -> AccountsService,
) : CliktCommand(name = "list", help = "List stored accounts and mark the active one.") {
    override fun run() {
        val listing = accountsServiceProvider().listAccounts()
        if (listing.accounts.isEmpty()) {
            echo("No stored accounts. Run `wire login` to add one.")
            return
        }
        listing.accounts.forEach { account ->
            val marker = if (account.userId == listing.activeUserId) "*" else " "
            val server = account.server?.let { "  ($it)" }.orEmpty()
            echo("$marker ${account.userId}$server")
        }
    }
}

class AccountsUseCommand(
    private val accountsServiceProvider: () -> AccountsService,
) : CliktCommand(name = "use", help = "Switch the active account (local only).") {
    private val userId by argument("user-id", help = "Qualified user id (value@domain) of the account to activate.")

    override fun run() {
        val account = accountsServiceProvider().useAccount(userId)
        if (account == null) {
            echo("No stored account for '$userId'. Run `wire accounts list`.", err = true)
            throw ProgramResult(processExitCode(ExitCodes.VALIDATION_ERROR))
        }
        echo("Switched to ${account.userId}.")
    }
}

class AccountsRemoveCommand(
    private val accountsServiceProvider: () -> AccountsService,
) : CliktCommand(
        name = "remove",
        help = "Remove a stored account (local only; use `wire logout` for server logout).",
    ) {
    private val userId by argument("user-id", help = "Qualified user id (value@domain) of the account to remove.")

    override fun run() {
        val account = accountsServiceProvider().removeAccount(userId)
        if (account == null) {
            echo("No stored account for '$userId'. Run `wire accounts list`.", err = true)
            throw ProgramResult(processExitCode(ExitCodes.VALIDATION_ERROR))
        }
        echo("Removed ${account.userId}.")
    }
}

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AccountService
import wirecli.auth.ExitCodes

private val logger = KotlinLogging.logger {}

class AccountCommand(
    private val accountServiceProvider: () -> AccountService,
) : CliktCommand(
        name = "account",
        help = "Manage stored accounts (list, use, remove).",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            AccountListCommand(accountServiceProvider),
            AccountUseCommand(accountServiceProvider),
            AccountRemoveCommand(accountServiceProvider),
        )
    }

    override fun run() {
        logger.info { "Account command executed" }
        if (currentContext.invokedSubcommand == null) {
            failWithUsage()
        }
    }
}

class AccountListCommand(
    private val accountServiceProvider: () -> AccountService,
) : CliktCommand(name = "list", help = "List stored accounts and mark the active one.") {
    override fun run() {
        val listing = accountServiceProvider().listAccounts()
        if (listing.accounts.isEmpty()) {
            echo("No stored accounts. Run `wire login` to add one.")
            return
        }
        listing.accounts.forEach { account ->
            val marker = if (account.userId == listing.activeUserId) "*" else " "
            val server = account.server?.let { "  ($it)" }.orEmpty()
            val identity = if (account.label != null) "${account.label}  ${account.userId}" else account.userId
            echo("$marker $identity$server")
        }
    }
}

class AccountUseCommand(
    private val accountServiceProvider: () -> AccountService,
) : CliktCommand(name = "use", help = "Switch the active account (local only).") {
    private val selector by argument("selector", help = "Label or qualified user id (value@domain) of the account to activate.")

    override fun run() {
        val account = accountServiceProvider().useAccount(selector)
        if (account == null) {
            echo("No stored account for '$selector'. Run `wire account list`.", err = true)
            throw ProgramResult(processExitCode(ExitCodes.VALIDATION_ERROR))
        }
        echo("Switched to ${describeAccount(account)}.")
    }
}

class AccountRemoveCommand(
    private val accountServiceProvider: () -> AccountService,
) : CliktCommand(
        name = "remove",
        help = "Remove a stored account (local only; use `wire logout` for server logout).",
    ) {
    private val selector by argument("selector", help = "Label or qualified user id (value@domain) of the account to remove.")

    override fun run() {
        val account = accountServiceProvider().removeAccount(selector)
        if (account == null) {
            echo("No stored account for '$selector'. Run `wire account list`.", err = true)
            throw ProgramResult(processExitCode(ExitCodes.VALIDATION_ERROR))
        }
        echo("Removed ${describeAccount(account)}.")
    }
}

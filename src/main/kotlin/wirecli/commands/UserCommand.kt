package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthRedactor
import wirecli.user.UserFormatter
import wirecli.user.UserGetResult
import wirecli.user.UserSearchQuery
import wirecli.user.UserSearchResult
import wirecli.user.UserService

private val logger = KotlinLogging.logger {}

class UserCommand(
    private val userServiceProvider: () -> UserService,
) : CliktCommand(
        name = "user",
        help = "Discover users (search, get).",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            UserSearchCommand(userServiceProvider),
            UserGetCommand(userServiceProvider),
        )
    }

    override fun run() {
        logger.debug { "Current subcommand: ${currentContext.invokedSubcommand}" }
        if (currentContext.invokedSubcommand == null) failWithUsage()
    }
}

class UserSearchCommand(
    private val userServiceProvider: () -> UserService,
) : CliktCommand(name = "search", help = "Search users by name, handle, or email.") {
    private val query by argument(name = "query", help = "Search query (name, handle, or email)")
    private val limit by option("--limit", help = "Maximum number of results (1-100)").int()
    private val contactsOnly by option("--contacts-only", help = "Search connected contacts only").flag(default = false)
    private val json by option("--json", help = "Output as JSON").flag(default = false)
    private val jsonLines by option("--json-lines", help = "Output as JSON lines").flag(default = false)

    private val formatter = UserFormatter()

    override fun run() {
        validateStructuredOutputOrExit(json, jsonLines)
        val validatedQuery =
            validateOrExit {
                UserSearchQuery(
                    query = query,
                    limit = limit ?: UserSearchQuery.DEFAULT_LIMIT,
                    contactsOnly = contactsOnly,
                )
            }
        val userService = userServiceProvider()

        when (val result = userService.search(validatedQuery)) {
            is UserSearchResult.Success -> {
                val users = result.view.users
                when {
                    jsonLines -> echo(formatter.toJsonLines(users))
                    json -> echo(formatter.toJson(users))
                    else -> echo(formatter.toTable(users))
                }
            }

            is UserSearchResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }
}

class UserGetCommand(
    private val userServiceProvider: () -> UserService,
) : CliktCommand(name = "get", help = "Show details for a single user by qualified ID (value@domain).") {
    private val userId by argument(name = "user-id", help = "Qualified user ID (value@domain)")
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    private val formatter = UserFormatter()

    override fun run() {
        val validatedUserId = validateUserIdOrExit(userId)
        val userService = userServiceProvider()

        when (val result = userService.get(validatedUserId)) {
            is UserGetResult.Success -> {
                // Reuse the schema-versioned list payload so a single-user get stays
                // consistent with `user search`: {"schemaVersion":1,"users":[{...}]}
                if (json) {
                    echo(formatter.toJson(listOf(result.view)))
                } else {
                    echo(formatter.toDetail(result.view))
                }
            }

            is UserGetResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }
}

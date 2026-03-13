package wirecli.commands
// TODO: Add validation for email format and server URL.

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import wirecli.auth.AuthRedactor
import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import wirecli.auth.LoginInput

class LoginCommand(
    private val authSessionService: AuthSessionService,
) : CliktCommand(name = "login", help = "Authenticate and persist a local session.") {
    private val email by
        option("--email", help = "Email used to authenticate.").required()
    private val password by
        option(
            "--password",
            help = "Password used to authenticate (deprecated; prefer prompt or --password-stdin).",
        )
    private val passwordStdin by
        option("--password-stdin", help = "Read password from stdin for automation.")
            .flag(default = false)
    private val server by option("--server", help = "Wire backend URL override.")

    override fun run() {
        if (passwordStdin && password != null) {
            echo("Use either --password or --password-stdin, not both.", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }

        val resolvedPassword =
            when {
                password != null -> {
                    echo(
                        "Warning: --password is deprecated and may expose secrets in process args. " +
                            "Prefer prompt input or --password-stdin.",
                        err = true,
                    )
                    password
                }

                passwordStdin -> readPasswordFromStdin()
                else -> readPasswordFromConsole()
            }

        if (resolvedPassword.isNullOrEmpty()) {
            echo("Password is required. Use interactive prompt, --password-stdin, or --password.", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }

        when (
            val result =
                authSessionService.login(
                    LoginInput(
                        email = email,
                        password = resolvedPassword,
                        server = server,
                    ),
                )
        ) {
            is AuthResult.Success -> echo(result.message)
            is AuthResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun readPasswordFromStdin(): String? {
        return System.`in`
            .bufferedReader()
            .readLine()
            ?.trimEnd('\r')
    }

    private fun readPasswordFromConsole(): String? {
        val console = System.console() ?: return null
        val passwordChars = console.readPassword("Password: ") ?: return null
        return String(passwordChars)
    }
}

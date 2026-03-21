package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthRedactor
import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.auth.ExitCodes
import wirecli.auth.LoginInput
import wirecli.validation.InputValidator

private val logger = KotlinLogging.logger {}

/**
 * CLI command to authenticate a user and persist their session locally.
 *
 * Supports multiple password input methods:
 * - Interactive console prompt (default, most secure)
 * - stdin input (for automation via --password-stdin)
 * - Command-line option (deprecated, may expose credentials in process args)
 *
 * The resulting session is stored with restricted file permissions (0600)
 * and can be resumed on subsequent CLI invocations without re-authenticating.
 *
 * @invariant authSessionService is never null
 * @invariant Only one password method is used per invocation
 * @invariant Password is securely handled and never logged
 */
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

    /**
     * Executes the login command.
     *
     * Resolves password input method, validates credentials with Wire backend,
     * registers a device, and persists the resulting session.
     *
     * @throws ProgramResult on validation error or authentication failure
     *
     * @pre email option must be provided via --email flag
     * @pre One of: interactive prompt, --password-stdin, or --password must provide password
     * @post If successful, session is persisted and success message printed
     * @post If failed, error message is printed and exit code indicates failure type
     */
    override fun run() {
        val validatedEmail = validateOrExit { InputValidator.validateEmail(email) }
        logger.info { "Login command started for email: $validatedEmail" }

        if (passwordStdin && password != null) {
            logger.warn { "Both --password and --password-stdin provided; must use only one" }
            echo("Use either --password or --password-stdin, not both.", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }

        logger.debug { "Resolving password input method" }
        val resolvedPassword =
            when {
                password != null -> {
                    logger.warn { "Using deprecated --password option; consider interactive prompt or --password-stdin" }
                    echo(
                        "Warning: --password is deprecated and may expose secrets in process args. " +
                            "Prefer prompt input or --password-stdin.",
                        err = true,
                    )
                    password
                }

                passwordStdin -> {
                    logger.debug { "Reading password from stdin" }
                    readPasswordFromStdin()
                }
                else -> {
                    logger.debug { "Reading password from console prompt" }
                    readPasswordFromConsole()
                }
            }

        val validatedPassword = validateOrExit { InputValidator.validatePassword(resolvedPassword ?: "") }

        logger.debug { "Password resolved successfully (${validatedPassword.length} characters)" }
        logger.debug { "Calling authentication service with email: $validatedEmail, server: $server" }

        when (
            val result =
                authSessionService.login(
                    LoginInput(
                        email = validatedEmail,
                        password = validatedPassword,
                        server = server,
                    ),
                )
        ) {
            is AuthResult.Success -> {
                logger.info { "Login successful for email: $validatedEmail" }
                echo(result.message)
            }
            is AuthResult.Failure -> {
                logger.warn { "Login failed for email: $validatedEmail - ${AuthRedactor.redact(result.message)}" }
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun <T> validateOrExit(block: () -> T): T {
        return try {
            block()
        } catch (error: IllegalArgumentException) {
            echo(error.message ?: "Invalid input.", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }
    }

    /**
     * Reads password from stdin stream (non-interactive).
     *
     * Used for automation scenarios where password is piped from another process.
     * Strips trailing carriage returns for cross-platform compatibility.
     *
     * @return Password string from stdin or null if no input available
     *
     * @post If successful, return is trimmed (no trailing \\r)
     */
    private fun readPasswordFromStdin(): String? {
        logger.debug { "Opening stdin for password input" }
        return try {
            val password =
                System.`in`
                    .bufferedReader()
                    .readLine()
                    ?.trimEnd('\r')
            if (password != null) {
                logger.debug { "Password read from stdin successfully (${password.length} characters)" }
            } else {
                logger.warn { "No password data available from stdin" }
            }
            password
        } catch (e: Exception) {
            logger.error(e) { "Failed to read password from stdin" }
            null
        }
    }

    /**
     * Reads password from console with echo suppression.
     *
     * Uses System.console() for secure interactive password input.
     * This is the most secure method as password characters are not echoed to terminal.
     *
     * @return Password string from console or null if console not available
     *
     * @post Characters are not echoed to terminal during input
     * @post If successful, returned password is non-empty
     */
    private fun readPasswordFromConsole(): String? {
        logger.debug { "Attempting to read password from console" }
        val console = System.console()
        if (console == null) {
            logger.warn { "System console not available - cannot read password interactively" }
            return null
        }

        return try {
            val passwordChars = console.readPassword("Password: ")
            if (passwordChars != null) {
                val password = String(passwordChars)
                logger.debug { "Password read from console successfully (${password.length} characters)" }
                password
            } else {
                logger.warn { "Console read password returned null" }
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Error reading password from console" }
            null
        }
    }
}

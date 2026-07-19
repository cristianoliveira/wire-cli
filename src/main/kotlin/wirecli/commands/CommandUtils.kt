package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import wirecli.auth.ExitCodes

/**
 * Validates input and exits with error code if validation fails.
 *
 * Executes the validation block and catches any IllegalArgumentException,
 * formatting and displaying the error message before exiting with the specified code.
 *
 * @param exitCode The exit code to use on validation failure (default: VALIDATION_ERROR)
 * @param defaultMessage The message to show if the exception has no message
 * @param errorFormatter Function to format the error message for display
 * @param block The validation block to execute
 * @return The result of the validation block if successful
 * @throws ProgramResult with the specified exit code if validation fails
 */
internal fun CliktCommand.failWithUsage(message: String = "no subcommand specified"): Nothing {
    echo("Error: $message", err = true)
    echoFormattedHelp()
    throw ProgramResult(ExitCodes.VALIDATION_ERROR)
}

internal fun CliktCommand.validateStructuredOutputOrExit(
    json: Boolean,
    jsonLines: Boolean,
) {
    if (!json || !jsonLines) return

    echo("validation error: use either --json or --json-lines, not both", err = true)
    throw ProgramResult(ExitCodes.VALIDATION_ERROR)
}

internal fun processExitCode(domainExitCode: Int): Int =
    when (domainExitCode) {
        ExitCodes.OK -> ExitCodes.OK
        ExitCodes.VALIDATION_ERROR, 14 -> ExitCodes.VALIDATION_ERROR
        else -> ExitCodes.UNKNOWN_ERROR
    }

internal inline fun <T> CliktCommand.validateOrExit(
    exitCode: Int = ExitCodes.VALIDATION_ERROR,
    defaultMessage: String = "Invalid input.",
    errorFormatter: (String) -> String = { it },
    block: () -> T,
): T {
    return try {
        block()
    } catch (error: IllegalArgumentException) {
        val message = error.message ?: defaultMessage
        echo(errorFormatter(message), err = true)
        throw ProgramResult(processExitCode(exitCode))
    }
}

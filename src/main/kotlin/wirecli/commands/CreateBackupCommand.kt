package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import wirecli.exporting.LocalBackupResult
import wirecli.exporting.LocalBackupService
import kotlin.io.path.Path

class CreateBackupCommand(private val serviceProvider: () -> LocalBackupService) : CliktCommand(
    name = "create",
    help = "Create a Wire backup from the authenticated user's local cache.",
) {
    private val destination by option("--destination", help = "Output .wbu file").required()
    private val password by option("--password", help = "Encrypt backup with this password")

    override fun run() {
        when (val result = serviceProvider().create(Path(destination), password)) {
            is LocalBackupResult.Success -> echo("Created Wire backup at ${result.destination}")
            is LocalBackupResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }
}

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import wirecli.auth.AuthRedactor
import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class LogoutCommand(
    private val authSessionService: AuthSessionService,
) : CliktCommand(name = "logout", help = "Remove local authenticated session.") {
    override fun run() {
        when (val result = authSessionService.logout()) {
            is AuthResult.Success -> echo(result.message)
            is AuthResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

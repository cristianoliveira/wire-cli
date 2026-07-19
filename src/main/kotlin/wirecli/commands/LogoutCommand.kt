package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthRedactor
import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

private val logger = KotlinLogging.logger {}

class LogoutCommand(
    private val authSessionService: AuthSessionService,
) : CliktCommand(name = "logout", help = "Remove local authenticated session.") {
    override fun run() {
        logger.info { "Logout command started" }
        logger.debug { "Checking for active session" }

        when (val result = authSessionService.logout()) {
            is AuthResult.Success -> {
                logger.info { "Logout successful - session cleared" }
                echo(result.message)
            }
            is AuthResult.Failure -> {
                logger.warn { "Logout failed - ${AuthRedactor.redact(result.message)}" }
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }
}

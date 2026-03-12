package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import wirecli.presence.PresenceResult
import wirecli.presence.PresenceService

class PresenceCommand(
    private val presenceService: PresenceService
) : CliktCommand(name = "presence", help = "Show current user presence.") {
    override fun run() {
        when (val result = presenceService.getCurrentPresence()) {
            is PresenceResult.Success -> echo(result.presence.state.value)
            is PresenceResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

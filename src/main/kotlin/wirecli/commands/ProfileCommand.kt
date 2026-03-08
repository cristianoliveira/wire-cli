package wirecli.commands

import wirecli.profile.ProfileResult
import wirecli.profile.ProfileService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult

class ProfileCommand(
    private val profileService: ProfileService
) : CliktCommand(name = "profile", help = "Show current user profile.") {
    override fun run() {
        when (val result = profileService.getCurrentProfile()) {
            is ProfileResult.Success -> {
                echo("Name: ${result.profile.name ?: "-"}")
                echo("Email: ${result.profile.email ?: "-"}")
                echo("Handle: ${result.profile.handle ?: "-"}")
            }

            is ProfileResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

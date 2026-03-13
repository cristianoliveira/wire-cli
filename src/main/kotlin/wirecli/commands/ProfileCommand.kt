package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import wirecli.auth.AuthRedactor
import wirecli.profile.ProfileResult
import wirecli.profile.ProfileService

class ProfileCommand(
    private val profileServiceProvider: () -> ProfileService,
) : CliktCommand(name = "profile", help = "Show current user profile.") {
    override fun run() {
        val profileService = profileServiceProvider()
        when (val result = profileService.getCurrentProfile()) {
            is ProfileResult.Success -> {
                echo("Name: ${result.profile.name ?: "-"}")
                echo("Email: ${result.profile.email ?: "-"}")
                echo("Handle: ${result.profile.handle ?: "-"}")
                echo("Presence: ${result.profile.presence}")
            }

            is ProfileResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

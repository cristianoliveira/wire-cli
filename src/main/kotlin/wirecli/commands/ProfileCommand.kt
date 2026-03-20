package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthRedactor
import wirecli.profile.ProfileResult
import wirecli.profile.ProfileService

private val logger = KotlinLogging.logger {}

class ProfileCommand(
    private val profileServiceProvider: () -> ProfileService,
) : CliktCommand(name = "profile", help = "Show current user profile.") {
    override fun run() {
        logger.info { "Profile command started" }
        val profileService = profileServiceProvider()
        when (val result = profileService.getCurrentProfile()) {
            is ProfileResult.Success -> {
                logger.info { "Successfully retrieved profile: name=${result.profile.name}, handle=${result.profile.handle}" }
                echo("Name: ${result.profile.name ?: "-"}")
                echo("Email: ${result.profile.email ?: "-"}")
                echo("Handle: ${result.profile.handle ?: "-"}")
                echo("Presence: ${result.profile.presence}")
            }

            is ProfileResult.Failure -> {
                logger.warn { "Failed to retrieve profile: ${AuthRedactor.redact(result.message)}" }
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

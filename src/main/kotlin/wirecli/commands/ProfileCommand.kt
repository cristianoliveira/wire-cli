package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthRedactor
import wirecli.profile.ProfileResult
import wirecli.profile.ProfileService
import wirecli.validation.InputValidator

private val logger = KotlinLogging.logger {}

class ProfileCommand(
    private val profileServiceProvider: () -> ProfileService,
) : CliktCommand(name = "profile", help = "Show current user profile.") {
    private val userId by argument(name = "user-id", help = "Optional user ID (value@domain)").optional()

    override fun run() {
        if (userId != null) {
            validateOrExit { InputValidator.validateUserId(userId!!) }
        }

        logger.info { "Profile command started" }
        val profileService = profileServiceProvider()
        when (val result = profileService.getCurrentProfile()) {
            is ProfileResult.Success -> {
                logger.info {
                    "Successfully retrieved profile: name=${result.profile.name}, " +
                        "handle=${result.profile.handle}"
                }
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

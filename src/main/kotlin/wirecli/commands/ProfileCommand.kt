package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthRedactor
import wirecli.profile.ProfileResult
import wirecli.profile.ProfileService
import wirecli.profile.ProfileUpdate
import wirecli.profile.ProfileUpdateResult

private val logger = KotlinLogging.logger {}

class ProfileCommand(
    private val profileServiceProvider: () -> ProfileService,
) : CliktCommand(
        name = "profile",
        help = "Show or update current user profile.",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            ProfileUpdateCommand(profileServiceProvider),
        )
    }

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

class ProfileUpdateCommand(
    private val profileServiceProvider: () -> ProfileService,
) : CliktCommand(name = "update", help = "Update profile name and/or handle.") {
    private val name by option("--name", help = "New display name")
    private val handle by option("--handle", help = "New handle (username)")

    override fun run() {
        if (name == null && handle == null) {
            echo("At least one of --name or --handle must be provided.", err = true)
            throw ProgramResult(1)
        }

        logger.info {
            "Profile update command started: name=${name?.let { AuthRedactor.redact(it) }}, handle=${handle?.let { AuthRedactor.redact(it) }}"
        }

        val profileService = profileServiceProvider()
        val update = ProfileUpdate(name = name, handle = handle)

        when (val result = profileService.updateProfile(update)) {
            is ProfileUpdateResult.Success -> {
                logger.info { "Profile updated successfully" }
                echo("Profile updated successfully.")
                echo("Name: ${result.profile.name ?: "(unchanged)"}")
                echo("Handle: ${result.profile.handle ?: "(unchanged)"}")
            }

            is ProfileUpdateResult.Failure -> {
                logger.warn { "Failed to update profile: ${AuthRedactor.redact(result.message)}" }
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

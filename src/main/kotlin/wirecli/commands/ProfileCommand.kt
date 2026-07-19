package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
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
            ProfileNameCommand(profileServiceProvider),
        )
    }

    override fun run() {
        showCurrentProfile(profileServiceProvider)
    }
}

class MeCommand(
    private val profileServiceProvider: () -> ProfileService,
) : CliktCommand(
        name = "me",
        help = "Show current user profile.",
    ) {
    override fun run() {
        showCurrentProfile(profileServiceProvider)
    }
}

private fun CliktCommand.showCurrentProfile(profileServiceProvider: () -> ProfileService) {
    logger.info { "Profile command started" }
    when (val result = profileServiceProvider().getCurrentProfile()) {
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
            throw ProgramResult(processExitCode(result.exitCode))
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
            "Profile update command started: name=${name?.let {
                AuthRedactor.redact(
                    it,
                )
            }}, handle=${handle?.let { AuthRedactor.redact(it) }}"
        }

        val profileService = profileServiceProvider()
        val update = ProfileUpdate(name = name, handle = handle)

        handleProfileUpdateResult(profileService.updateProfile(update)) { result ->
            echo("Name: ${result.profile.name ?: "(unchanged)"}")
            echo("Handle: ${result.profile.handle ?: "(unchanged)"}")
        }
    }
}

class ProfileNameCommand(
    private val profileServiceProvider: () -> ProfileService,
) : CliktCommand(name = "name", help = "Update your profile display name.") {
    private val profileName by argument(name = "NAME", help = "New display name")

    override fun run() {
        val validatedName =
            requireValueOrExit(
                value = profileName,
                fieldName = "Name",
                errorMessage = "name required",
            )

        logger.info { "Profile name command started: name=${AuthRedactor.redact(validatedName)}" }

        val result = profileServiceProvider().updateProfile(ProfileUpdate(name = validatedName))
        handleProfileUpdateResult(result) { success ->
            echo("Name: ${success.profile.name ?: validatedName}")
        }
    }
}

private fun CliktCommand.handleProfileUpdateResult(
    result: ProfileUpdateResult,
    onSuccess: CliktCommand.(ProfileUpdateResult.Success) -> Unit,
) {
    when (result) {
        is ProfileUpdateResult.Success -> {
            logger.info { "Profile updated successfully" }
            echo("Profile updated successfully.")
            onSuccess(result)
        }

        is ProfileUpdateResult.Failure -> {
            logger.warn { "Failed to update profile: ${AuthRedactor.redact(result.message)}" }
            echo(AuthRedactor.redact(result.message), err = true)
            throw ProgramResult(processExitCode(result.exitCode))
        }
    }
}

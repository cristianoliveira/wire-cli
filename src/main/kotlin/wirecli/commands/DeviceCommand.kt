package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.device.DeviceExitCodes
import wirecli.device.DeviceService
import wirecli.validation.InputValidator

private val logger = KotlinLogging.logger {}

class DeviceCommand(
    private val deviceServiceProvider: () -> DeviceService,
) : CliktCommand(
        name = "device",
        help = "Manage devices (list, info, delete, verify).",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            DeviceListCommand(deviceServiceProvider),
            DeviceInfoCommand(deviceServiceProvider),
            DeviceDeleteCommand(deviceServiceProvider),
            DeviceVerifyCommand(deviceServiceProvider),
        )
    }

    override fun run() {
        logger.info { "Device command executed" }
        logger.debug { "Current subcommand: ${currentContext.invokedSubcommand}" }

        if (currentContext.invokedSubcommand == null) {
            logger.warn { "No subcommand specified for device command" }
            echo("No subcommand specified. Use 'wire device --help' for available commands.")
            throw ProgramResult(0)
        }

        logger.debug { "Device subcommand routing to: ${currentContext.invokedSubcommand}" }
    }
}

internal fun CliktCommand.validateDeviceIdOrExit(deviceId: String): String {
    return validateOrExit(
        exitCode = DeviceExitCodes.INVALID_INPUT,
        defaultMessage = "Invalid device ID.",
    ) {
        InputValidator.validateDeviceId(deviceId)
    }
}

internal fun CliktCommand.validateConversationIdOrExit(conversationId: String): String {
    return validateOrExit(
        defaultMessage = "Invalid conversation ID.",
    ) {
        InputValidator.validateConversationId(conversationId)
    }
}

internal fun CliktCommand.requireValueOrExit(
    value: String,
    fieldName: String,
    errorMessage: String,
): String {
    return validateOrExit(
        defaultMessage = errorMessage,
        errorFormatter = { "validation error: $errorMessage" },
    ) {
        InputValidator.validateRequiredText(value, fieldName)
    }
}

internal fun CliktCommand.validatePositiveLongOrExit(
    value: Long,
    fieldName: String,
): Long {
    return validateOrExit(
        errorFormatter = {
            val normalizedMessage = it.removeSuffix(".")
            "validation error: ${normalizedMessage.replaceFirstChar { char -> char.lowercaseChar() }}"
        },
    ) {
        InputValidator.validatePositiveLong(value, fieldName)
    }
}

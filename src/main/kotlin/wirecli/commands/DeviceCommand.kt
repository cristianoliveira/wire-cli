package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.device.DeviceService

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

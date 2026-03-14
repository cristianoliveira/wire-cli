package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import wirecli.device.DeviceService

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
        if (currentContext.invokedSubcommand == null) {
            echo("No subcommand specified. Use 'wire device --help' for available commands.")
            throw ProgramResult(0)
        }
    }
}

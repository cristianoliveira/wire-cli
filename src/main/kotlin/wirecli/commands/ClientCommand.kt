package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import wirecli.device.DeviceService

class ClientCommand(
    private val deviceServiceProvider: () -> DeviceService,
) : CliktCommand(
        name = "client",
        help = "Manage client devices.",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            ClientListCommand(deviceServiceProvider),
            ClientShowCommand(deviceServiceProvider),
            ClientDeleteCommand(deviceServiceProvider),
        )
    }

    override fun run() {
        if (currentContext.invokedSubcommand == null) {
            echo("No subcommand specified. Use 'wire client --help' for available commands.")
            throw ProgramResult(0)
        }
    }
}

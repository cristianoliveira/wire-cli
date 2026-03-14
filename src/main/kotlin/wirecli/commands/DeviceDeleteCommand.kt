package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.device.DeviceDeleteResult
import wirecli.device.DeviceService

class DeviceDeleteCommand(
    private val deviceServiceProvider: () -> DeviceService,
) : CliktCommand(name = "delete", help = "Delete a device.") {
    private val deviceId by argument(name = "device-id", help = "The device ID to delete")
    private val yes by option("--yes", help = "Skip confirmation prompt").flag(default = false)

    override fun run() {
        if (yes != true) {
            echo("Are you sure you want to delete device '$deviceId'? (y/n)")
            val input = readLine()
            if (input?.lowercase() != "y") {
                echo("Device deletion cancelled.")
                return
            }
        }

        val deviceService = deviceServiceProvider()
        when (val result = deviceService.remove(deviceId)) {
            is DeviceDeleteResult.Success -> {
                echo(result.message)
            }

            is DeviceDeleteResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

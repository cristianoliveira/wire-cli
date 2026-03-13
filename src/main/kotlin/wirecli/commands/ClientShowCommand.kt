package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import wirecli.auth.AuthRedactor
import wirecli.device.DeviceDetailResult
import wirecli.device.DeviceService

class ClientShowCommand(
    private val deviceServiceProvider: () -> DeviceService,
) : CliktCommand(name = "show", help = "Show device details.") {
    private val deviceId by argument(name = "device-id", help = "The device ID to show")

    override fun run() {
        val deviceService = deviceServiceProvider()
        when (val result = deviceService.getDetail(deviceId)) {
            is DeviceDetailResult.Success -> {
                val device = result.view.device
                echo("ID: ${device.id}")
                echo("Type: ${device.type}")
                echo("Fingerprint: ${device.fingerprint}")
                echo("Last Active: ${device.lastActive}")
                echo("Key Package Status: ${result.view.keyPackageStatus}")
            }

            is DeviceDetailResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

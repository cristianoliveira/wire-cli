package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.device.DeviceListResult
import wirecli.device.DeviceService

class ClientListCommand(
    private val deviceServiceProvider: () -> DeviceService,
) : CliktCommand(name = "list", help = "List all devices.") {
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val deviceService = deviceServiceProvider()
        when (val result = deviceService.listCurrentDevices()) {
            is DeviceListResult.Success -> {
                if (json) {
                    outputAsJson(result)
                } else {
                    outputAsTable(result)
                }
            }

            is DeviceListResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun outputAsTable(result: DeviceListResult.Success) {
        val devices = result.view.devices
        if (devices.isEmpty()) {
            echo("No devices found.")
            return
        }

        // Print header
        echo(String.format("%-20s %-15s %-25s %-20s", "ID", "Type", "Fingerprint", "Last Active"))
        echo("-".repeat(80))

        // Print rows
        for (device in devices) {
            echo(
                String.format(
                    "%-20s %-15s %-25s %-20s",
                    device.id,
                    device.type,
                    device.fingerprint.take(20) + if (device.fingerprint.length > 20) "..." else "",
                    device.lastActive,
                ),
            )
        }
    }

    private fun outputAsJson(result: DeviceListResult.Success) {
        val devices = result.view.devices
        val jsonDevices =
            devices
                .map { device ->
                    buildDeviceJsonObject(device)
                }
                .joinToString(",")

        echo("""{"devices":[$jsonDevices]}""")
    }

    private fun buildDeviceJsonObject(device: wirecli.device.Device): String {
        val id = escapeJson(device.id)
        val type = escapeJson(device.type.toString())
        val fingerprint = escapeJson(device.fingerprint)
        val lastActive = escapeJson(device.lastActive)
        val json = "{\"id\":\"$id\",\"type\":\"$type\",\"fingerprint\":\"$fingerprint\",\"lastActive\":\"$lastActive\"}"
        return json
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

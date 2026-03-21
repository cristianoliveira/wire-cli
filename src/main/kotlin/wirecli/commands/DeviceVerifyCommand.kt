package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.device.DeviceService
import wirecli.device.DeviceVerifyResult

class DeviceVerifyCommand(
    private val deviceServiceProvider: () -> DeviceService,
) : CliktCommand(name = "verify", help = "Verify device fingerprint.") {
    private val deviceId by argument(name = "device-id", help = "The device ID to verify")
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val validatedDeviceId = validateDeviceIdOrExit(deviceId)
        val deviceService = deviceServiceProvider()
        when (val result = deviceService.verify(validatedDeviceId)) {
            is DeviceVerifyResult.Success -> {
                if (json) {
                    outputAsJson(result)
                } else {
                    outputAsText(result)
                }
            }

            is DeviceVerifyResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun outputAsText(result: DeviceVerifyResult.Success) {
        echo(result.message)
        echo("Fingerprint: ${result.fingerprint}")
    }

    private fun outputAsJson(result: DeviceVerifyResult.Success) {
        val fingerprint = escapeJson(result.fingerprint)
        val message = escapeJson(result.message)
        val json = "{\"message\":\"$message\",\"fingerprint\":\"$fingerprint\"}"
        echo(json)
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

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.device.DeviceResult
import wirecli.device.DeviceService

class DeviceVerifyCommand(
    private val deviceServiceProvider: () -> DeviceService,
) : CliktCommand(name = "verify", help = "Verify device fingerprint.") {
    private val deviceId by argument(name = "device-id", help = "The device ID to verify")
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val validatedDeviceId = validateDeviceIdOrExit(deviceId)
        val deviceService = deviceServiceProvider()
        when (val result = deviceService.verify(validatedDeviceId)) {
            is DeviceResult.Success -> {
                if (json) {
                    outputAsJson(result.value)
                } else {
                    outputAsText(result.value)
                }
            }

            is DeviceResult.Failure -> {
                echo(AuthRedactor.redact(result.error.message), err = true)
                throw ProgramResult(result.error.exitCode)
            }
        }
    }

    private fun outputAsText(fingerprint: String) {
        echo("Device verified successfully.")
        echo("Fingerprint: $fingerprint")
    }

    private fun outputAsJson(fingerprint: String) {
        val escapedFingerprint = escapeJson(fingerprint)
        val json = "{\"message\":\"Device verified successfully.\",\"fingerprint\":\"$escapedFingerprint\"}"
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

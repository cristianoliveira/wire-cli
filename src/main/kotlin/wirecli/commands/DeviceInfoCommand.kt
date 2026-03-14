package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.device.DeviceDetailResult
import wirecli.device.DeviceService

class DeviceInfoCommand(
    private val deviceServiceProvider: () -> DeviceService,
) : CliktCommand(name = "info", help = "Show device details.") {
    private val deviceId by argument(name = "device-id", help = "The device ID to show details for")
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val deviceService = deviceServiceProvider()
        when (val result = deviceService.getDetail(deviceId)) {
            is DeviceDetailResult.Success -> {
                val device = result.view.device
                if (json) {
                    outputAsJson(device, result.view.keyPackageStatus.toString())
                } else {
                    outputAsText(device, result.view.keyPackageStatus.toString())
                }
            }

            is DeviceDetailResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun outputAsText(device: wirecli.device.Device, keyPackageStatus: String) {
        echo("ID: ${device.id}")
        echo("Type: ${device.type}")
        echo("Fingerprint: ${device.fingerprint}")
        echo("Last Active: ${device.lastActive}")
        echo("Key Package Status: $keyPackageStatus")
        if (device.label != null) {
            echo("Label: ${device.label}")
        }
        if (device.model != null) {
            echo("Model: ${device.model}")
        }
        if (device.registrationTime != null) {
            echo("Registration Time: ${device.registrationTime}")
        }
        echo("Verified: ${device.isVerified}")
        if (device.location != null) {
            echo("Location: ${device.location}")
        }
        if (device.capabilities.isNotEmpty()) {
            echo("Capabilities: ${device.capabilities.joinToString(", ")}")
        }
    }

    private fun outputAsJson(device: wirecli.device.Device, keyPackageStatus: String) {
        val id = escapeJson(device.id)
        val type = escapeJson(device.type.toString())
        val fingerprint = escapeJson(device.fingerprint)
        val lastActive = escapeJson(device.lastActive)
        val label = device.label?.let { escapeJson(it) } ?: "null"
        val model = device.model?.let { escapeJson(it) } ?: "null"
        val registrationTime = device.registrationTime?.let { escapeJson(it) } ?: "null"
        val location = device.location?.let { escapeJson(it) } ?: "null"
        
        val labelJson = if (device.label != null) "\"$label\"" else "null"
        val modelJson = if (device.model != null) "\"$model\"" else "null"
        val registrationTimeJson = if (device.registrationTime != null) "\"$registrationTime\"" else "null"
        val locationJson = if (device.location != null) "\"$location\"" else "null"
        
        val capabilitiesJson = device.capabilities.joinToString(",") { "\"${escapeJson(it)}\"" }

        val json = """{
  "id": "$id",
  "type": "$type",
  "fingerprint": "$fingerprint",
  "lastActive": "$lastActive",
  "keyPackageStatus": "$keyPackageStatus",
  "label": $labelJson,
  "model": $modelJson,
  "registrationTime": $registrationTimeJson,
  "verified": ${device.isVerified},
  "location": $locationJson,
  "capabilities": [$capabilitiesJson]
}"""
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

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.device.DeviceListView
import wirecli.device.DeviceResult
import wirecli.device.DeviceService

/**
 * CLI command to list devices registered to current user or another user.
 *
 * Supports multiple output formats:
 * - Table format (default, human-readable)
 * - JSON format (single object with devices array)
 * - JSON Lines format (one device per line)
 *
 * If no user ID is specified, lists devices for the authenticated session user.
 * If user ID is specified, lists devices for that user (if permitted).
 *
 * @invariant deviceServiceProvider returns non-null DeviceService
 * @invariant Output format is exactly one of: table, json, or json-lines
 */
class DeviceListCommand(
    private val deviceServiceProvider: () -> DeviceService,
) : CliktCommand(name = "list", help = "List devices. Optionally specify a user ID to list their devices.") {
    private val userId by argument(name = "user-id", help = "Optional user ID to list their devices").optional()
    private val json by option("--json", help = "Output as JSON").flag(default = false)
    private val jsonLines by option("--json-lines", help = "Output as JSON lines").flag(default = false)

    /**
     * Executes device list command.
     *
     * Fetches devices from service and outputs in the requested format.
     * On success, outputs formatted device list. On failure, prints error and exits.
     *
     * @throws ProgramResult on API failure with appropriate exit code
     *
     * @pre deviceServiceProvider must return non-null service
     * @post Output is exactly one format: table, JSON, or JSON Lines
     * @post If failure, error message is redacted before output
     */
    override fun run() {
        val deviceService = deviceServiceProvider()
        val result =
            if (userId != null) {
                deviceService.listDevicesForUser(userId!!)
            } else {
                deviceService.listCurrentDevices()
            }

        when (result) {
            is DeviceResult.Success -> {
                when {
                    jsonLines -> outputAsJsonLines(result.value)
                    json -> outputAsJson(result.value)
                    else -> outputAsTable(result.value)
                }
            }

            is DeviceResult.Failure -> {
                echo(AuthRedactor.redact(result.error.message), err = true)
                throw ProgramResult(result.error.exitCode)
            }
        }
    }

    /**
     * Outputs device list in human-readable table format.
     *
     * @param view The device list view
     *
     * @post Output includes header row and device rows with columns:
     *       ID | Type | Fingerprint | Last Active
     */
    private fun outputAsTable(view: DeviceListView) {
        val devices = view.devices
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

    /**
     * Outputs device list as a single JSON object with devices array.
     *
     * Format: {"devices": [{...}, {...}]}
     *
     * @param view The device list view
     *
     * @post Output is valid JSON with root object containing "devices" array
     * @post All string values are properly escaped for JSON
     */
    private fun outputAsJson(view: DeviceListView) {
        val devices = view.devices
        val jsonDevices =
            devices
                .map { device ->
                    buildDeviceJsonObject(device)
                }
                .joinToString(",")

        echo("""{"devices":[$jsonDevices]}""")
    }

    /**
     * Outputs device list as JSON Lines format (one device per line).
     *
     * Each line is a complete JSON object, suitable for streaming/piping.
     *
     * @param view The device list view
     *
     * @post Each output line is valid JSON representing one device
     * @post All string values are properly escaped for JSON
     */
    private fun outputAsJsonLines(view: DeviceListView) {
        val devices = view.devices
        for (device in devices) {
            echo(buildDeviceJsonObject(device))
        }
    }

    /**
     * Builds a JSON object string for a single device.
     *
     * @param device The device to serialize
     * @return Valid JSON string with device properties
     *
     * @post Result is valid JSON with all string values escaped
     * @post Properties: id, type, fingerprint, lastActive
     */
    private fun buildDeviceJsonObject(device: wirecli.device.Device): String {
        val id = escapeJson(device.id)
        val type = escapeJson(device.type.toString())
        val fingerprint = escapeJson(device.fingerprint)
        val lastActive = escapeJson(device.lastActive)
        val json = "{\"id\":\"$id\",\"type\":\"$type\",\"fingerprint\":\"$fingerprint\",\"lastActive\":\"$lastActive\"}"
        return json
    }

    /**
     * Escapes a string value for safe inclusion in JSON.
     *
     * Escapes: backslash, quote, newline, carriage return, tab
     *
     * @param value The string to escape
     * @return Escaped string safe for JSON inclusion
     *
     * @post Result contains no unescaped special characters
     * @post Result is safe to include in JSON strings
     */
    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

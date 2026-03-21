package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import wirecli.auth.AuthRedactor
import wirecli.auth.ExitCodes
import wirecli.device.DeviceDeleteResult
import wirecli.device.DeviceService
import wirecli.shared.Result.Success
import wirecli.shared.Result.Failure

class DeviceDeleteCommand(
    private val deviceServiceProvider: () -> DeviceService,
) : CliktCommand(name = "delete", help = "Delete a device.") {
    private val deviceId by argument(name = "device-id", help = "The device ID to delete")
    private val yes by option("--yes", help = "Skip confirmation prompt").flag(default = false)
    private val password by
        option(
            "--password",
            help = "Password used for device deletion (deprecated; prefer prompt or --password-stdin).",
        )
    private val passwordStdin by
        option("--password-stdin", help = "Read password from stdin for automation.")
            .flag(default = false)

    override fun run() {
        val validatedDeviceId = validateDeviceIdOrExit(deviceId)

        if (yes != true) {
            echo("Are you sure you want to delete device '$validatedDeviceId'? (y/n)")
            val input = readLine()
            if (input?.lowercase() != "y") {
                echo("Device deletion cancelled.")
                return
            }
        }

        // Resolve password from multiple sources
        val resolvedPassword =
            when {
                passwordStdin && password != null -> {
                    echo("Use either --password or --password-stdin, not both.", err = true)
                    throw ProgramResult(ExitCodes.VALIDATION_ERROR)
                }

                password != null -> {
                    echo(
                        "Warning: --password is deprecated and may expose secrets in process args. " +
                            "Prefer prompt input or --password-stdin.",
                        err = true,
                    )
                    password
                }

                passwordStdin -> readPasswordFromStdin()
                else -> null // Allow null for devices that don't require password
            }

        val deviceService = deviceServiceProvider()
        when (val result = deviceService.remove(validatedDeviceId, resolvedPassword)) {
            is DeviceDeleteResult.Success -> {
                echo(result.message)
            }

            is DeviceDeleteResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun readPasswordFromStdin(): String? {
        return System.`in`
            .bufferedReader()
            .readLine()
            ?.trimEnd('\r')
    }
}

package wirecli.device

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.shared.DeviceError

class StubDeviceApiClient(
    private val environment: Map<String, String>,
) : DeviceApiClient {
    // FIXME: Duplicate failure mode handling across methods; consider extracting common mapping
    private val defaultDevices =
        listOf(
            Device(
                id = "device-001",
                label = "MacBook Pro",
                type = DeviceType.DESKTOP,
                model = "MacBook Pro 16\"",
                fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                isVerified = true,
                lastActive = "2025-03-13T10:30:00Z",
                registrationTime = "2025-01-15T14:22:00Z",
                capabilities = listOf("mls", "e2ee", "calling"),
                keyPackages = listOf("kp-001", "kp-002"),
                location = "Berlin, Germany",
            ),
            Device(
                id = "device-002",
                label = "iPhone 15",
                type = DeviceType.MOBILE,
                model = "iPhone 15 Pro Max",
                fingerprint = "b2c3d4e5f6g7h8i9j0k1",
                isVerified = true,
                lastActive = "2025-03-12T15:45:00Z",
                registrationTime = "2025-02-20T09:15:00Z",
                capabilities = listOf("mls", "e2ee", "calling"),
                keyPackages = listOf("kp-003"),
                location = "Berlin, Germany",
            ),
            Device(
                id = "device-003",
                label = "Work PC",
                type = DeviceType.DESKTOP,
                model = "Dell XPS 15",
                fingerprint = "c3d4e5f6g7h8i9j0k1l2",
                isVerified = false,
                lastActive = "2025-03-10T08:20:00Z",
                registrationTime = "2025-03-01T11:30:00Z",
                capabilities = listOf("mls", "e2ee"),
                keyPackages = listOf(),
                location = "Munich, Germany",
            ),
        )

    override fun listDevices(session: AuthSession): DeviceResult<DeviceListView> {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "list_empty" ->
                DeviceResult.Success(
                    value = DeviceListView(devices = emptyList()),
                )

            "list_ok" ->
                DeviceResult.Success(
                    value = DeviceListView(devices = defaultDevices),
                )

            "not_found" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = DeviceMessages.DEVICE_NOT_FOUND,
                        exitCode = DeviceExitCodes.NOT_FOUND,
                    ),
                )

            "server_error" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = DeviceMessages.SERVER_FAILURE,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            "unauthorized" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            else ->
                DeviceResult.Success(
                    value = DeviceListView(devices = defaultDevices),
                )
        }
    }

    override fun listDevicesForUser(
        session: AuthSession,
        userId: String,
    ): DeviceResult<DeviceListView> {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "list_empty" ->
                DeviceResult.Success(
                    value = DeviceListView(devices = emptyList()),
                )

            "list_ok" ->
                DeviceResult.Success(
                    value = DeviceListView(devices = defaultDevices),
                )

            "not_found" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = DeviceMessages.DEVICE_NOT_FOUND,
                        exitCode = DeviceExitCodes.NOT_FOUND,
                    ),
                )

            "server_error" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = DeviceMessages.SERVER_FAILURE,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            "unauthorized" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            else ->
                DeviceResult.Success(
                    value = DeviceListView(devices = defaultDevices),
                )
        }
    }

    override fun getDeviceDetail(
        session: AuthSession,
        deviceId: String,
    ): DeviceResult<DeviceDetailView> {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "not_found" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = DeviceMessages.DEVICE_NOT_FOUND,
                        exitCode = DeviceExitCodes.NOT_FOUND,
                    ),
                )

            "server_error" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = DeviceMessages.SERVER_FAILURE,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            "unauthorized" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            else -> {
                val device =
                    defaultDevices.find { it.id == deviceId }
                        ?: return DeviceResult.Failure(
                            error = DeviceError(
                                message = DeviceMessages.DEVICE_NOT_FOUND,
                                exitCode = DeviceExitCodes.NOT_FOUND,
                            ),
                        )

                DeviceResult.Success(
                    value =
                        DeviceDetailView(
                            device = device,
                            keyPackageStatus = KeyPackageStatus.VALID,
                        ),
                )
            }
        }
    }

    override fun deleteDevice(
        session: AuthSession,
        deviceId: String,
        password: String?,
    ): DeviceResult<String> {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "not_found" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = DeviceMessages.DEVICE_NOT_FOUND,
                        exitCode = DeviceExitCodes.NOT_FOUND,
                    ),
                )

            "server_error" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = DeviceMessages.DELETE_SERVER_FAILURE,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            "unauthorized" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            "password_required" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = DeviceMessages.PASSWORD_REQUIRED,
                        exitCode = DeviceExitCodes.UNAUTHORIZED,
                    ),
                )

            else ->
                DeviceResult.Success(
                    value = "Device deleted successfully.",
                )
        }
    }

    override fun verifyDevice(
        session: AuthSession,
        deviceId: String,
    ): DeviceResult<String> {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "not_found" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = DeviceMessages.DEVICE_NOT_FOUND,
                        exitCode = DeviceExitCodes.NOT_FOUND,
                    ),
                )

            "server_error" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = DeviceMessages.VERIFY_SERVER_FAILURE,
                        exitCode = ExitCodes.SERVER_ERROR,
                    ),
                )

            "unauthorized" ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = AuthMessages.invalidOrExpiredSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                )

            else -> {
                val device =
                    defaultDevices.find { it.id == deviceId }
                        ?: return DeviceResult.Failure(
                            error = DeviceError(
                                message = DeviceMessages.DEVICE_NOT_FOUND,
                                exitCode = DeviceExitCodes.NOT_FOUND,
                            ),
                        )

                // Note: DeviceVerifyResult.Success had both message and fingerprint
                // We're simplifying to just return the message
                DeviceResult.Success(
                    value = "Device verified successfully.",
                )
            }
        }
    }
}

package wirecli.device

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

class StubDeviceApiClient(
    private val environment: Map<String, String>,
) : DeviceApiClient {
    // FIXME: Duplicate failure mode handling across methods; consider extracting common mapping
    private val defaultDevices =
        listOf(
            Device(
                id = "device-001",
                type = DeviceType.DESKTOP,
                fingerprint = "a1b2c3d4e5f6g7h8i9j0",
                lastActive = "2025-03-13T10:30:00Z",
            ),
            Device(
                id = "device-002",
                type = DeviceType.MOBILE,
                fingerprint = "b2c3d4e5f6g7h8i9j0k1",
                lastActive = "2025-03-12T15:45:00Z",
            ),
            Device(
                id = "device-003",
                type = DeviceType.DESKTOP,
                fingerprint = "c3d4e5f6g7h8i9j0k1l2",
                lastActive = "2025-03-10T08:20:00Z",
            ),
        )

    override fun listDevices(session: AuthSession): DeviceListResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "list_empty" ->
                DeviceListResult.Success(
                    view = DeviceListView(devices = emptyList()),
                )

            "list_ok" ->
                DeviceListResult.Success(
                    view = DeviceListView(devices = defaultDevices),
                )

            "not_found" ->
                DeviceListResult.Failure(
                    message = DeviceMessages.DEVICE_NOT_FOUND,
                    exitCode = DeviceExitCodes.NOT_FOUND,
                )

            "server_error" ->
                DeviceListResult.Failure(
                    message = DeviceMessages.SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "unauthorized" ->
                DeviceListResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else ->
                DeviceListResult.Success(
                    view = DeviceListView(devices = defaultDevices),
                )
        }
    }

    override fun listDevicesForUser(
        session: AuthSession,
        userId: String,
    ): DeviceListResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "list_empty" ->
                DeviceListResult.Success(
                    view = DeviceListView(devices = emptyList()),
                )

            "list_ok" ->
                DeviceListResult.Success(
                    view = DeviceListView(devices = defaultDevices),
                )

            "not_found" ->
                DeviceListResult.Failure(
                    message = DeviceMessages.DEVICE_NOT_FOUND,
                    exitCode = DeviceExitCodes.NOT_FOUND,
                )

            "server_error" ->
                DeviceListResult.Failure(
                    message = DeviceMessages.SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "unauthorized" ->
                DeviceListResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else ->
                DeviceListResult.Success(
                    view = DeviceListView(devices = defaultDevices),
                )
        }
    }

    override fun getDeviceDetail(
        session: AuthSession,
        deviceId: String,
    ): DeviceDetailResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "not_found" ->
                DeviceDetailResult.Failure(
                    message = DeviceMessages.DEVICE_NOT_FOUND,
                    exitCode = DeviceExitCodes.NOT_FOUND,
                )

            "server_error" ->
                DeviceDetailResult.Failure(
                    message = DeviceMessages.SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "unauthorized" ->
                DeviceDetailResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else -> {
                val device =
                    defaultDevices.find { it.id == deviceId }
                        ?: return DeviceDetailResult.Failure(
                            message = DeviceMessages.DEVICE_NOT_FOUND,
                            exitCode = DeviceExitCodes.NOT_FOUND,
                        )

                DeviceDetailResult.Success(
                    view =
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
    ): DeviceDeleteResult {
        val mode = environment["WIRE_STUB_MODE"]

        return when (mode) {
            "not_found" ->
                DeviceDeleteResult.Failure(
                    message = DeviceMessages.DEVICE_NOT_FOUND,
                    exitCode = DeviceExitCodes.NOT_FOUND,
                )

            "server_error" ->
                DeviceDeleteResult.Failure(
                    message = DeviceMessages.DELETE_SERVER_FAILURE,
                    exitCode = ExitCodes.SERVER_ERROR,
                )

            "unauthorized" ->
                DeviceDeleteResult.Failure(
                    message = AuthMessages.invalidOrExpiredSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

            else ->
                DeviceDeleteResult.Success(
                    message = "Device deleted successfully.",
                )
        }
    }
}

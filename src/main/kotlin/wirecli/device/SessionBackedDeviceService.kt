package wirecli.device

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import wirecli.shared.DeviceError
import wirecli.shared.Result

private val logger = KotlinLogging.logger {}

class SessionBackedDeviceService(
    private val sessionStore: SessionProvider,
    private val apiClient: DeviceApiClient,
) : DeviceService {
    override fun listCurrentDevices(): DeviceResult<DeviceListView> {
        logger.debug { "Service operation: listCurrentDevices() started" }

        val session =
            sessionStore.readActiveSession()
                ?: return Result.Failure(
                    error = DeviceError(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                ).also { logger.warn { "No active session found for listCurrentDevices()" } }

        logger.debug { "Active session found, calling API client" }
        return apiClient.listDevices(session).also { result ->
            when (result) {
                is Result.Success -> logger.info { "Service: Successfully listed ${result.value.devices.size} device(s)" }
                is Result.Failure -> logger.warn { "Service: Failed to list devices - ${result.error.message}" }
            }
        }
    }

    override fun listDevicesForUser(userId: String): DeviceResult<DeviceListView> {
        logger.debug { "Service operation: listDevicesForUser($userId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return Result.Failure(
                    error = DeviceError(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                ).also { logger.warn { "No active session found for listDevicesForUser($userId)" } }

        logger.debug { "Active session found, calling API client for user $userId" }
        return apiClient.listDevicesForUser(session, userId).also { result ->
            when (result) {
                is Result.Success -> logger.info { "Service: Successfully listed ${result.value.devices.size} device(s)" }
                is Result.Failure -> logger.warn { "Service: Failed to list devices for user $userId" }
            }
        }
    }

    override fun getDetail(deviceId: String): DeviceResult<DeviceDetailView> {
        logger.debug { "Service operation: getDetail($deviceId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return Result.Failure(
                    error = DeviceError(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                ).also { logger.warn { "No active session found for getDetail($deviceId)" } }

        logger.debug { "Active session found, calling API client for device $deviceId" }
        return apiClient.getDeviceDetail(session, deviceId).also { result ->
            when (result) {
                is Result.Success -> logger.info { "Service: Retrieved detail for device ${result.value.device.id}" }
                is Result.Failure -> logger.warn { "Service: Failed to get detail for device $deviceId" }
            }
        }
    }

    override fun remove(
        deviceId: String,
        password: String?,
    ): DeviceResult<String> {
        logger.debug { "Service operation: remove($deviceId) started" }
        logger.debug { "Password provided: ${password != null}" }

        val session =
            sessionStore.readActiveSession()
                ?: return Result.Failure(
                    error = DeviceError(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                ).also { logger.warn { "No active session found for remove($deviceId)" } }

        logger.debug { "Active session found, calling API client to delete device $deviceId" }
        return apiClient.deleteDevice(session, deviceId, password).also { result ->
            when (result) {
                is Result.Success -> logger.info { "Service: Deleted device $deviceId" }
                is Result.Failure -> logger.warn { "Service: Failed to delete device $deviceId" }
            }
        }
    }

    override fun verify(deviceId: String): DeviceResult<String> {
        logger.debug { "Service operation: verify($deviceId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return Result.Failure(
                    error = DeviceError(
                        message = AuthMessages.noActiveSession(),
                        exitCode = ExitCodes.UNAUTHORIZED,
                    ),
                ).also { logger.warn { "No active session found for verify($deviceId)" } }

        logger.debug { "Active session found, calling API client to verify device $deviceId" }
        return apiClient.verifyDevice(session, deviceId).also { result ->
            when (result) {
                is Result.Success -> logger.info { "Service: Successfully verified device $deviceId" }
                is Result.Failure -> logger.warn { "Service: Failed to verify device $deviceId" }
            }
        }
    }
}

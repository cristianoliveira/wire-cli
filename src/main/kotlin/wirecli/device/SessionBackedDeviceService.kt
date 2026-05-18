package wirecli.device

import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider

private val logger = KotlinLogging.logger {}

class SessionBackedDeviceService(
    private val sessionStore: SessionProvider,
    private val apiClient: DeviceApiClient,
) : DeviceService {
    override fun listCurrentDevices(): DeviceListResult {
        logger.debug { "Service operation: listCurrentDevices() started" }

        val session =
            sessionStore.readActiveSession()
                ?: return DeviceListResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for listCurrentDevices()" } }

        logger.debug { "Active session found, calling API client" }
        return apiClient.listDevices(session).also { result ->
            when (result) {
                is DeviceListResult.Success ->
                    logger.info { "Service: Successfully listed ${result.view.devices.size} device(s)" }
                is DeviceListResult.Failure -> logger.warn { "Service: Failed to list devices - ${result.message}" }
            }
        }
    }

    override fun listDevicesForUser(userId: String): DeviceListResult {
        logger.debug { "Service operation: listDevicesForUser($userId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return DeviceListResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for listDevicesForUser($userId)" } }

        logger.debug { "Active session found, calling API client for user $userId" }
        return apiClient.listDevicesForUser(session, userId).also { result ->
            when (result) {
                is DeviceListResult.Success ->
                    logger.info { "Service: Successfully listed ${result.view.devices.size} device(s)" }
                is DeviceListResult.Failure -> logger.warn { "Service: Failed to list devices for user $userId" }
            }
        }
    }

    override fun getDetail(deviceId: String): DeviceDetailResult {
        logger.debug { "Service operation: getDetail($deviceId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return DeviceDetailResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for getDetail($deviceId)" } }

        logger.debug { "Active session found, calling API client for device $deviceId" }
        return apiClient.getDeviceDetail(session, deviceId).also { result ->
            when (result) {
                is DeviceDetailResult.Success ->
                    logger.info { "Service: Retrieved detail for device ${result.view.device.id}" }
                is DeviceDetailResult.Failure -> logger.warn { "Service: Failed to get detail for device $deviceId" }
            }
        }
    }

    override fun remove(
        deviceId: String,
        password: String?,
    ): DeviceDeleteResult {
        logger.debug { "Service operation: remove($deviceId) started" }
        logger.debug { "Password provided: ${password != null}" }

        val session =
            sessionStore.readActiveSession()
                ?: return DeviceDeleteResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for remove($deviceId)" } }

        logger.debug { "Active session found, calling API client to delete device $deviceId" }
        return apiClient.deleteDevice(session, deviceId, password).also { result ->
            when (result) {
                is DeviceDeleteResult.Success -> logger.info { "Service: Deleted device $deviceId" }
                is DeviceDeleteResult.Failure -> logger.warn { "Service: Failed to delete device $deviceId" }
            }
        }
    }

    override fun verify(deviceId: String): DeviceVerifyResult {
        logger.debug { "Service operation: verify($deviceId) started" }

        val session =
            sessionStore.readActiveSession()
                ?: return DeviceVerifyResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                ).also { logger.warn { "No active session found for verify($deviceId)" } }

        logger.debug { "Active session found, calling API client to verify device $deviceId" }
        return apiClient.verifyDevice(session, deviceId).also { result ->
            when (result) {
                is DeviceVerifyResult.Success -> logger.info { "Service: Successfully verified device $deviceId" }
                is DeviceVerifyResult.Failure -> logger.warn { "Service: Failed to verify device $deviceId" }
            }
        }
    }
}

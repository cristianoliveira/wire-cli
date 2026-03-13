package wirecli.device

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSessionStore
import wirecli.auth.ExitCodes

class SessionBackedDeviceService(
    private val sessionStore: AuthSessionStore,
    private val apiClient: DeviceApiClient,
) : DeviceService {
    override fun listCurrentDevices(): DeviceListResult {
        val session =
            sessionStore.readActiveSession()
                ?: return DeviceListResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.listDevices(session)
    }

    override fun listDevicesForUser(userId: String): DeviceListResult {
        val session =
            sessionStore.readActiveSession()
                ?: return DeviceListResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.listDevicesForUser(session, userId)
    }

    override fun getDetail(deviceId: String): DeviceDetailResult {
        val session =
            sessionStore.readActiveSession()
                ?: return DeviceDetailResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.getDeviceDetail(session, deviceId)
    }

    override fun remove(deviceId: String): DeviceDeleteResult {
        val session =
            sessionStore.readActiveSession()
                ?: return DeviceDeleteResult.Failure(
                    message = AuthMessages.noActiveSession(),
                    exitCode = ExitCodes.UNAUTHORIZED,
                )

        return apiClient.deleteDevice(session, deviceId)
    }
}

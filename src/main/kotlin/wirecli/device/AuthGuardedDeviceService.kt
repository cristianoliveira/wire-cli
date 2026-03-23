package wirecli.device

import wirecli.auth.AuthSessionService
import wirecli.shared.DeviceError
import wirecli.shared.Result

class AuthGuardedDeviceService(
    private val authSessionService: AuthSessionService,
    private val delegate: DeviceService,
) : DeviceService {
    override fun listCurrentDevices(): DeviceResult<DeviceListView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> delegate.listCurrentDevices()
            is Result.Failure ->
                Result.Failure(
                    error = DeviceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun listDevicesForUser(userId: String): DeviceResult<DeviceListView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> delegate.listDevicesForUser(userId)
            is Result.Failure ->
                Result.Failure(
                    error = DeviceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun getDetail(deviceId: String): DeviceResult<DeviceDetailView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> delegate.getDetail(deviceId)
            is Result.Failure ->
                Result.Failure(
                    error = DeviceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun remove(
        deviceId: String,
        password: String?,
    ): DeviceResult<String> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> delegate.remove(deviceId, password)
            is Result.Failure ->
                Result.Failure(
                    error = DeviceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun verify(deviceId: String): DeviceResult<String> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is Result.Success -> delegate.verify(deviceId)
            is Result.Failure ->
                Result.Failure(
                    error = DeviceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }
}

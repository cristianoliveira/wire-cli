package wirecli.device

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService
import wirecli.shared.DeviceError

class AuthGuardedDeviceService(
    private val authSessionService: AuthSessionService,
    private val delegate: DeviceService,
) : DeviceService {
    override fun listCurrentDevices(): DeviceResult<DeviceListView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.listCurrentDevices()
            is AuthResult.Failure ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun listDevicesForUser(userId: String): DeviceResult<DeviceListView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.listDevicesForUser(userId)
            is AuthResult.Failure ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun getDetail(deviceId: String): DeviceResult<DeviceDetailView> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getDetail(deviceId)
            is AuthResult.Failure ->
                DeviceResult.Failure(
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
            is AuthResult.Success -> delegate.remove(deviceId, password)
            is AuthResult.Failure ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }

    override fun verify(deviceId: String): DeviceResult<String> {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.verify(deviceId)
            is AuthResult.Failure ->
                DeviceResult.Failure(
                    error = DeviceError(
                        message = authResult.error.message,
                        exitCode = authResult.error.exitCode,
                    ),
                )
        }
    }
}

package wirecli.device

import wirecli.auth.AuthResult
import wirecli.auth.AuthSessionService

class AuthGuardedDeviceService(
    private val authSessionService: AuthSessionService,
    private val delegate: DeviceService,
) : DeviceService {
    override fun listCurrentDevices(): DeviceListResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.listCurrentDevices()
            is AuthResult.Failure ->
                DeviceListResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun listDevicesForUser(userId: String): DeviceListResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.listDevicesForUser(userId)
            is AuthResult.Failure ->
                DeviceListResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun getDetail(deviceId: String): DeviceDetailResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.getDetail(deviceId)
            is AuthResult.Failure ->
                DeviceDetailResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun remove(
        deviceId: String,
        password: String?,
    ): DeviceDeleteResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.remove(deviceId, password)
            is AuthResult.Failure ->
                DeviceDeleteResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }

    override fun verify(deviceId: String): DeviceVerifyResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.verify(deviceId)
            is AuthResult.Failure ->
                DeviceVerifyResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

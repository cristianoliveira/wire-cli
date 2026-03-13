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

    override fun remove(deviceId: String): DeviceDeleteResult {
        return when (val authResult = authSessionService.requireActiveSession()) {
            is AuthResult.Success -> delegate.remove(deviceId)
            is AuthResult.Failure ->
                DeviceDeleteResult.Failure(
                    message = authResult.message,
                    exitCode = authResult.exitCode,
                )
        }
    }
}

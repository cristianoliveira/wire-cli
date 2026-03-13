package wirecli.device

import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes

internal class RealKaliumDeviceApiClient(
    private val runtime: RealKaliumDeviceRuntime,
) : DeviceApiClient {
    override fun listDevices(session: AuthSession): DeviceListResult {
        // TODO: Implement device list fetching via Kalium SDK
        return DeviceListResult.Failure(
            message = "Not implemented",
            exitCode = ExitCodes.UNKNOWN_ERROR,
        )
    }

    override fun getDeviceDetail(
        session: AuthSession,
        deviceId: String,
    ): DeviceDetailResult {
        // TODO: Implement device detail fetching via Kalium SDK
        return DeviceDetailResult.Failure(
            message = "Not implemented",
            exitCode = ExitCodes.UNKNOWN_ERROR,
        )
    }

    override fun deleteDevice(
        session: AuthSession,
        deviceId: String,
    ): DeviceDeleteResult {
        // TODO: Implement device deletion via Kalium SDK
        return DeviceDeleteResult.Failure(
            message = "Not implemented",
            exitCode = ExitCodes.UNKNOWN_ERROR,
        )
    }
}

internal interface RealKaliumDeviceRuntime {
    // TODO: Add device runtime methods
    fun shutdown()
}

internal class SdkKaliumDeviceRuntime(
    private val environment: Map<String, String>,
) : RealKaliumDeviceRuntime {
    override fun shutdown() {
        // No background resources in device runtime stub.
    }
}

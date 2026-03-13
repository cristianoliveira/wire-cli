package wirecli.device

import wirecli.auth.AuthSession

// TODO: Consider unifying DeviceListResult, DeviceDetailResult, DeviceDeleteResult into a generic DeviceResult<T> to reduce duplication
enum class DeviceType(val value: String) {
    DESKTOP("desktop"),
    MOBILE("mobile"),
    UNKNOWN("unknown"),
    ;

    override fun toString(): String = value
}

enum class KeyPackageStatus(val value: String) {
    VALID("valid"),
    INVALID("invalid"),
    EXHAUSTED("exhausted"),
    UNKNOWN("unknown"),
    ;

    override fun toString(): String = value
}

data class Device(
    val id: String,
    val type: DeviceType,
    val fingerprint: String,
    val lastActive: String,
)

data class DeviceListView(
    val devices: List<Device>,
)

data class DeviceDetailView(
    val device: Device,
    val keyPackageStatus: KeyPackageStatus,
)

sealed interface DeviceListResult {
    data class Success(val view: DeviceListView) : DeviceListResult

    data class Failure(val message: String, val exitCode: Int) : DeviceListResult
}

sealed interface DeviceDetailResult {
    data class Success(val view: DeviceDetailView) : DeviceDetailResult

    data class Failure(val message: String, val exitCode: Int) : DeviceDetailResult
}

sealed interface DeviceDeleteResult {
    data class Success(val message: String) : DeviceDeleteResult

    data class Failure(val message: String, val exitCode: Int) : DeviceDeleteResult
}

interface DeviceApiClient {
    fun listDevices(session: AuthSession): DeviceListResult

    fun listDevicesForUser(
        session: AuthSession,
        userId: String,
    ): DeviceListResult

    fun getDeviceDetail(
        session: AuthSession,
        deviceId: String,
    ): DeviceDetailResult

    fun deleteDevice(
        session: AuthSession,
        deviceId: String,
    ): DeviceDeleteResult
}

interface DeviceService {
    fun listCurrentDevices(): DeviceListResult

    fun listDevicesForUser(userId: String): DeviceListResult

    fun getDetail(deviceId: String): DeviceDetailResult

    fun remove(deviceId: String): DeviceDeleteResult
}

internal object DeviceExitCodes {
    const val DEVICE_NOT_FOUND = 14
}

internal object DeviceMessages {
    const val DEVICE_NOT_FOUND = "Device not found. Check device ID and try again."
    const val NETWORK_FAILURE = "Device list fetch failed: network is unreachable. Check your connection and retry."
    const val SERVER_FAILURE = "Device service is unavailable. Retry later or check server settings."
    const val UNKNOWN_FAILURE = "Device operation failed unexpectedly. Retry and check your setup."
    const val UNAUTHORIZED_FAILURE = "Your session is invalid or expired. Please log in again."
    const val DELETE_NETWORK_FAILURE = "Device deletion failed: network is unreachable. Check your connection and retry."
    const val DELETE_SERVER_FAILURE = "Device deletion could not be completed. Retry later or check server settings."
    const val DELETE_UNKNOWN_FAILURE = "Device deletion failed unexpectedly. Retry and check your setup."
}

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

// Comprehensive device representation with all relevant properties
data class Device(
    val id: String,
    val label: String? = null,
    val type: DeviceType,
    val model: String? = null,
    val fingerprint: String,
    val isVerified: Boolean = false,
    val lastActive: String,
    val registrationTime: String? = null,
    val capabilities: List<String> = emptyList(),
    val keyPackages: List<String> = emptyList(),
    val location: String? = null,
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

sealed interface DeviceVerifyResult {
    data class Success(val message: String, val fingerprint: String) : DeviceVerifyResult

    data class Failure(val message: String, val exitCode: Int) : DeviceVerifyResult
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

    fun verifyDevice(
        session: AuthSession,
        deviceId: String,
    ): DeviceVerifyResult
}

interface DeviceService {
    fun listCurrentDevices(): DeviceListResult

    fun listDevicesForUser(userId: String): DeviceListResult

    fun getDetail(deviceId: String): DeviceDetailResult

    fun remove(deviceId: String): DeviceDeleteResult

    fun verify(deviceId: String): DeviceVerifyResult
}

// Exit codes for device operations following standard CLI conventions
object DeviceExitCodes {
    const val OK = 0
    const val UNAUTHORIZED = 11
    const val PASSWORD_REQUIRED = 15
    const val PERMISSION_DENIED = 12
    const val NOT_FOUND = 13
    const val INVALID_INPUT = 14
}

internal object DeviceMessages {
    const val DEVICE_NOT_FOUND = "Device not found. Check device ID and try again."
    const val NETWORK_FAILURE = "Device list fetch failed: network is unreachable. Check your connection and retry."
    const val SERVER_FAILURE = "Device service is unavailable. Retry later or check server settings."
    const val UNKNOWN_FAILURE = "Device operation failed unexpectedly. Retry and check your setup."
    const val UNAUTHORIZED_FAILURE = "Your session is invalid or expired. Please log in again."
    const val PASSWORD_REQUIRED = "Password confirmation required to delete device."
    const val INVALID_CREDENTIALS = "Password incorrect. Device deletion cancelled."
    const val DELETE_NETWORK_FAILURE = "Device deletion failed: network is unreachable. Check your connection and retry."
    const val DELETE_SERVER_FAILURE = "Device deletion could not be completed. Retry later or check server settings."
    const val DELETE_UNKNOWN_FAILURE = "Device deletion failed unexpectedly. Retry and check your setup."
    const val VERIFY_NETWORK_FAILURE = "Device verification failed: network is unreachable. Check your connection and retry."
    const val VERIFY_SERVER_FAILURE = "Device verification could not be completed. Retry later or check server settings."
    const val VERIFY_UNKNOWN_FAILURE = "Device verification failed unexpectedly. Retry and check your setup."
    const val PERMISSION_DENIED = "You do not have permission to access this device. Contact your administrator."
    const val INVALID_INPUT = "Invalid device ID or parameters provided."
}

// Device-specific exceptions for error handling
sealed class DeviceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class DeviceNotFound(message: String = DeviceMessages.DEVICE_NOT_FOUND) : DeviceException(message)

    class Unauthorized(message: String = DeviceMessages.UNAUTHORIZED_FAILURE) : DeviceException(message)

    class PermissionDenied(message: String = DeviceMessages.PERMISSION_DENIED) : DeviceException(message)

    class InvalidInput(message: String = DeviceMessages.INVALID_INPUT) : DeviceException(message)

    class NetworkFailure(message: String = DeviceMessages.NETWORK_FAILURE, cause: Throwable? = null) :
        DeviceException(message, cause)

    class ServerError(message: String = DeviceMessages.SERVER_FAILURE, cause: Throwable? = null) :
        DeviceException(message, cause)

    class UnknownFailure(message: String = DeviceMessages.UNKNOWN_FAILURE, cause: Throwable? = null) :
        DeviceException(message, cause)
}

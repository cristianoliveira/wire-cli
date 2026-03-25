package wirecli.domains.device

import wirecli.auth.AuthMessages
import wirecli.auth.ExitCodes
import wirecli.device.DeviceExitCodes
import wirecli.device.DeviceFailureCategory
import wirecli.device.DeviceMessages

/**
 * Maps DeviceFailureCategory to appropriate messages and exit codes.
 * Reduces cyclomatic complexity in failure mapping functions.
 */
internal object DeviceFailureMapper {
    /**
     * Message/code pair for a failure result.
     */
    data class FailureInfo(val message: String, val exitCode: Int)

    /**
     * Maps category to message for list operations.
     */
    fun toListFailureInfo(category: DeviceFailureCategory): FailureInfo =
        FailureInfo(
            message =
                when (category) {
                    DeviceFailureCategory.NETWORK -> DeviceMessages.NETWORK_FAILURE
                    DeviceFailureCategory.SERVER -> DeviceMessages.SERVER_FAILURE
                    DeviceFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
                    DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceMessages.PASSWORD_REQUIRED
                    DeviceFailureCategory.INVALID_CREDENTIALS -> DeviceMessages.INVALID_CREDENTIALS
                    DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceMessages.DEVICE_NOT_FOUND
                    DeviceFailureCategory.UNKNOWN -> DeviceMessages.UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    /**
     * Maps category to message for detail operations.
     */
    fun toDetailFailureInfo(category: DeviceFailureCategory): FailureInfo =
        FailureInfo(
            message =
                when (category) {
                    DeviceFailureCategory.NETWORK -> DeviceMessages.NETWORK_FAILURE
                    DeviceFailureCategory.SERVER -> DeviceMessages.SERVER_FAILURE
                    DeviceFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
                    DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceMessages.PASSWORD_REQUIRED
                    DeviceFailureCategory.INVALID_CREDENTIALS -> DeviceMessages.INVALID_CREDENTIALS
                    DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceMessages.DEVICE_NOT_FOUND
                    DeviceFailureCategory.UNKNOWN -> DeviceMessages.UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    /**
     * Maps category to message for delete operations.
     */
    fun toDeleteFailureInfo(category: DeviceFailureCategory): FailureInfo =
        FailureInfo(
            message =
                when (category) {
                    DeviceFailureCategory.NETWORK -> DeviceMessages.DELETE_NETWORK_FAILURE
                    DeviceFailureCategory.SERVER -> DeviceMessages.DELETE_SERVER_FAILURE
                    DeviceFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
                    DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceMessages.PASSWORD_REQUIRED
                    DeviceFailureCategory.INVALID_CREDENTIALS -> DeviceMessages.INVALID_CREDENTIALS
                    DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceMessages.DEVICE_NOT_FOUND
                    DeviceFailureCategory.UNKNOWN -> DeviceMessages.DELETE_UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    /**
     * Maps category to message for verify operations.
     */
    fun toVerifyFailureInfo(category: DeviceFailureCategory): FailureInfo =
        FailureInfo(
            message =
                when (category) {
                    DeviceFailureCategory.NETWORK -> DeviceMessages.VERIFY_NETWORK_FAILURE
                    DeviceFailureCategory.SERVER -> DeviceMessages.VERIFY_SERVER_FAILURE
                    DeviceFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
                    DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceMessages.PASSWORD_REQUIRED
                    DeviceFailureCategory.INVALID_CREDENTIALS -> DeviceMessages.INVALID_CREDENTIALS
                    DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceMessages.DEVICE_NOT_FOUND
                    DeviceFailureCategory.UNKNOWN -> DeviceMessages.VERIFY_UNKNOWN_FAILURE
                },
            exitCode = categoryToExitCode(category),
        )

    /**
     * Maps category to exit code (common across all operations).
     */
    private fun categoryToExitCode(category: DeviceFailureCategory): Int =
        when (category) {
            DeviceFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            DeviceFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            DeviceFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceExitCodes.PASSWORD_REQUIRED
            DeviceFailureCategory.INVALID_CREDENTIALS -> ExitCodes.AUTH_FAILED
            DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceExitCodes.NOT_FOUND
            DeviceFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }
}

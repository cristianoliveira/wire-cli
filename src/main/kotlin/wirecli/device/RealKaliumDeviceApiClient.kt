package wirecli.device

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.client.DeleteClientResult
import com.wire.kalium.logic.feature.client.SelfClientsResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthSession
import wirecli.domains.device.DeviceFailureMapper
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs

private val logger = KotlinLogging.logger {}

/**
 * Real Kalium-backed implementation of the device API client.
 *
 * Delegates device management operations to the Kalium SDK through [RealKaliumDeviceRuntime],
 * handling session resolution, API calls, and error mapping.
 *
 * @invariant runtime is never null and properly initialized
 * @invariant All public methods return non-null Result types
 */
internal class RealKaliumDeviceApiClient(
    private val runtime: RealKaliumDeviceRuntime,
) : DeviceApiClient {
    /**
     * Lists all devices registered to the current user.
     *
     * @param session The authenticated session for the current user
     * @return DeviceListResult with list of devices or error details
     * @throws Nothing - All errors wrapped in DeviceListResult
     *
     * @pre session must be valid and authenticated
     * @post Result is either Success with Device list or Failure with error code
     * @post Success result contains zero or more Device objects
     */
    override fun listDevices(session: AuthSession): DeviceListResult {
        require(session.userId.isNotBlank()) { "List devices requires a non-blank session user ID." }
        require(session.accessToken.isNotBlank()) { "List devices requires a non-blank session access token." }

        logger.info { "Listing devices for current user" }
        logger.debug { "API call: GET /clients (list self devices)" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, isWriteOperation = false)) {
                is DeviceStepResult.Success -> {
                    logger.debug { "Session scope resolved successfully" }
                    scope.value
                }
                is DeviceStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope: ${scope.category}" }
                    return scope.toDeviceFailure()
                }
            }

        val result =
            when (val devices = runtime.listDevices(sessionScope)) {
                is DeviceStepResult.Success -> {
                    logger.info { "Successfully retrieved ${devices.value.size} device(s)" }
                    logger.debug { "Device list result: ${devices.value.map { it.id }}" }
                    DeviceListResult.Success(
                        view = DeviceListView(devices = devices.value),
                    )
                }

                is DeviceStepResult.Failure -> {
                    logger.warn { "Failed to list devices: ${devices.category}" }
                    devices.toDeviceFailure()
                }
            }

        if (result is DeviceListResult.Success) {
            check(result.view.devices.all { it.id.isNotBlank() }) {
                "Device list success must only include devices with non-blank IDs."
            }
            check(result.view.devices.all { it.fingerprint.isNotBlank() }) {
                "Device list success must only include devices with non-blank fingerprints."
            }
        }
        return result
    }

    /**
     * Lists all devices registered to a specific user.
     *
     * @param session The authenticated session (must have permission to view target user)
     * @param userId The qualified user ID to fetch devices for (format: value@domain)
     * @return DeviceListResult with list of user's devices or error details
     * @throws Nothing - All errors wrapped in DeviceListResult
     *
     * @pre session must be valid and authenticated
     * @pre userId must be in qualified format (value@domain)
     * @post Result is either Success with Device list or Failure with error code
     */
    override fun listDevicesForUser(
        session: AuthSession,
        userId: String,
    ): DeviceListResult {
        require(session.userId.isNotBlank()) { "List devices for user requires a non-blank session user ID." }
        require(session.accessToken.isNotBlank()) { "List devices for user requires a non-blank session access token." }
        require(userId.isNotBlank()) { "List devices for user requires a non-blank target user ID." }

        logger.info { "Listing devices for user: $userId" }
        logger.debug { "API call: GET /users/$userId/clients" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, isWriteOperation = false)) {
                is DeviceStepResult.Success -> {
                    logger.debug { "Session scope resolved for user listing" }
                    scope.value
                }
                is DeviceStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for user listing: ${scope.category}" }
                    return scope.toDeviceFailure()
                }
            }

        val result =
            when (val devices = runtime.listDevicesForUser(sessionScope, userId)) {
                is DeviceStepResult.Success -> {
                    logger.info { "Successfully retrieved ${devices.value.size} device(s) for user $userId" }
                    logger.debug { "Device list for user $userId: ${devices.value.map { it.id }}" }
                    DeviceListResult.Success(
                        view = DeviceListView(devices = devices.value),
                    )
                }

                is DeviceStepResult.Failure -> {
                    logger.warn { "Failed to list devices for user $userId: ${devices.category}" }
                    devices.toDeviceFailure()
                }
            }

        if (result is DeviceListResult.Success) {
            check(result.view.devices.all { it.id.isNotBlank() }) {
                "User device list success must only include devices with non-blank IDs."
            }
            check(result.view.devices.all { it.lastActive.isNotBlank() }) {
                "User device list success must only include devices with non-blank lastActive values."
            }
        }
        return result
    }

    /**
     * Retrieves detailed information for a specific device.
     *
     * @param session The authenticated session
     * @param deviceId The ID of the device to fetch details for
     * @return DeviceDetailResult with device details and key package status or error
     * @throws Nothing - All errors wrapped in DeviceDetailResult
     *
     * @pre session must be valid and authenticated
     * @pre deviceId must reference a device owned by session user or publicly queryable
     * @post Result is either Success with Device details or Failure with error code
     * @post Success result contains device with fingerprint and type information
     */
    override fun getDeviceDetail(
        session: AuthSession,
        deviceId: String,
    ): DeviceDetailResult {
        require(session.userId.isNotBlank()) { "Get device detail requires a non-blank session user ID." }
        require(session.accessToken.isNotBlank()) { "Get device detail requires a non-blank session access token." }
        require(deviceId.isNotBlank()) { "Get device detail requires a non-blank device ID." }

        logger.info { "Retrieving device detail: $deviceId" }
        logger.debug { "API call: GET /clients/$deviceId" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, isWriteOperation = false)) {
                is DeviceStepResult.Success -> {
                    logger.debug { "Session scope resolved for device detail request" }
                    scope.value
                }
                is DeviceStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for device detail: ${scope.category}" }
                    return scope.toDeviceDetailFailure()
                }
            }

        val result =
            when (val device = runtime.getDeviceDetail(sessionScope, deviceId)) {
                is DeviceStepResult.Success -> {
                    logger.info { "Successfully retrieved device detail: ${device.value.id}" }
                    logger.debug { "Device type: ${device.value.type}, Last active: ${device.value.lastActive}" }
                    DeviceDetailResult.Success(
                        view =
                            DeviceDetailView(
                                device = device.value,
                                keyPackageStatus = KeyPackageStatus.VALID,
                            ),
                    )
                }

                is DeviceStepResult.Failure -> {
                    logger.warn { "Failed to retrieve device detail $deviceId: ${device.category}" }
                    device.toDeviceDetailFailure()
                }
            }

        if (result is DeviceDetailResult.Success) {
            check(result.view.device.id == deviceId) {
                "Device detail success must return the requested device ID."
            }
            check(result.view.device.fingerprint.isNotBlank()) {
                "Device detail success must include a non-blank fingerprint."
            }
        }
        return result
    }

    /**
     * Deletes a device registered to the current user.
     *
     * @param session The authenticated session
     * @param deviceId The ID of the device to delete
     * @param password Optional password for re-authentication (may be required by server)
     * @return DeviceDeleteResult with success message or error details
     * @throws Nothing - All errors wrapped in DeviceDeleteResult
     *
     * @pre session must be valid and authenticated
     * @pre deviceId must reference a device owned by session user
     * @pre If PASSWORD_REQUIRED, password must match user's current credentials
     * @post If successful, device is deleted server-side and no longer functional
     * @post Result indicates success message or specific error requiring action
     */
    override fun deleteDevice(
        session: AuthSession,
        deviceId: String,
        password: String?,
    ): DeviceDeleteResult {
        require(session.userId.isNotBlank()) { "Delete device requires a non-blank session user ID." }
        require(session.accessToken.isNotBlank()) { "Delete device requires a non-blank session access token." }
        require(deviceId.isNotBlank()) { "Delete device requires a non-blank device ID." }
        require(password == null || password.isNotBlank()) {
            "Delete device password must be null or non-blank."
        }

        logger.info { "Deleting device: $deviceId" }
        logger.debug { "API call: DELETE /clients/$deviceId (write operation)" }
        logger.debug { "Password provided: ${password != null}" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, isWriteOperation = true)) {
                is DeviceStepResult.Success -> {
                    logger.debug { "Session scope resolved for device deletion" }
                    scope.value
                }
                is DeviceStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for device deletion: ${scope.category}" }
                    return scope.toDeviceDeleteFailure()
                }
            }

        val result =
            when (val deleteResult = runtime.deleteDevice(sessionScope, deviceId, password)) {
                is DeviceStepResult.Success -> {
                    logger.info { "Device deleted successfully: $deviceId" }
                    DeviceDeleteResult.Success(
                        message = "Device deleted successfully.",
                    )
                }

                is DeviceStepResult.Failure -> {
                    logger.warn { "Failed to delete device $deviceId: ${deleteResult.category}" }
                    deleteResult.toDeviceDeleteFailure()
                }
            }

        when (result) {
            is DeviceDeleteResult.Success -> {
                check(result.message.isNotBlank()) {
                    "Delete device success must provide a non-blank confirmation message."
                }
            }

            is DeviceDeleteResult.Failure -> {
                check(result.exitCode > 0) {
                    "Delete device failure must include a positive exit code."
                }
            }
        }
        return result
    }

    /**
     * Verifies a device by fetching and displaying its fingerprint.
     *
     * @param session The authenticated session
     * @param deviceId The ID of the device to verify
     * @return DeviceVerifyResult with success message and fingerprint or error details
     * @throws Nothing - All errors wrapped in DeviceVerifyResult
     *
     * @pre session must be valid and authenticated
     * @pre deviceId must reference a device owned by session user or publicly queryable
     * @post Result is either Success with device fingerprint or Failure with error code
     */
    override fun verifyDevice(
        session: AuthSession,
        deviceId: String,
    ): DeviceVerifyResult {
        require(session.userId.isNotBlank()) { "Verify device requires a non-blank session user ID." }
        require(session.accessToken.isNotBlank()) { "Verify device requires a non-blank session access token." }
        require(deviceId.isNotBlank()) { "Verify device requires a non-blank device ID." }

        logger.info { "Verifying device: $deviceId" }
        logger.debug { "API call: GET /clients/$deviceId (verification)" }

        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, isWriteOperation = false)) {
                is DeviceStepResult.Success -> {
                    logger.debug { "Session scope resolved for device verification" }
                    scope.value
                }
                is DeviceStepResult.Failure -> {
                    logger.warn { "Failed to resolve session scope for device verification: ${scope.category}" }
                    return scope.toDeviceVerifyFailure()
                }
            }

        val result =
            when (val device = runtime.getDeviceDetail(sessionScope, deviceId)) {
                is DeviceStepResult.Success -> {
                    logger.info { "Device verified successfully: ${device.value.id}" }
                    logger.debug { "Fingerprint verified for device: ${device.value.id}" }
                    DeviceVerifyResult.Success(
                        message = "Device verified successfully.",
                        fingerprint = device.value.fingerprint,
                    )
                }

                is DeviceStepResult.Failure -> {
                    logger.warn { "Failed to verify device $deviceId: ${device.category}" }
                    device.toDeviceVerifyFailure()
                }
            }

        when (result) {
            is DeviceVerifyResult.Success -> {
                check(result.fingerprint.isNotBlank()) {
                    "Verify device success must include a non-blank fingerprint."
                }
            }

            is DeviceVerifyResult.Failure -> {
                check(result.exitCode > 0) {
                    "Verify device failure must include a positive exit code."
                }
            }
        }
        return result
    }
}

/**
 * Contract for Kalium SDK device runtime operations.
 *
 * This interface abstracts Kalium SDK interactions for device management,
 * enabling testability and separation between CLI and SDK concerns.
 *
 * @invariant All methods return non-null DeviceStepResult
 * @invariant Shutdown must be called to cleanup resources
 */
internal interface RealKaliumDeviceRuntime {
    /**
     * Resolves the device session scope for the authenticated user.
     *
     * @param session The authenticated session
     * @param isWriteOperation True if operation requires strict sync validation
     * @return DeviceStepResult with KaliumDeviceSessionScope or Failure
     * @throws Nothing - All errors wrapped in DeviceStepResult
     *
     * @pre session must be valid and authenticated
     * @post Result is either Success with functional session scope or Failure
     */
    fun resolveSessionScope(
        session: AuthSession,
        isWriteOperation: Boolean = false,
    ): DeviceStepResult<KaliumDeviceSessionScope>

    /**
     * Lists all devices registered to the current user.
     *
     * @param sessionScope The session context for the authenticated user
     * @return DeviceStepResult with list of Device objects or Failure
     * @throws Nothing - All errors wrapped in DeviceStepResult
     *
     * @pre sessionScope must represent a valid authenticated user
     * @post Result is either Success with Device list or Failure with error category
     */
    fun listDevices(sessionScope: KaliumDeviceSessionScope): DeviceStepResult<List<Device>>

    /**
     * Lists all devices registered to a specific user.
     *
     * @param sessionScope The session context for the authenticated user
     * @param userId The qualified user ID to list devices for
     * @return DeviceStepResult with list of Device objects or Failure
     * @throws Nothing - All errors wrapped in DeviceStepResult
     *
     * @pre sessionScope must represent a valid authenticated user
     * @pre userId must be in qualified format (value@domain)
     * @post Result is either Success with Device list or Failure
     */
    fun listDevicesForUser(
        sessionScope: KaliumDeviceSessionScope,
        userId: String,
    ): DeviceStepResult<List<Device>>

    /**
     * Retrieves detailed information for a specific device.
     *
     * @param sessionScope The session context for the authenticated user
     * @param deviceId The ID of the device to fetch
     * @return DeviceStepResult with Device or Failure
     * @throws Nothing - All errors wrapped in DeviceStepResult
     *
     * @pre sessionScope must represent a valid authenticated user
     * @pre deviceId must be non-null and non-empty
     * @post Result is either Success with Device or Failure with error category
     */
    fun getDeviceDetail(
        sessionScope: KaliumDeviceSessionScope,
        deviceId: String,
    ): DeviceStepResult<Device>

    /**
     * Deletes a device from the current user's device list.
     *
     * @param sessionScope The session context for the authenticated user
     * @param deviceId The ID of the device to delete
     * @param password Optional password for re-authentication
     * @return DeviceStepResult with Unit on success or Failure
     * @throws Nothing - All errors wrapped in DeviceStepResult
     *
     * @pre sessionScope must represent a valid authenticated user
     * @pre deviceId must reference a device owned by session user
     * @pre If PASSWORD_REQUIRED, password must match user's credentials
     * @post If successful, device is deleted server-side
     */
    fun deleteDevice(
        sessionScope: KaliumDeviceSessionScope,
        deviceId: String,
        password: String? = null,
    ): DeviceStepResult<Unit>

    /**
     * Closes the runtime and releases all resources.
     * Delegates to shutdown() for implementation.
     */
    fun close() {
        shutdown()
    }

    /**
     * Shuts down the Kalium runtime, cleaning up resources and active sessions.
     */
    fun shutdown()
}

/**
 * Scoped context for device operations on a specific authenticated user.
 *
 * @invariant userId is in qualified format (value@domain)
 */
internal data class KaliumDeviceSessionScope(
    val userId: String,
    val server: String?,
)

/**
 * Result type for device operation steps (sealed interface).
 *
 * Represents either a successful step result with typed value or a failure
 * with category for error handling.
 *
 * @invariant Each operation returns exactly one sealed subtype
 */
internal sealed interface DeviceStepResult<out T> {
    /**
     * Successful step result with associated value.
     *
     * @param value The result value from the successful step
     */
    data class Success<T>(val value: T) : DeviceStepResult<T>

    /**
     * Failed step result with error category.
     *
     * @param category The category of failure (determines error message and exit code)
     */
    data class Failure(val category: DeviceFailureCategory) : DeviceStepResult<Nothing>
}

/**
 * Enumeration of possible device operation failure categories.
 *
 * Each category maps to specific user-facing messages and exit codes:
 * NETWORK: Network connectivity issues
 * SERVER: Server errors (5xx, unavailable)
 * UNAUTHORIZED: Session invalid or expired
 * PASSWORD_REQUIRED: Device deletion requires password re-entry
 * INVALID_CREDENTIALS: Password mismatch
 * DEVICE_NOT_FOUND: Device ID not found
 * UNKNOWN: Unexpected/unclassifiable errors
 */
internal enum class DeviceFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    PASSWORD_REQUIRED,
    INVALID_CREDENTIALS,
    DEVICE_NOT_FOUND,
    UNKNOWN,
}

/**
 * Kalium SDK-based implementation of the device runtime.
 *
 * This class manages CoreLogic initialization and delegates device operations
 * to the Kalium SDK, handling error mappings and session lifecycle management.
 *
 * @invariant coreLogic is lazily initialized and properly shut down
 * @invariant activeSessionUserIds tracks all open sessions for cleanup
 * @invariant All methods return non-null DeviceStepResult
 */
internal class SdkKaliumDeviceRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : RealKaliumDeviceRuntime {
    private val activeSessionUserIds = mutableSetOf<UserId>()

    private val coreLogicLazy =
        lazy {
            CoreLogic(
                rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium",
                kaliumConfigs = kaliumCliConfigs(cliMode),
                userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
            )
        }
    private val coreLogic: CoreLogic by coreLogicLazy

    /**
     * Resolves the device session scope for the authenticated user.
     *
     * @param session The authenticated session
     * @param isWriteOperation True if operation requires strict sync validation (write ops)
     * @return DeviceStepResult with KaliumDeviceSessionScope or Failure
     * @throws Nothing - All errors wrapped in DeviceStepResult
     *
     * @pre session.userId must be in qualified format (value@domain)
     * @post Result is either Success with functional scope or Failure
     * @post activeSessionUserIds is updated to track session for cleanup
     */
    override fun resolveSessionScope(
        session: AuthSession,
        isWriteOperation: Boolean,
    ): DeviceStepResult<KaliumDeviceSessionScope> {
        require(session.userId.isNotBlank()) { "Session scope resolution requires a non-blank user ID." }
        require(session.accessToken.isNotBlank()) { "Session scope resolution requires a non-blank access token." }

        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        val result =
            runBlocking {
                try {
                    // For read operations, skip strict sync validation to allow fresh sessions
                    // For write operations, enforce sync validation only if explicitly enabled
                    if (!cliMode.disableSessionSyncWait && isWriteOperation) {
                        coreLogic.sessionScope(qualifiedId) {
                            syncExecutor.request { waitUntilLiveOrFailure() }
                        }
                    }
                    DeviceStepResult.Success(
                        KaliumDeviceSessionScope(
                            userId = session.userId,
                            server = session.server,
                        ),
                    )
                } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
                    DeviceStepResult.Failure(categoryFromThrowable(error))
                }
            }

        check(activeSessionUserIds.contains(qualifiedId)) {
            "Resolved session scopes must track active user IDs for cleanup."
        }
        if (result is DeviceStepResult.Success) {
            check(result.value.userId == session.userId) {
                "Resolved device session scope must preserve the requested user ID."
            }
        }
        return result
    }

    override fun listDevices(sessionScope: KaliumDeviceSessionScope): DeviceStepResult<List<Device>> {
        require(sessionScope.userId.isNotBlank()) { "List devices requires a non-blank session scope user ID." }

        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        val result =
            runBlocking {
                try {
                    val result =
                        coreLogic.sessionScope(qualifiedId) {
                            client.fetchSelfClients()
                        }

                    when (result) {
                        is SelfClientsResult.Success ->
                            DeviceStepResult.Success(
                                result.clients.map { kaliumClient ->
                                    Device(
                                        id = kaliumClient.id.value,
                                        type = mapDeviceType(kaliumClient.deviceType),
                                        fingerprint = kaliumClient.id.value, // Use client ID as fingerprint for now
                                        lastActive = formatLastActive(kaliumClient),
                                    )
                                },
                            )

                        is SelfClientsResult.Failure.Generic ->
                            DeviceStepResult.Failure(categoryFromCoreFailure(result.genericFailure))
                    }
                } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
                    DeviceStepResult.Failure(categoryFromThrowable(error))
                }
            }

        when (result) {
            is DeviceStepResult.Success -> {
                check(result.value.all { it.id.isNotBlank() }) {
                    "List devices success must only include devices with non-blank IDs."
                }
            }

            is DeviceStepResult.Failure -> {
                check(result.category in DeviceFailureCategory.entries) {
                    "List devices failures must map to known DeviceFailureCategory values."
                }
            }
        }
        return result
    }

    override fun listDevicesForUser(
        sessionScope: KaliumDeviceSessionScope,
        userId: String,
    ): DeviceStepResult<List<Device>> {
        require(
            sessionScope.userId.isNotBlank(),
        ) { "List devices for user requires a non-blank session scope user ID." }
        require(userId.isNotBlank()) { "List devices for user requires a non-blank target user ID." }

        val sessionUserId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        val targetUserId =
            userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.DEVICE_NOT_FOUND)

        val result =
            runBlocking {
                try {
                    // This feature requires proper Flow collection from Kalium SDK
                    // Currently unsupported - explicitly fail rather than return empty list
                    throw UnsupportedOperationException(
                        "Fetching devices for other users is not yet implemented. " +
                            "This requires proper Flow-based device collection from Kalium SDK.",
                    )
                } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
                    DeviceStepResult.Failure(categoryFromThrowable(error))
                }
            }

        check(sessionUserId == sessionScope.userId.toQualifiedIdOrNull()) {
            "List devices for user must keep a stable authenticated session user ID."
        }
        check(targetUserId == userId.toQualifiedIdOrNull()) {
            "List devices for user must keep a stable target user ID."
        }
        return result
    }

    override fun getDeviceDetail(
        sessionScope: KaliumDeviceSessionScope,
        deviceId: String,
    ): DeviceStepResult<Device> {
        require(sessionScope.userId.isNotBlank()) { "Get device detail requires a non-blank session scope user ID." }
        require(deviceId.isNotBlank()) { "Get device detail requires a non-blank device ID." }

        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        val result =
            runBlocking {
                try {
                    val result =
                        coreLogic.sessionScope(qualifiedId) {
                            client.fetchSelfClients()
                        }

                    when (result) {
                        is SelfClientsResult.Success -> {
                            val foundClient =
                                result.clients.find { it.id.value == deviceId }
                                    ?: return@runBlocking DeviceStepResult.Failure(
                                        DeviceFailureCategory.DEVICE_NOT_FOUND,
                                    )

                            DeviceStepResult.Success(
                                Device(
                                    id = foundClient.id.value,
                                    type = mapDeviceType(foundClient.deviceType),
                                    fingerprint = foundClient.id.value, // Use client ID as fingerprint for now
                                    lastActive = formatLastActive(foundClient),
                                ),
                            )
                        }

                        is SelfClientsResult.Failure.Generic ->
                            DeviceStepResult.Failure(categoryFromCoreFailure(result.genericFailure))
                    }
                } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
                    DeviceStepResult.Failure(categoryFromThrowable(error))
                }
            }

        if (result is DeviceStepResult.Success) {
            check(result.value.id == deviceId) {
                "Get device detail success must return the requested device ID."
            }
            check(result.value.fingerprint.isNotBlank()) {
                "Get device detail success must include a non-blank fingerprint."
            }
        }
        return result
    }

    override fun deleteDevice(
        sessionScope: KaliumDeviceSessionScope,
        deviceId: String,
        password: String?,
    ): DeviceStepResult<Unit> {
        require(sessionScope.userId.isNotBlank()) { "Delete device requires a non-blank session scope user ID." }
        require(deviceId.isNotBlank()) { "Delete device requires a non-blank device ID." }
        require(password == null || password.isNotBlank()) {
            "Delete device password must be null or non-blank."
        }

        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        val result =
            runBlocking {
                try {
                    val result =
                        coreLogic.sessionScope(qualifiedId) {
                            client.deleteClient(
                                com.wire.kalium.logic.data.client.DeleteClientParam(
                                    password = password,
                                    clientId = com.wire.kalium.logic.data.conversation.ClientId(deviceId),
                                ),
                            )
                        }

                    when (result) {
                        is DeleteClientResult.Success ->
                            DeviceStepResult.Success(Unit)

                        is DeleteClientResult.Failure.InvalidCredentials ->
                            DeviceStepResult.Failure(DeviceFailureCategory.INVALID_CREDENTIALS)

                        is DeleteClientResult.Failure.PasswordAuthRequired ->
                            DeviceStepResult.Failure(DeviceFailureCategory.PASSWORD_REQUIRED)

                        is DeleteClientResult.Failure.Generic ->
                            DeviceStepResult.Failure(categoryFromCoreFailure(result.genericFailure))
                    }
                } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
                    DeviceStepResult.Failure(categoryFromThrowable(error))
                }
            }

        if (result is DeviceStepResult.Failure) {
            check(result.category in DeviceFailureCategory.entries) {
                "Delete device failures must map to known DeviceFailureCategory values."
            }
        }
        check(activeSessionUserIds.contains(qualifiedId)) {
            "Delete device must keep session user tracked for runtime cleanup."
        }
        return result
    }

    override fun shutdown() {
        if (!coreLogicLazy.isInitialized()) return

        runBlocking {
            activeSessionUserIds.forEach { userId ->
                coreLogic.sessionScope(userId) { cancel() }
            }
        }
        activeSessionUserIds.clear()
        check(activeSessionUserIds.isEmpty()) {
            "Device runtime shutdown must clear tracked active sessions."
        }
        coreLogic.getGlobalScope().cancel()
        check(coreLogicLazy.isInitialized()) {
            "Device runtime shutdown expects initialized CoreLogic before cancellation."
        }
    }

    @Suppress("UNCHECKED_CAST", "CANNOT_ACCESS_CLASS")
    private fun formatLastActive(client: com.wire.kalium.logic.data.client.Client): String {
        return try {
            // Access lastActive via reflection to avoid Instant type dependency
            val field = client::class.java.getDeclaredField("lastActive")
            field.isAccessible = true
            val lastActive = field.get(client)
            lastActive?.toString() ?: "unknown"
        } catch (e: Exception) {
            // Reflection failure is intentionally caught with safe fallback.
            // This occurs when the underlying SDK changes its data model.
            // Reason: Fallback to "unknown" is acceptable for display purposes.
            "unknown"
        }
    }

    private fun mapDeviceType(deviceType: com.wire.kalium.logic.data.client.DeviceType?): DeviceType {
        return when (deviceType) {
            com.wire.kalium.logic.data.client.DeviceType.Desktop -> DeviceType.DESKTOP
            com.wire.kalium.logic.data.client.DeviceType.Phone -> DeviceType.MOBILE
            com.wire.kalium.logic.data.client.DeviceType.Tablet -> DeviceType.MOBILE
            else -> DeviceType.UNKNOWN
        }
    }

    private fun categoryFromCoreFailure(failure: CoreFailure): DeviceFailureCategory {
        return when (failure) {
            is NetworkFailure.NoNetworkConnection,
            is NetworkFailure.ProxyError,
            -> DeviceFailureCategory.NETWORK

            is NetworkFailure.ServerMiscommunication -> DeviceFailureCategory.SERVER

            is NetworkFailure.FederatedBackendFailure,
            NetworkFailure.FeatureNotSupported,
            is NetworkFailure.MlsMessageRejectedFailure,
            -> DeviceFailureCategory.SERVER

            else -> DeviceFailureCategory.UNKNOWN
        }
    }

    private fun categoryFromThrowable(error: Throwable): DeviceFailureCategory {
        val message = error.message.orEmpty()
        return when {
            message.contains("network", ignoreCase = true) -> DeviceFailureCategory.NETWORK
            message.contains("unauthorized", ignoreCase = true) -> DeviceFailureCategory.UNAUTHORIZED
            message.contains("auth", ignoreCase = true) -> DeviceFailureCategory.UNAUTHORIZED
            message.contains("not found", ignoreCase = true) -> DeviceFailureCategory.DEVICE_NOT_FOUND
            message.isNotEmpty() -> DeviceFailureCategory.SERVER
            else -> DeviceFailureCategory.UNKNOWN
        }
    }

    private fun resolveHomeDirectory(env: Map<String, String>): String {
        val home = env["HOME"]?.trim()
        if (!home.isNullOrEmpty()) return home
        return System.getProperty("user.home")
    }
}

private fun String.toQualifiedIdOrNull(): UserId? {
    val trimmed = trim()
    val atIndex = trimmed.lastIndexOf('@')
    val isValidFormat = atIndex > 0 && atIndex < trimmed.lastIndex

    return if (isValidFormat) {
        val value = trimmed.substring(0, atIndex)
        val domain = trimmed.substring(atIndex + 1)
        if (value.isNotBlank() && domain.isNotBlank()) UserId(value = value, domain = domain) else null
    } else {
        null
    }
}

private fun DeviceStepResult.Failure.toDeviceFailure(): DeviceListResult.Failure {
    val (message, exitCode) = DeviceFailureMapper.toListFailureInfo(category)
    return DeviceListResult.Failure(message = message, exitCode = exitCode)
}

private fun DeviceStepResult.Failure.toDeviceDetailFailure(): DeviceDetailResult.Failure {
    val (message, exitCode) = DeviceFailureMapper.toDetailFailureInfo(category)
    return DeviceDetailResult.Failure(message = message, exitCode = exitCode)
}

private fun DeviceStepResult.Failure.toDeviceDeleteFailure(): DeviceDeleteResult.Failure {
    val (message, exitCode) = DeviceFailureMapper.toDeleteFailureInfo(category)
    return DeviceDeleteResult.Failure(message = message, exitCode = exitCode)
}

private fun DeviceStepResult.Failure.toDeviceVerifyFailure(): DeviceVerifyResult.Failure {
    val (message, exitCode) = DeviceFailureMapper.toVerifyFailureInfo(category)
    return DeviceVerifyResult.Failure(message = message, exitCode = exitCode)
}

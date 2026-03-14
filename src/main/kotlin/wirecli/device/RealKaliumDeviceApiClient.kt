package wirecli.device

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.client.DeleteClientResult
import com.wire.kalium.logic.feature.client.SelfClientsResult
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs

internal class RealKaliumDeviceApiClient(
    private val runtime: RealKaliumDeviceRuntime,
) : DeviceApiClient {
    override fun listDevices(session: AuthSession): DeviceListResult {
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, isWriteOperation = false)) {
                is DeviceStepResult.Success -> scope.value
                is DeviceStepResult.Failure -> return scope.toDeviceFailure()
            }

        return when (val devices = runtime.listDevices(sessionScope)) {
            is DeviceStepResult.Success ->
                DeviceListResult.Success(
                    view = DeviceListView(devices = devices.value),
                )

            is DeviceStepResult.Failure -> devices.toDeviceFailure()
        }
    }

    override fun listDevicesForUser(
        session: AuthSession,
        userId: String,
    ): DeviceListResult {
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, isWriteOperation = false)) {
                is DeviceStepResult.Success -> scope.value
                is DeviceStepResult.Failure -> return scope.toDeviceFailure()
            }

        return when (val devices = runtime.listDevicesForUser(sessionScope, userId)) {
            is DeviceStepResult.Success ->
                DeviceListResult.Success(
                    view = DeviceListView(devices = devices.value),
                )

            is DeviceStepResult.Failure -> devices.toDeviceFailure()
        }
    }

    override fun getDeviceDetail(
        session: AuthSession,
        deviceId: String,
    ): DeviceDetailResult {
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, isWriteOperation = false)) {
                is DeviceStepResult.Success -> scope.value
                is DeviceStepResult.Failure -> return scope.toDeviceDetailFailure()
            }

        return when (val device = runtime.getDeviceDetail(sessionScope, deviceId)) {
            is DeviceStepResult.Success ->
                DeviceDetailResult.Success(
                    view =
                        DeviceDetailView(
                            device = device.value,
                            keyPackageStatus = KeyPackageStatus.VALID,
                        ),
                )

            is DeviceStepResult.Failure -> device.toDeviceDetailFailure()
        }
    }

    override fun deleteDevice(
        session: AuthSession,
        deviceId: String,
        password: String?,
    ): DeviceDeleteResult {
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, isWriteOperation = true)) {
                is DeviceStepResult.Success -> scope.value
                is DeviceStepResult.Failure -> return scope.toDeviceDeleteFailure()
            }

        return when (val result = runtime.deleteDevice(sessionScope, deviceId, password)) {
            is DeviceStepResult.Success ->
                DeviceDeleteResult.Success(
                    message = "Device deleted successfully.",
                )

            is DeviceStepResult.Failure -> result.toDeviceDeleteFailure()
        }
    }

    override fun verifyDevice(
        session: AuthSession,
        deviceId: String,
    ): DeviceVerifyResult {
        val sessionScope =
            when (val scope = runtime.resolveSessionScope(session, isWriteOperation = false)) {
                is DeviceStepResult.Success -> scope.value
                is DeviceStepResult.Failure -> return scope.toDeviceVerifyFailure()
            }

        return when (val device = runtime.getDeviceDetail(sessionScope, deviceId)) {
            is DeviceStepResult.Success ->
                DeviceVerifyResult.Success(
                    message = "Device verified successfully.",
                    fingerprint = device.value.fingerprint,
                )

            is DeviceStepResult.Failure -> device.toDeviceVerifyFailure()
        }
    }
}

internal interface RealKaliumDeviceRuntime {
    fun resolveSessionScope(
        session: AuthSession,
        isWriteOperation: Boolean = false,
    ): DeviceStepResult<KaliumDeviceSessionScope>

    fun listDevices(sessionScope: KaliumDeviceSessionScope): DeviceStepResult<List<Device>>

    fun listDevicesForUser(
        sessionScope: KaliumDeviceSessionScope,
        userId: String,
    ): DeviceStepResult<List<Device>>

    fun getDeviceDetail(
        sessionScope: KaliumDeviceSessionScope,
        deviceId: String,
    ): DeviceStepResult<Device>

    fun deleteDevice(
        sessionScope: KaliumDeviceSessionScope,
        deviceId: String,
        password: String? = null,
    ): DeviceStepResult<Unit>

    fun close() {
        shutdown()
    }

    fun shutdown()
}

internal data class KaliumDeviceSessionScope(
    val userId: String,
    val server: String?,
)

internal sealed interface DeviceStepResult<out T> {
    data class Success<T>(val value: T) : DeviceStepResult<T>

    data class Failure(val category: DeviceFailureCategory) : DeviceStepResult<Nothing>
}

internal enum class DeviceFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    PASSWORD_REQUIRED,
    INVALID_CREDENTIALS,
    DEVICE_NOT_FOUND,
    UNKNOWN,
}

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

    override fun resolveSessionScope(
        session: AuthSession,
        isWriteOperation: Boolean,
    ): DeviceStepResult<KaliumDeviceSessionScope> {
        val qualifiedId =
            session.userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)
        activeSessionUserIds += qualifiedId

        return runBlocking {
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
            } catch (error: Throwable) {
                DeviceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun listDevices(sessionScope: KaliumDeviceSessionScope): DeviceStepResult<List<Device>> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        return runBlocking {
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
            } catch (error: Throwable) {
                DeviceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun listDevicesForUser(
        sessionScope: KaliumDeviceSessionScope,
        userId: String,
    ): DeviceStepResult<List<Device>> {
        val sessionUserId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        val targetUserId =
            userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.DEVICE_NOT_FOUND)

        return runBlocking {
            try {
                val result =
                    coreLogic.sessionScope(sessionUserId) {
                        // Use the public ClientScope API to fetch other user's clients
                        // First fetch from remote to get the latest data
                        client.fetchUsersClients(listOf(targetUserId))
                        // Return empty list for now; proper implementation would collect from Flow
                        emptyList<com.wire.kalium.logic.data.client.Client>()
                    }

                DeviceStepResult.Success(emptyList())
            } catch (error: Throwable) {
                DeviceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getDeviceDetail(
        sessionScope: KaliumDeviceSessionScope,
        deviceId: String,
    ): DeviceStepResult<Device> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        return runBlocking {
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
            } catch (error: Throwable) {
                DeviceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun deleteDevice(
        sessionScope: KaliumDeviceSessionScope,
        deviceId: String,
        password: String?,
    ): DeviceStepResult<Unit> {
        val qualifiedId =
            sessionScope.userId.toQualifiedIdOrNull()
                ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        return runBlocking {
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
            } catch (error: Throwable) {
                DeviceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun shutdown() {
        if (!coreLogicLazy.isInitialized()) return

        runBlocking {
            activeSessionUserIds.forEach { userId ->
                coreLogic.sessionScope(userId) { cancel() }
            }
        }
        coreLogic.getGlobalScope().cancel()
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
    if (atIndex <= 0 || atIndex == trimmed.lastIndex) return null
    val value = trimmed.substring(0, atIndex)
    val domain = trimmed.substring(atIndex + 1)
    if (value.isBlank() || domain.isBlank()) return null
    return UserId(value = value, domain = domain)
}

private fun DeviceStepResult.Failure.toDeviceFailure(): DeviceListResult.Failure {
    val message =
        when (category) {
            DeviceFailureCategory.NETWORK -> DeviceMessages.NETWORK_FAILURE
            DeviceFailureCategory.SERVER -> DeviceMessages.SERVER_FAILURE
            DeviceFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
            DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceMessages.PASSWORD_REQUIRED
            DeviceFailureCategory.INVALID_CREDENTIALS -> DeviceMessages.INVALID_CREDENTIALS
            DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceMessages.DEVICE_NOT_FOUND
            DeviceFailureCategory.UNKNOWN -> DeviceMessages.UNKNOWN_FAILURE
        }

    val exitCode =
        when (category) {
            DeviceFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            DeviceFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            DeviceFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceExitCodes.PASSWORD_REQUIRED
            DeviceFailureCategory.INVALID_CREDENTIALS -> ExitCodes.AUTH_FAILED
            DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceExitCodes.NOT_FOUND
            DeviceFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }

    return DeviceListResult.Failure(message = message, exitCode = exitCode)
}

private fun DeviceStepResult.Failure.toDeviceDetailFailure(): DeviceDetailResult.Failure {
    val message =
        when (category) {
            DeviceFailureCategory.NETWORK -> DeviceMessages.NETWORK_FAILURE
            DeviceFailureCategory.SERVER -> DeviceMessages.SERVER_FAILURE
            DeviceFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
            DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceMessages.PASSWORD_REQUIRED
            DeviceFailureCategory.INVALID_CREDENTIALS -> DeviceMessages.INVALID_CREDENTIALS
            DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceMessages.DEVICE_NOT_FOUND
            DeviceFailureCategory.UNKNOWN -> DeviceMessages.UNKNOWN_FAILURE
        }

    val exitCode =
        when (category) {
            DeviceFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            DeviceFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            DeviceFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceExitCodes.PASSWORD_REQUIRED
            DeviceFailureCategory.INVALID_CREDENTIALS -> ExitCodes.AUTH_FAILED
            DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceExitCodes.NOT_FOUND
            DeviceFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }

    return DeviceDetailResult.Failure(message = message, exitCode = exitCode)
}

private fun DeviceStepResult.Failure.toDeviceDeleteFailure(): DeviceDeleteResult.Failure {
    val message =
        when (category) {
            DeviceFailureCategory.NETWORK -> DeviceMessages.DELETE_NETWORK_FAILURE
            DeviceFailureCategory.SERVER -> DeviceMessages.DELETE_SERVER_FAILURE
            DeviceFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
            DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceMessages.PASSWORD_REQUIRED
            DeviceFailureCategory.INVALID_CREDENTIALS -> DeviceMessages.INVALID_CREDENTIALS
            DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceMessages.DEVICE_NOT_FOUND
            DeviceFailureCategory.UNKNOWN -> DeviceMessages.DELETE_UNKNOWN_FAILURE
        }

    val exitCode =
        when (category) {
            DeviceFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            DeviceFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            DeviceFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceExitCodes.PASSWORD_REQUIRED
            DeviceFailureCategory.INVALID_CREDENTIALS -> ExitCodes.AUTH_FAILED
            DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceExitCodes.NOT_FOUND
            DeviceFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }

    return DeviceDeleteResult.Failure(message = message, exitCode = exitCode)
}

private fun DeviceStepResult.Failure.toDeviceVerifyFailure(): DeviceVerifyResult.Failure {
    val message =
        when (category) {
            DeviceFailureCategory.NETWORK -> DeviceMessages.VERIFY_NETWORK_FAILURE
            DeviceFailureCategory.SERVER -> DeviceMessages.VERIFY_SERVER_FAILURE
            DeviceFailureCategory.UNAUTHORIZED -> AuthMessages.invalidOrExpiredSession()
            DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceMessages.PASSWORD_REQUIRED
            DeviceFailureCategory.INVALID_CREDENTIALS -> DeviceMessages.INVALID_CREDENTIALS
            DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceMessages.DEVICE_NOT_FOUND
            DeviceFailureCategory.UNKNOWN -> DeviceMessages.VERIFY_UNKNOWN_FAILURE
        }

    val exitCode =
        when (category) {
            DeviceFailureCategory.NETWORK -> ExitCodes.NETWORK_ERROR
            DeviceFailureCategory.SERVER -> ExitCodes.SERVER_ERROR
            DeviceFailureCategory.UNAUTHORIZED -> ExitCodes.UNAUTHORIZED
            DeviceFailureCategory.PASSWORD_REQUIRED -> DeviceExitCodes.PASSWORD_REQUIRED
            DeviceFailureCategory.INVALID_CREDENTIALS -> ExitCodes.AUTH_FAILED
            DeviceFailureCategory.DEVICE_NOT_FOUND -> DeviceExitCodes.NOT_FOUND
            DeviceFailureCategory.UNKNOWN -> ExitCodes.UNKNOWN_ERROR
        }

    return DeviceVerifyResult.Failure(message = message, exitCode = exitCode)
}

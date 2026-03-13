# Wire-CLI Device Feature - Technical Implementation Guide

## Complete Code Examples

### 1. DeviceContracts.kt (Data Models & Interfaces)

```kotlin
package wirecli.device

import wirecli.auth.AuthSession

// Result Types
sealed class DeviceResult {
    data class Success<T>(val data: T) : DeviceResult()
    data class Error(val message: String, val code: String? = null) : DeviceResult()
    object Unauthorized : DeviceResult()
    object NetworkError : DeviceResult()
}

// Data Models
data class Device(
    val id: String,                    // Client ID
    val name: String,                  // Device label/name
    val type: DeviceType,
    val model: String?,
    val location: String?,
    val lastActive: String?,           // ISO 8601 timestamp
    val isVerified: Boolean,
    val capabilities: List<String>,    // ["PROTEUS", "MLS"]
    val fingerprint: String?,
    val isCurrent: Boolean = false     // Is this the current device?
)

data class DeviceList(
    val devices: List<Device>,
    val total: Int
)

data class DeviceFingerprint(
    val clientId: String,
    val fingerprint: String,
    val algorithm: String = "SHA256"
)

data class DeviceInfo(
    val device: Device,
    val createdAt: String?,
    val lastActiveAt: String?,
    val sessions: Int = 0
)

enum class DeviceType {
    DESKTOP, MOBILE, TABLET, UNKNOWN;
    
    fun displayName() = when(this) {
        DESKTOP -> "Desktop"
        MOBILE -> "Mobile"
        TABLET -> "Tablet"
        UNKNOWN -> "Unknown"
    }
}

// API Client Interface
interface DeviceApiClient {
    // List operations
    fun listMyDevices(session: AuthSession): DeviceResult
    fun listUserDevices(session: AuthSession, userId: String): DeviceResult
    fun getDeviceInfo(session: AuthSession, deviceId: String): DeviceResult
    
    // Management operations
    fun deleteDevice(
        session: AuthSession,
        deviceId: String,
        password: String? = null
    ): DeviceResult
    
    fun updateDeviceLabel(
        session: AuthSession,
        deviceId: String,
        newLabel: String
    ): DeviceResult
    
    // Verification operations
    fun verifyDevice(
        session: AuthSession,
        userId: String,
        deviceId: String
    ): DeviceResult
    
    fun getFingerprint(
        session: AuthSession,
        userId: String? = null,
        deviceId: String? = null
    ): DeviceResult
}

// Service Interface (adds business logic)
interface DeviceService {
    fun listMyDevices(session: AuthSession): DeviceResult
    fun listUserDevices(session: AuthSession, userId: String): DeviceResult
    fun getDeviceInfo(session: AuthSession, deviceId: String): DeviceResult
    fun deleteDevice(
        session: AuthSession,
        deviceId: String,
        password: String? = null
    ): DeviceResult
    fun renameDevice(
        session: AuthSession,
        deviceId: String,
        newName: String
    ): DeviceResult
    fun verifyDevice(
        session: AuthSession,
        userId: String,
        deviceId: String
    ): DeviceResult
    fun trustDevice(
        session: AuthSession,
        userId: String,
        deviceId: String
    ): DeviceResult
    fun getDeviceFingerprint(
        session: AuthSession,
        userId: String? = null,
        deviceId: String? = null
    ): DeviceResult
}
```

### 2. RealKaliumDeviceApiClient.kt (Kalium Integration)

```kotlin
package wirecli.device

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.conversation.ClientId
import kotlinx.coroutines.runBlocking
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.runtime.KaliumCliMode
import wirecli.runtime.kaliumCliConfigs

internal class RealKaliumDeviceApiClient(
    private val runtime: RealKaliumDeviceRuntime,
) : DeviceApiClient {
    
    override fun listMyDevices(session: AuthSession): DeviceResult {
        val sessionScope = when (val result = runtime.resolveSessionScope(session)) {
            is DeviceStepResult.Success -> result.value
            is DeviceStepResult.Failure -> return result.toDeviceFailure()
        }

        return when (val devices = runtime.getSelfDevices(sessionScope)) {
            is DeviceStepResult.Success -> {
                val deviceList = DeviceList(
                    devices = devices.value.map { it.toDeviceModel() },
                    total = devices.value.size
                )
                DeviceResult.Success(deviceList)
            }
            is DeviceStepResult.Failure -> devices.toDeviceFailure()
        }
    }

    override fun listUserDevices(session: AuthSession, userId: String): DeviceResult {
        val sessionScope = when (val result = runtime.resolveSessionScope(session)) {
            is DeviceStepResult.Success -> result.value
            is DeviceStepResult.Failure -> return result.toDeviceFailure()
        }

        return when (val devices = runtime.getUserDevices(sessionScope, userId)) {
            is DeviceStepResult.Success -> {
                val deviceList = DeviceList(
                    devices = devices.value.map { it.toDeviceModel() },
                    total = devices.value.size
                )
                DeviceResult.Success(deviceList)
            }
            is DeviceStepResult.Failure -> devices.toDeviceFailure()
        }
    }

    override fun getDeviceInfo(session: AuthSession, deviceId: String): DeviceResult {
        val sessionScope = when (val result = runtime.resolveSessionScope(session)) {
            is DeviceStepResult.Success -> result.value
            is DeviceStepResult.Failure -> return result.toDeviceFailure()
        }

        return when (val device = runtime.getDeviceInfo(sessionScope, deviceId)) {
            is DeviceStepResult.Success -> {
                DeviceResult.Success(device.value.toDeviceInfoModel())
            }
            is DeviceStepResult.Failure -> device.toDeviceFailure()
        }
    }

    override fun deleteDevice(
        session: AuthSession,
        deviceId: String,
        password: String?
    ): DeviceResult {
        val sessionScope = when (val result = runtime.resolveSessionScope(session)) {
            is DeviceStepResult.Success -> result.value
            is DeviceStepResult.Failure -> return result.toDeviceFailure()
        }

        return when (val result = runtime.deleteDevice(sessionScope, deviceId, password)) {
            is DeviceStepResult.Success -> DeviceResult.Success("Device deleted successfully")
            is DeviceStepResult.Failure -> result.toDeviceFailure()
        }
    }

    override fun updateDeviceLabel(
        session: AuthSession,
        deviceId: String,
        newLabel: String
    ): DeviceResult {
        val sessionScope = when (val result = runtime.resolveSessionScope(session)) {
            is DeviceStepResult.Success -> result.value
            is DeviceStepResult.Failure -> return result.toDeviceFailure()
        }

        return when (val result = runtime.updateDeviceLabel(sessionScope, deviceId, newLabel)) {
            is DeviceStepResult.Success -> DeviceResult.Success("Device renamed successfully")
            is DeviceStepResult.Failure -> result.toDeviceFailure()
        }
    }

    override fun verifyDevice(
        session: AuthSession,
        userId: String,
        deviceId: String
    ): DeviceResult {
        val sessionScope = when (val result = runtime.resolveSessionScope(session)) {
            is DeviceStepResult.Success -> result.value
            is DeviceStepResult.Failure -> return result.toDeviceFailure()
        }

        return when (val result = runtime.verifyDevice(sessionScope, userId, deviceId)) {
            is DeviceStepResult.Success -> DeviceResult.Success("Device verified successfully")
            is DeviceStepResult.Failure -> result.toDeviceFailure()
        }
    }

    override fun getFingerprint(
        session: AuthSession,
        userId: String?,
        deviceId: String?
    ): DeviceResult {
        val sessionScope = when (val result = runtime.resolveSessionScope(session)) {
            is DeviceStepResult.Success -> result.value
            is DeviceStepResult.Failure -> return result.toDeviceFailure()
        }

        return when (val fp = runtime.getFingerprint(sessionScope, userId, deviceId)) {
            is DeviceStepResult.Success -> {
                DeviceResult.Success(fp.value.toFingerprintModel())
            }
            is DeviceStepResult.Failure -> fp.toDeviceFailure()
        }
    }
}

// Runtime interface for dependency injection
internal interface RealKaliumDeviceRuntime {
    fun resolveSessionScope(session: AuthSession): DeviceStepResult<KaliumDeviceSessionScope>
    fun getSelfDevices(sessionScope: KaliumDeviceSessionScope): DeviceStepResult<List<ClientModel>>
    fun getUserDevices(sessionScope: KaliumDeviceSessionScope, userId: String): DeviceStepResult<List<ClientModel>>
    fun getDeviceInfo(sessionScope: KaliumDeviceSessionScope, deviceId: String): DeviceStepResult<ClientModel>
    fun deleteDevice(sessionScope: KaliumDeviceSessionScope, deviceId: String, password: String?): DeviceStepResult<Unit>
    fun updateDeviceLabel(sessionScope: KaliumDeviceSessionScope, deviceId: String, label: String): DeviceStepResult<Unit>
    fun verifyDevice(sessionScope: KaliumDeviceSessionScope, userId: String, deviceId: String): DeviceStepResult<Unit>
    fun getFingerprint(sessionScope: KaliumDeviceSessionScope, userId: String?, deviceId: String?): DeviceStepResult<FingerprintModel>
    fun shutdown()
}

internal data class KaliumDeviceSessionScope(
    val userId: String,
    val server: String?,
    val coreLogic: CoreLogic? = null
)

internal sealed interface DeviceStepResult<out T> {
    data class Success<T>(val value: T) : DeviceStepResult<T>
    data class Failure(val category: DeviceFailureCategory) : DeviceStepResult<Nothing>
}

internal enum class DeviceFailureCategory {
    NETWORK,
    SERVER,
    UNAUTHORIZED,
    NOT_FOUND,
    INVALID_INPUT,
    UNKNOWN,
}

// SDK Implementation
internal class SdkKaliumDeviceRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment),
) : RealKaliumDeviceRuntime {
    
    private val coreLogicLazy = lazy {
        CoreLogic(
            rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium",
            kaliumConfigs = kaliumCliConfigs(cliMode),
            userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}",
        )
    }
    private val coreLogic: CoreLogic by coreLogicLazy

    override fun resolveSessionScope(session: AuthSession): DeviceStepResult<KaliumDeviceSessionScope> {
        val qualifiedId = session.userId.toQualifiedIdOrNull()
            ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        return try {
            DeviceStepResult.Success(
                KaliumDeviceSessionScope(
                    userId = session.userId,
                    server = session.server,
                    coreLogic = coreLogic
                )
            )
        } catch (error: Throwable) {
            DeviceStepResult.Failure(categoryFromThrowable(error))
        }
    }

    override fun getSelfDevices(sessionScope: KaliumDeviceSessionScope): DeviceStepResult<List<ClientModel>> {
        val qualifiedId = sessionScope.userId.toQualifiedIdOrNull()
            ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                val clients = coreLogic.sessionScope(qualifiedId) {
                    client.fetchSelfClients()
                }.getOrNull() ?: emptyList()

                DeviceStepResult.Success(clients)
            } catch (error: Throwable) {
                DeviceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getUserDevices(sessionScope: KaliumDeviceSessionScope, userId: String): DeviceStepResult<List<ClientModel>> {
        val qualifiedId = sessionScope.userId.toQualifiedIdOrNull()
            ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        val targetUserId = userId.toQualifiedIdOrNull()
            ?: return DeviceStepResult.Failure(DeviceFailureCategory.INVALID_INPUT)

        return runBlocking {
            try {
                val clients = coreLogic.sessionScope(qualifiedId) {
                    client.getOtherUserClients(targetUserId)
                }.getOrNull() ?: emptyList()

                DeviceStepResult.Success(clients)
            } catch (error: Throwable) {
                DeviceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getDeviceInfo(sessionScope: KaliumDeviceSessionScope, deviceId: String): DeviceStepResult<ClientModel> {
        val qualifiedId = sessionScope.userId.toQualifiedIdOrNull()
            ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                val client = coreLogic.sessionScope(qualifiedId) {
                    client.observeClientDetailsUseCase(ClientId(deviceId))
                }.getOrNull()

                if (client != null) {
                    DeviceStepResult.Success(client)
                } else {
                    DeviceStepResult.Failure(DeviceFailureCategory.NOT_FOUND)
                }
            } catch (error: Throwable) {
                DeviceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun deleteDevice(
        sessionScope: KaliumDeviceSessionScope,
        deviceId: String,
        password: String?
    ): DeviceStepResult<Unit> {
        val qualifiedId = sessionScope.userId.toQualifiedIdOrNull()
            ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                coreLogic.sessionScope(qualifiedId) {
                    client.deleteClient(DeleteClientParam(ClientId(deviceId), password))
                }.fold(
                    onSuccess = { DeviceStepResult.Success(Unit) },
                    onFailure = { DeviceStepResult.Failure(DeviceFailureCategory.SERVER) }
                )
            } catch (error: Throwable) {
                DeviceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun updateDeviceLabel(
        sessionScope: KaliumDeviceSessionScope,
        deviceId: String,
        label: String
    ): DeviceStepResult<Unit> {
        // Note: Kalium may not have direct label update - check actual API
        // This is a placeholder for the pattern
        return DeviceStepResult.Failure(DeviceFailureCategory.UNKNOWN)
    }

    override fun verifyDevice(
        sessionScope: KaliumDeviceSessionScope,
        userId: String,
        deviceId: String
    ): DeviceStepResult<Unit> {
        val qualifiedId = sessionScope.userId.toQualifiedIdOrNull()
            ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        val targetUserId = userId.toQualifiedIdOrNull()
            ?: return DeviceStepResult.Failure(DeviceFailureCategory.INVALID_INPUT)

        return runBlocking {
            try {
                coreLogic.sessionScope(qualifiedId) {
                    client.updateClientVerificationStatus(
                        userId = targetUserId,
                        clientId = ClientId(deviceId),
                        verified = true
                    )
                }.fold(
                    onSuccess = { DeviceStepResult.Success(Unit) },
                    onFailure = { DeviceStepResult.Failure(DeviceFailureCategory.SERVER) }
                )
            } catch (error: Throwable) {
                DeviceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun getFingerprint(
        sessionScope: KaliumDeviceSessionScope,
        userId: String?,
        deviceId: String?
    ): DeviceStepResult<FingerprintModel> {
        val qualifiedId = sessionScope.userId.toQualifiedIdOrNull()
            ?: return DeviceStepResult.Failure(DeviceFailureCategory.UNAUTHORIZED)

        return runBlocking {
            try {
                val fingerprint = coreLogic.sessionScope(qualifiedId) {
                    if (userId == null) {
                        client.getProteusFingerprint()
                    } else {
                        val targetUserId = userId.toQualifiedIdOrNull() ?: return@sessionScope null
                        client.remoteClientFingerPrint(targetUserId, ClientId(deviceId ?: ""))
                    }
                }.getOrNull()

                if (fingerprint != null) {
                    DeviceStepResult.Success(FingerprintModel(
                        clientId = deviceId ?: "self",
                        fingerprint = fingerprint
                    ))
                } else {
                    DeviceStepResult.Failure(DeviceFailureCategory.NOT_FOUND)
                }
            } catch (error: Throwable) {
                DeviceStepResult.Failure(categoryFromThrowable(error))
            }
        }
    }

    override fun shutdown() {
        coreLogicLazy.value.shutdown()
    }
}

// Model conversion extensions
private fun ClientModel.toDeviceModel(): Device {
    return Device(
        id = this.id.value,
        name = this.label ?: "Unknown Device",
        type = DeviceType.valueOf(this.type.name),
        model = this.model,
        location = this.location,
        lastActive = this.lastActive?.toString(),
        isVerified = this.isVerified,
        capabilities = this.capabilities?.map { it.name } ?: emptyList(),
        fingerprint = null
    )
}

private fun ClientModel.toDeviceInfoModel(): DeviceInfo {
    return DeviceInfo(
        device = this.toDeviceModel(),
        createdAt = this.registrationDate?.toString(),
        lastActiveAt = this.lastActive?.toString()
    )
}

private fun FingerprintData.toFingerprintModel(): DeviceFingerprint {
    return DeviceFingerprint(
        clientId = this.clientId,
        fingerprint = this.fingerprint,
        algorithm = "SHA256"
    )
}

private fun DeviceStepResult.Failure.toDeviceFailure(): DeviceResult.Error {
    return DeviceResult.Error(
        message = when(this.category) {
            DeviceFailureCategory.NETWORK -> "Network error occurred"
            DeviceFailureCategory.SERVER -> "Server error occurred"
            DeviceFailureCategory.UNAUTHORIZED -> "Unauthorized - please login"
            DeviceFailureCategory.NOT_FOUND -> "Device not found"
            DeviceFailureCategory.INVALID_INPUT -> "Invalid input provided"
            DeviceFailureCategory.UNKNOWN -> "Unknown error occurred"
        },
        code = this.category.name
    )
}

private fun categoryFromThrowable(throwable: Throwable): DeviceFailureCategory {
    return when {
        throwable.message?.contains("unauthorized", ignoreCase = true) == true -> 
            DeviceFailureCategory.UNAUTHORIZED
        throwable.message?.contains("not found", ignoreCase = true) == true -> 
            DeviceFailureCategory.NOT_FOUND
        throwable.message?.contains("network", ignoreCase = true) == true -> 
            DeviceFailureCategory.NETWORK
        else -> DeviceFailureCategory.UNKNOWN
    }
}
```

### 3. DeviceCommand.kt (CLI Integration)

```kotlin
package wirecli.commands

import picocli.commandline.Command
import picocli.commandline.Option
import picocli.commandline.Parameters
import picocli.commandline.CommandLine
import wirecli.auth.AuthGuardedService
import wirecli.device.DeviceApiClient
import wirecli.device.DeviceResult
import wirecli.device.DeviceService

@Command(
    name = "device",
    description = "Manage devices/clients",
    subcommands = [
        DeviceCommand.ListCommand::class,
        DeviceCommand.InfoCommand::class,
        DeviceCommand.DeleteCommand::class,
        DeviceCommand.VerifyCommand::class,
        DeviceCommand.RenameCommand::class,
        DeviceCommand.FingerprintCommand::class,
    ]
)
class DeviceCommand : Runnable {
    override fun run() {
        println("Use 'device --help' for available commands")
    }

    @Command(name = "list", description = "List devices")
    inner class ListCommand(
        @Option(names = ["-h", "--help"], usageHelp = true, description = "Show help")
        val help: Boolean = false,
        
        @Parameters(index = "0", arity = "0..1", description = "User ID (optional)")
        val userId: String? = null,
        
        @Option(names = ["-f", "--format"], description = "Output format: table, json, yaml")
        val format: String = "table"
    ) : Runnable {
        override fun run() {
            val result = if (userId == null) {
                deviceService.listMyDevices()
            } else {
                deviceService.listUserDevices(userId)
            }

            when (result) {
                is DeviceResult.Success<*> -> {
                    when (format.lowercase()) {
                        "json" -> println(result.data.toJson())
                        "yaml" -> println(result.data.toYaml())
                        else -> printTable(result.data)
                    }
                }
                is DeviceResult.Error -> System.err.println("Error: ${result.message}")
                is DeviceResult.Unauthorized -> System.err.println("Error: Unauthorized")
                is DeviceResult.NetworkError -> System.err.println("Error: Network error")
            }
        }

        private fun printTable(devices: Any) {
            // Implementation for table formatting
            println("Devices: $devices")
        }

        private fun Any.toJson(): String = "JSON representation"
        private fun Any.toYaml(): String = "YAML representation"
    }

    @Command(name = "info", description = "Get device information")
    inner class InfoCommand(
        @Parameters(index = "0", description = "Device ID")
        val deviceId: String,
        
        @Option(names = ["-f", "--format"], description = "Output format: table, json, yaml")
        val format: String = "table"
    ) : Runnable {
        override fun run() {
            val result = deviceService.getDeviceInfo(deviceId)

            when (result) {
                is DeviceResult.Success<*> -> {
                    when (format.lowercase()) {
                        "json" -> println(result.data.toJson())
                        "yaml" -> println(result.data.toYaml())
                        else -> println(result.data)
                    }
                }
                is DeviceResult.Error -> System.err.println("Error: ${result.message}")
                is DeviceResult.Unauthorized -> System.err.println("Error: Unauthorized")
                is DeviceResult.NetworkError -> System.err.println("Error: Network error")
            }
        }
    }

    @Command(name = "delete", description = "Delete a device")
    inner class DeleteCommand(
        @Parameters(index = "0", description = "Device ID")
        val deviceId: String,
        
        @Option(names = ["-p", "--password"], description = "Password for confirmation", arity = "0..1")
        val password: String? = null
    ) : Runnable {
        override fun run() {
            val result = deviceService.deleteDevice(deviceId, password)

            when (result) {
                is DeviceResult.Success<*> -> println("Device deleted successfully")
                is DeviceResult.Error -> System.err.println("Error: ${result.message}")
                is DeviceResult.Unauthorized -> System.err.println("Error: Unauthorized")
                is DeviceResult.NetworkError -> System.err.println("Error: Network error")
            }
        }
    }

    @Command(name = "verify", description = "Verify a device")
    inner class VerifyCommand(
        @Parameters(index = "0", description = "User ID")
        val userId: String,
        
        @Parameters(index = "1", description = "Device ID")
        val deviceId: String
    ) : Runnable {
        override fun run() {
            val result = deviceService.verifyDevice(userId, deviceId)

            when (result) {
                is DeviceResult.Success<*> -> println("Device verified successfully")
                is DeviceResult.Error -> System.err.println("Error: ${result.message}")
                is DeviceResult.Unauthorized -> System.err.println("Error: Unauthorized")
                is DeviceResult.NetworkError -> System.err.println("Error: Network error")
            }
        }
    }

    @Command(name = "rename", description = "Rename a device")
    inner class RenameCommand(
        @Parameters(index = "0", description = "Device ID")
        val deviceId: String,
        
        @Parameters(index = "1", description = "New device name")
        val newName: String
    ) : Runnable {
        override fun run() {
            val result = deviceService.renameDevice(deviceId, newName)

            when (result) {
                is DeviceResult.Success<*> -> println("Device renamed successfully")
                is DeviceResult.Error -> System.err.println("Error: ${result.message}")
                is DeviceResult.Unauthorized -> System.err.println("Error: Unauthorized")
                is DeviceResult.NetworkError -> System.err.println("Error: Network error")
            }
        }
    }

    @Command(name = "fingerprint", description = "Get device fingerprint")
    inner class FingerprintCommand(
        @Parameters(index = "0", arity = "0..1", description = "User ID (optional)")
        val userId: String? = null,
        
        @Parameters(index = "1", arity = "0..1", description = "Device ID (optional)")
        val deviceId: String? = null
    ) : Runnable {
        override fun run() {
            val result = deviceService.getDeviceFingerprint(userId, deviceId)

            when (result) {
                is DeviceResult.Success<*> -> println("Fingerprint: ${result.data}")
                is DeviceResult.Error -> System.err.println("Error: ${result.message}")
                is DeviceResult.Unauthorized -> System.err.println("Error: Unauthorized")
                is DeviceResult.NetworkError -> System.err.println("Error: Network error")
            }
        }
    }

    companion object {
        var deviceService: DeviceService? = null
    }
}
```

---

## Kalium Methods Usage Mapping

| Feature | Kalium Method | Notes |
|---------|---------------|-------|
| List self devices | `client.fetchSelfClients()` | Returns `FetchSelfClientsFromRemoteUseCase` |
| List user devices | `client.getOtherUserClients(userId)` | Returns `ObserveClientsByUserIdUseCase` |
| Get device info | `client.observeClientDetailsUseCase(clientId)` | Returns device details |
| Delete device | `client.deleteClient(DeleteClientParam)` | Requires password for self device |
| Verify device | `client.updateClientVerificationStatus()` | Mark as trusted |
| Get fingerprint | `client.getProteusFingerprint()` | Self device fingerprint |
| Remote fingerprint | `client.remoteClientFingerPrint()` | Other user's device fingerprint |

---

## Error Handling Strategy

```kotlin
sealed class DeviceError {
    object UnauthorizedError : DeviceError()
    object NetworkError : DeviceError()
    object ServerError : DeviceError()
    data class NotFoundError(val resourceId: String) : DeviceError()
    data class ValidationError(val field: String, val message: String) : DeviceError()
    data class UnknownError(val cause: Throwable) : DeviceError()
}

// Use in try-catch blocks
try {
    // Kalium operation
} catch (e: Exception) {
    return when {
        e.message?.contains("unauthorized") == true -> DeviceError.UnauthorizedError
        e.message?.contains("404") == true -> DeviceError.NotFoundError("device")
        e.message?.contains("network") == true -> DeviceError.NetworkError
        else -> DeviceError.UnknownError(e)
    }
}
```

---

## Testing Strategy

```kotlin
// StubDeviceApiClient.kt for testing
class StubDeviceApiClient : DeviceApiClient {
    private val stubDevices = listOf(
        Device(
            id = "device-1",
            name = "Test Desktop",
            type = DeviceType.DESKTOP,
            model = "Test PC",
            location = "San Francisco",
            lastActive = "2026-03-14T10:00:00Z",
            isVerified = true,
            capabilities = listOf("PROTEUS", "MLS"),
            fingerprint = "abc123..."
        )
    )

    override fun listMyDevices(session: AuthSession): DeviceResult {
        return DeviceResult.Success(DeviceList(stubDevices, stubDevices.size))
    }

    // ... other stub implementations
}
```

---

## Integration Points

1. **Command Registration** - Add to RootCommand subcommands
2. **Dependency Injection** - Wire DeviceService in KaliumRuntime
3. **Session Management** - Use existing AuthSession infrastructure
4. **Error Reporting** - Consistent with Presence/Profile patterns

---

## Next Steps for Full Implementation

1. Implement DeviceContracts.kt completely
2. Implement RealKaliumDeviceApiClient with full SDK runtime
3. Create SessionBackedDeviceService (business logic wrapper)
4. Create AuthGuardedDeviceService (auth wrapping)
5. Integrate DeviceCommand into RootCommand
6. Add unit tests for each component
7. Add integration tests with Kalium
8. Document device-specific CLI usage


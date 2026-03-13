# Wire-CLI Device Feature Implementation Analysis

## Overview
Based on comprehensive exploration of the wire-cli and Kalium codebase, **there is currently NO device feature implemented in wire-cli**. However, the infrastructure exists in Kalium to support device management.

---

## 1. Current Wire-CLI Architecture

### Existing Feature Modules
The wire-cli currently implements three feature modules following a consistent pattern:

1. **Presence** - User availability status
2. **Profile** - User profile information  
3. **Auth** - Authentication management

### Architecture Pattern: Contracts → Real Implementation → Command

Each feature follows this pattern:
```
FeatureName/
├── FeatureContracts.kt     # Data models and interfaces
├── RealKaliumFeatureApiClient.kt  # Kalium integration
├── StubFeatureApiClient.kt        # Mock implementation
├── FeatureService.kt              # Business logic
└── Command: FeatureCommand.kt     # CLI command
```

---

## 2. Device Repositories Available in Kalium

### ClientRepository (Core Device Data)
Located: `kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/data/client/ClientRepository.kt`

**Key Capabilities:**
```kotlin
interface ClientRepository {
    // Self device operations
    suspend fun currentClientId(): Either<CoreFailure, ClientId>
    suspend fun registerClient(param: RegisterClientParameters): Either<NetworkFailure, Client>
    suspend fun deleteClient(param: DeleteClientParam): Either<NetworkFailure, Unit>
    
    // Self devices list
    suspend fun selfListOfClients(): Either<NetworkFailure, List<Client>>
    
    // Other users' devices
    suspend fun observeClientsByUserId(userId: UserId): Flow<Either<StorageFailure, List<Client>>>
    suspend fun getClientsByUserId(userId: UserId): Either<StorageFailure, List<OtherUserClient>>
    suspend fun listClientsOfUsers(userIds: List<UserId>): NetworkResponse<Map<UserId, List<SimpleClientResponse>>>
    
    // Device verification
    suspend fun updateClientProteusVerificationStatus(
        userId: UserId,
        clientId: ClientId,
        verified: Boolean
    ): Either<StorageFailure, Unit>
    
    // MLS support
    suspend fun registerMLSClient(clientId: ClientId, publicKey: ByteArray, cipherSuite: CipherSuite): Either<CoreFailure, Unit>
    suspend fun hasRegisteredMLSClient(): Either<CoreFailure, Boolean>
}
```

### ClientApi (Network Level)
Located: `kalium/data/network/src/commonMain/kotlin/com/wire/kalium/network/api/base/authenticated/client/ClientApi.kt`

**Available Endpoints:**
```kotlin
interface ClientApi {
    suspend fun registerClient(registerClientRequest: RegisterClientRequest): NetworkResponse<ClientDTO>
    suspend fun listClientsOfUsers(userIds: List<UserId>): NetworkResponse<Map<UserId, List<SimpleClientResponse>>>
    suspend fun fetchSelfUserClient(): NetworkResponse<List<ClientDTO>>
    suspend fun deleteClient(password: String?, clientID: String): NetworkResponse<Unit>
    suspend fun fetchClientInfo(clientID: String): NetworkResponse<ClientDTO>
    suspend fun updateClientMlsPublicKeys(updateClientMlsPublicKeysRequest: UpdateClientMlsPublicKeysRequest, clientID: String): NetworkResponse<Unit>
    suspend fun updateClientCapabilities(updateClientCapabilitiesRequest: UpdateClientCapabilitiesRequest, clientID: String): NetworkResponse<Unit>
    suspend fun registerToken(body: PushTokenBody): NetworkResponse<Unit>
    suspend fun deregisterToken(pid: String): NetworkResponse<Unit>
}
```

### ClientScope (Feature Access)
Located: `kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/client/ClientScope.kt`

**Available Use Cases:**
- `fetchSelfClients` - Get current user's devices
- `fetchUsersClients` - Get other users' devices
- `getOtherUserClients` - Observe devices by user ID
- `observeClientDetailsUseCase` - Get specific device details
- `deleteClient` - Remove a device
- `getProteusFingerprint` - Get device fingerprint
- `remoteClientFingerPrint` - Get remote device fingerprint
- `updateClientVerificationStatus` - Mark device as verified

### Available Client Models
From Kalium network DTOs:
- `ClientDTO` - Full client/device information
- `SimpleClientResponse` - Lightweight client response
- `RegisterClientRequest` - Device registration parameters
- `UpdateClientCapabilitiesRequest` - Update device capabilities
- `UpdateClientMlsPublicKeysRequest` - MLS key management

**Client/Device Properties Exposed:**
```
- clientId / id
- label (device name/description)
- type (DESKTOP, MOBILE, TABLET)
- model (device model)
- location (geographic location of device)
- capabilities (protocol support: PROTEUS, MLS, etc.)
- mlsPublicKeysBundle (MLS key material)
- lastActive (last activity timestamp)
- deviceClass (classification)
- cookieLabel (session identifier)
```

---

## 3. Comparison: Presence vs. Device Features

### Presence Implementation (Existing Reference)
**File Structure:**
- `PresenceContracts.kt` - Data models
- `RealKaliumPresenceApiClient.kt` - Kalium integration
- `StubPresenceApiClient.kt` - Mock implementation
- `PresenceCommand.kt` - CLI command

**Data Model Example:**
```kotlin
data class PresenceView(
    val state: PresenceState
)

enum class PresenceState {
    AVAILABLE, AWAY, BUSY, OFFLINE
}
```

**API Methods Used:**
- `users.getSelfUser()` → availabilityStatus
- `users.updateAvailabilityStatus()` → new status

**Command Implementation:**
```
wire presence [get|set] [state]
```

### Device Feature (Would Follow Same Pattern)
**Expected File Structure:**
```
device/
├── DeviceContracts.kt
├── RealKaliumDeviceApiClient.kt
├── StubDeviceApiClient.kt
├── SessionBackedDeviceService.kt
├── AuthGuardedDeviceService.kt
└── DeviceCommand.kt
```

**Expected Data Models:**
```kotlin
data class Device(
    val id: String,           // clientId
    val name: String,         // label
    val type: DeviceType,     // DESKTOP, MOBILE, etc.
    val model: String?,
    val location: String?,
    val lastActive: Instant?,
    val isVerified: Boolean,
    val capabilities: List<String>,
    val fingerprint: String?
)

enum class DeviceType {
    DESKTOP, MOBILE, TABLET, UNKNOWN
}

data class DeviceList(
    val devices: List<Device>,
    val total: Int
)
```

**Expected API Methods:**
- `getMyDevices()` - List self devices
- `getUserDevices(userId)` - List user's devices
- `getDeviceInfo(deviceId)` - Get specific device details
- `deleteDevice(deviceId, password)` - Remove device
- `verifyDevice(userId, deviceId)` - Mark as trusted
- `updateDeviceLabel(deviceId, name)` - Rename device
- `getDeviceFingerprint()` - Get security fingerprint

**Expected Command Implementation:**
```
wire device list              # List self devices
wire device list <user-id>    # List user's devices
wire device info <device-id>  # Get device details
wire device delete <device-id> [--password xxx]
wire device verify <user-id> <device-id>
wire device rename <device-id> <new-name>
wire device fingerprint [<user-id>] [<device-id>]
wire device trust <user-id> <device-id>  # Mark trusted
```

---

## 4. Real vs. Placeholder Data Analysis

### Presence Feature (Current - Real Data)
**Real Data Points:**
- ✅ `availabilityStatus` - Actual user status from profile
- ✅ Persistence across sessions
- ✅ Real-time updates when status changes
- ✅ Kalium syncs presence from API

**Placeholder Elements:**
- None identified - fully implemented

### Device Feature (Would be Real)
Based on Kalium ClientRepository capabilities:

**Real Data Available from Server:**
- ✅ `clientId` - Unique device identifier
- ✅ `label` - Device name (user-provided)
- ✅ `type` - Device class (DESKTOP/MOBILE/TABLET)
- ✅ `model` - Device model information
- ✅ `location` - Geographic location
- ✅ `lastActive` - Last activity timestamp
- ✅ `cookieLabel` - Session token
- ✅ `capabilities` - Supported protocols (PROTEUS, MLS, etc.)

**Potentially Hardcoded/Stub Elements (if not implemented properly):**
- Device fingerprint generation (could be cached)
- Trust verification status (would need per-device tracking)
- Device metadata beyond server fields

**Data Flow:**
```
Wire Server API
      ↓
ClientApi.listClientsOfUsers() / fetchSelfUserClient()
      ↓
ClientRepository.selfListOfClients() / getClientsByUserId()
      ↓
Kalium ClientScope
      ↓
Wire-CLI DeviceCommand
      ↓
User Output
```

---

## 5. Available Use Cases in ClientScope

**Public Use Cases Available:**
```kotlin
public val fetchSelfClients: FetchSelfClientsFromRemoteUseCase
public val fetchUsersClients: FetchUsersClientsFromRemoteUseCase
public val getOtherUserClients: ObserveClientsByUserIdUseCase
public val observeClientDetailsUseCase: ObserveClientDetailsUseCase
public val deleteClient: DeleteClientUseCase
public val getProteusFingerprint: GetProteusFingerprintUseCase
public val remoteClientFingerPrint: ClientFingerprintUseCase
public val updateClientVerificationStatus: UpdateClientVerificationStatusUseCase
```

**Internal Use Cases (Could be exposed):**
```kotlin
internal val register: RegisterClientUseCase
internal val deregisterNativePushToken: DeregisterTokenUseCase
internal val clearClientData: ClearClientDataUseCase
internal val importClient: ImportClientUseCase
internal val mlsKeyPackageCountUseCase: MLSKeyPackageCountUseCase
```

---

## 6. Sync vs. Device: Key Differences

### Sync Feature (Not implemented in wire-cli)
- Related to: Nomad Device Sync API
- Endpoints: `NomadDeviceSyncApi`
- Purpose: Message synchronization across devices
- Data: Encrypted message states, crypto state

### Device Feature (Not implemented in wire-cli)
- Related to: Standard Client Management
- Endpoints: `ClientApi`
- Purpose: Device/client registration, verification, listing
- Data: Device metadata, fingerprints, capabilities

**They are SEPARATE concerns:**
- Devices = Device registration and management
- Sync = Message delivery and crypto state sync between devices

---

## 7. Implementation Roadmap for Device Feature

### Phase 1: Core Contracts
```kotlin
// device/DeviceContracts.kt
sealed class DeviceResult
data class Success(val device: Device) : DeviceResult()
data class Failure(val error: DeviceError) : DeviceResult()

data class Device(
    val id: String,
    val name: String,
    val type: String,
    val model: String?,
    val location: String?,
    val lastActive: String?,
    val isVerified: Boolean,
    val capabilities: List<String>,
    val fingerprint: String?
)

interface DeviceApiClient {
    fun listMyDevices(session: AuthSession): DeviceResult
    fun listUserDevices(session: AuthSession, userId: String): DeviceResult
    fun getDeviceInfo(session: AuthSession, deviceId: String): DeviceResult
    fun deleteDevice(session: AuthSession, deviceId: String, password: String?): DeviceResult
    fun verifyDevice(session: AuthSession, userId: String, deviceId: String): DeviceResult
    fun updateDeviceLabel(session: AuthSession, deviceId: String, newLabel: String): DeviceResult
}
```

### Phase 2: Real Kalium Implementation
```kotlin
// device/RealKaliumDeviceApiClient.kt
class RealKaliumDeviceApiClient(
    private val runtime: RealKaliumDeviceRuntime
) : DeviceApiClient {
    override fun listMyDevices(session: AuthSession): DeviceResult {
        val sessionScope = runtime.resolveSessionScope(session)
        return runtime.getSelfDevices(sessionScope).toDeviceResult()
    }
    // ... other methods
}
```

### Phase 3: Command Integration
```kotlin
// commands/DeviceCommand.kt
@Command(name = "device", description = "Manage devices")
class DeviceCommand : Runnable {
    @Command(name = "list")
    fun listDevices() { ... }
    
    @Command(name = "info")
    fun deviceInfo(@Parameters(index = "0") deviceId: String) { ... }
    
    @Command(name = "delete")
    fun deleteDevice(
        @Parameters(index = "0") deviceId: String,
        @Option(names = ["--password"]) password: String?
    ) { ... }
}
```

---

## 8. Key Findings Summary

| Aspect | Status | Notes |
|--------|--------|-------|
| **Device Feature** | ❌ Not Implemented | No device package in wire-cli |
| **Kalium Support** | ✅ Full Support | ClientRepository & ClientApi complete |
| **Available Data** | ✅ Complete | All device fields available from server |
| **Architecture** | ✅ Ready | Pattern exists in Presence/Profile |
| **Real vs. Placeholder** | ✅ Real Data | No stubs needed - server provides everything |
| **Use Cases** | ✅ Available | ClientScope exposes all needed functions |
| **Network Endpoints** | ✅ Implemented | ClientApi v0-v14 with full methods |

---

## 9. What Data Would Device Feature Return

### Self Devices Example
```json
{
  "devices": [
    {
      "id": "5c3b67e8d4a2f1e9",
      "name": "My MacBook Pro",
      "type": "DESKTOP",
      "model": "MacBook Pro 16\"",
      "location": "San Francisco, CA",
      "lastActive": "2026-03-14T10:23:45Z",
      "isVerified": true,
      "capabilities": ["PROTEUS", "MLS"],
      "fingerprint": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    },
    {
      "id": "a1b2c3d4e5f6g7h8",
      "name": "My iPhone",
      "type": "MOBILE",
      "model": "iPhone 15 Pro",
      "location": "San Francisco, CA",
      "lastActive": "2026-03-14T09:15:30Z",
      "isVerified": true,
      "capabilities": ["PROTEUS", "MLS"],
      "fingerprint": "d4af3fc02b94ad0f5a89c8f3b4e2e5d8f2e8a7c9b5f3d1e6a8c0f2e4d6a8b"
    }
  ],
  "total": 2
}
```

### Other User's Devices Example
```json
{
  "userId": "user@example.com",
  "devices": [
    {
      "id": "abc123xyz789",
      "name": "Alice's Desktop",
      "type": "DESKTOP",
      "model": "Windows 11",
      "isVerified": true,
      "capabilities": ["PROTEUS", "MLS"],
      "fingerprint": "f8e2a5d9c7b4f1e8d6a3c5b2f9e7d4a1..."
    }
  ]
}
```

---

## Conclusion

The device feature is **feasible and straightforward to implement** in wire-cli using the proven pattern from Presence/Profile modules. The Kalium library provides complete backend support with:

- Full ClientRepository with all necessary methods
- Complete ClientApi for network operations
- Proper use cases already exposed in ClientScope
- Real device data from Wire servers (no placeholders needed)

The implementation would be a natural extension of wire-cli's existing architecture and would provide users with comprehensive device management capabilities.

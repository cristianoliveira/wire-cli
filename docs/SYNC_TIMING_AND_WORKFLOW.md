# Sync Timing in Wire-CLI Workflow

**Document Date**: March 16, 2026  
**Status**: Complete Analysis  
**Purpose**: Understand when sync happens in CLI commands and how to mimic wiretool's behavior

## Quick Answer

**In wire-cli, sync is NOT explicitly triggered by commands.** Instead, sync happens **automatically and lazily** when you first access Kalium data:

1. **On first data access** (e.g., `wire profile`, `wire presence get`, `wire conversation list`):
   - Kalium automatically triggers sync on session scope initialization
   - By default, it **waits for sync to reach "Live" state** before returning control

2. **Optional control via environment variable**:
   - Set `WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT=true` to **skip the sync wait** and return immediately
   - Useful for fast commands when you don't need fresh data

3. **No explicit sync command yet**:
   - `wire doctor` (sync status/diagnostics) reads sync state but doesn't trigger it
   - Message sending/receiving happens independently in background (not in CLI lifecycle)

---

## Wire-CLI Command Timeline

### Login Flow

```
$ wire login --email alice@example.com

1. User enters password (interactive prompt or stdin)
   ↓
2. RealKaliumAuthClient.login()
   └─ Create Kalium CoreLogic (lazy initialization)
   └─ POST /login (email + password)
   └─ Receive access token + refresh token
   ↓
3. Persist account to ~/.wire/kalium/ (Kalium's database)
   ├─ accountStore.addAuthenticatedAccount()
   └─ Server config, tokens, user ID stored locally
   ↓
4. Register device (client fingerprint)
   └─ Ensures this CLI instance is a recognized device
   ├─ Proteus: Get pre-keys, establish sessions
   └─ MLS: Get key packages
   ↓
5. Return session (userId, accessToken)
   ↓
Output: "Login successful ✓"
Exit: 0

⚠️  NO SYNC HAS HAPPENED YET
    (SlowSync + IncrementalSync haven't run)
    (Data is empty in Kalium's local database)
```

**Key:** Login doesn't trigger sync. It only authenticates and registers the device.

### Profile Command

```
$ wire profile

1. RootCommand routes to ProfileCommand
   ↓
2. ProfileCommand.run()
   └─ profileService.getCurrentProfile()
   ↓
3. SessionBackedProfileService.getCurrentProfile()
   └─ Read active session from local store
   └─ Call RealKaliumProfileApiClient.fetchProfile(session)
   ↓
4. SdkKaliumProfileRuntime.resolveSessionScope()
   ├─ THIS IS WHERE SYNC HAPPENS:
   │  ├─ coreLogic.sessionScope(userId) {
   │  │   if (!disableSessionSyncWait) {
   │  │      syncExecutor.request { waitUntilLiveOrFailure() }
   │  │   }
   │  │ }
   │  └─ Blocks here until sync reaches "Live" state
   │
   ├─ SlowSync executes (5-30s first time)
   │  └─ Fetch conversations, users, cryptographic state
   │
   ├─ IncrementalSync executes (0-10s typically)
   │  └─ Fetch missed events since last checkpoint
   │
   └─ Sync reaches "Live" state
      └─ Client is now up-to-date with server
   ↓
5. SdkKaliumProfileRuntime.getSelfUser()
   └─ Query local database for self user
   └─ (Data is now available because sync completed)
   ↓
6. Return profile (name, email, handle)
   ↓
Output:
  Name: Alice Smith
  Email: alice@example.com
  Handle: asmith
  Presence: online
Exit: 0

✅ SYNC HAS HAPPENED (and completed)
   SlowSync + IncrementalSync ran, data is synchronized
```

**Key:** First data access triggers sync and **waits for it to complete**. The CLI blocks until sync = Live.

### Presence Get Command

```
$ wire presence get

Same flow as ProfileCommand:
1. Resolve session scope
   └─ Triggers sync (waitUntilLiveOrFailure)
   └─ Blocks until Live
2. Query local database for presence status
3. Return presence

⏱️  Takes 5-40 seconds on first run (SlowSync time)
    Takes < 1 second on subsequent runs (sync already done)
```

### Presence Set Command

```
$ wire presence set online

1. Resolve session scope
   └─ Triggers sync (waitUntilLiveOrFailure)
   └─ Blocks until Live
2. Call Kalium: users.updateSelfAvailabilityStatus(AVAILABLE)
3. Return success

⏱️  Takes 5-40 seconds first time, < 1 second after
```

### Conversation List Command

```
$ wire conversation list

1. Resolve session scope
   └─ Triggers sync (waitUntilLiveOrFailure)
   └─ Blocks until Live
2. Query local database for all conversations
3. Return list

Example output:
  ID                    | Name               | Type       | Members
  ────────────────────────────────────────────────────────────────
  conv-123@example.com  | Alice Smith        | ONE_TO_ONE | 2
  conv-456@example.com  | Team Engineering   | GROUP      | 15
```

### Sync Status Command (Doctor)

```
$ wire doctor status

1. Resolve session scope
   └─ Does NOT trigger sync (special case)
   └─ Instead: observeSyncState().firstOrNull()
   └─ Returns current sync state WITHOUT waiting

2. Map sync state to status:
   - SyncState.Live → Status = "ready" (lag = 0ms)
   - SyncState.SlowSync → Status = "initializing"
   - SyncState.GatheringPendingEvents → Status = "initializing"
   - SyncState.Failed → Status = "degraded"

Example output (if currently syncing):
  Status: initializing
  Lag: 5000ms
  Pending: 50 messages
  MLS: 0%
  Exit: 1

Example output (if live):
  Status: ready
  Lag: 0ms
  Pending: 0 messages
  MLS: 100%
  Exit: 0

✅ This command is FAST (non-blocking)
   It doesn't wait for sync, just reports current state
```

---

## How Wiretool Handles Sync

Wire-cli's approach is based on Kalium's design. Here's how wiretool (the iOS/Desktop app) handles it:

### In Wiretool (App Model)

```
Application Launch
  ↓
1. Foreground: Initialize Kalium CoreLogic
   └─ Lazy-loaded, like wire-cli
   ↓
2. Background: Start sync
   ├─ SlowSync if first login
   ├─ IncrementalSync continuously
   └─ Runs in background thread/task
   ↓
3. UI: Show "Syncing..." until sync = Live
   └─ Once Live, show conversations, messages, etc.
   ↓
4. User can navigate while sync is still running
   ├─ View conversations → works if conversation already loaded
   ├─ View messages → may show partial data during sync
   └─ Send message → encrypts and queues locally, sends in background
   ↓
5. Sync completes → UI updates with full data
```

### In Wire-CLI (CLI Model)

```
$ wire <command>

1. Initialize Kalium CoreLogic (lazy)
   ↓
2. For data access commands (profile, presence, conversation list):
   └─ BLOCK and wait for sync to reach Live
   └─ Then return complete, up-to-date data
   
3. For status commands (doctor):
   └─ Return current state WITHOUT waiting
   └─ Fast, non-blocking

4. Command exits
   └─ Clean up Kalium resources
   └─ Deterministic shutdown
```

---

## Sync Timing Behavior Matrix

| Command | Sync Triggered? | Waits for Live? | Typical Duration |
|---------|---|---|---|
| `wire login` | ❌ No | ❌ No | < 5s |
| `wire profile` | ✅ Yes | ✅ Yes | 5-40s (first), < 1s (after) |
| `wire presence get` | ✅ Yes | ✅ Yes | 5-40s (first), < 1s (after) |
| `wire presence set` | ✅ Yes | ✅ Yes | 5-40s (first), < 1s (after) |
| `wire conversation list` | ✅ Yes | ✅ Yes | 5-40s (first), < 1s (after) |
| `wire conversation get <id>` | ✅ Yes | ✅ Yes | 5-40s (first), < 1s (after) |
| `wire doctor status` | ✅ Yes | ❌ No (observes only) | < 100ms |
| `wire doctor diagnose` | ✅ Yes | ❌ No (observes only) | < 100ms |
| `wire logout` | ❌ No | ❌ No | < 1s |

---

## Environment Variable Control

### `WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT`

**Default**: `false` (sync waits enabled)

**Effect**: When set to `true`, skip the `waitUntilLiveOrFailure()` call

**Code location** (in `RealKaliumProfileApiClient`, `RealKaliumPresenceApiClient`):
```kotlin
if (!cliMode.disableSessionSyncWait) {
    coreLogic.sessionScope(qualifiedId) {
        syncExecutor.request { waitUntilLiveOrFailure() }
    }
}
```

**Usage example**:
```bash
# Default: wait for sync to complete
$ wire profile
# Takes 5-40s on first run

# Skip sync wait: return immediately with whatever data exists
$ WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT=true wire profile
# Takes < 1s (but data may be incomplete/stale)
```

**When to use**:
- ✅ Testing (you don't want to wait)
- ✅ CI/CD pipelines (timeout concerns)
- ✅ Quick status checks (you accept stale data)
- ❌ Production workflows (may return incomplete data)

---

## Actual Code Flow: Profile Command (Step-by-Step)

Here's the exact execution path:

### 1. User Invokes Command
```bash
$ wire profile
```

### 2. Main.kt Entry Point
```kotlin
fun main(args: Array<String>) {
    val runtime = KaliumRuntimeBootstrap.create()
    
    RootCommand()
        .subcommands(
            ProfileCommand { runtime.profileService },
            ...
        )
        .main(args)  // ← Parses "profile" and routes
    
    runtime.close()  // ← Cleanup at process exit
}
```

### 3. ProfileCommand Routes to Service
```kotlin
class ProfileCommand(
    private val profileServiceProvider: () -> ProfileService
) {
    override fun run() {
        val profileService = profileServiceProvider()
        when (val result = profileService.getCurrentProfile()) {  // ← Call here
            is ProfileResult.Success -> echo("Name: ${result.profile.name}")
            is ProfileResult.Failure -> echo(result.message, err = true)
        }
    }
}
```

### 4. SessionBackedProfileService
```kotlin
class SessionBackedProfileService(
    private val apiClient: ProfileApiClient
) : ProfileService {
    override fun getCurrentProfile(): ProfileResult {
        val session = sessionStore.readActiveSession()
        return when (val profileResult = apiClient.fetchProfile(session)) {  // ← Call API client
            is ProfileResult.Success -> {
                // Merge with presence data
                ProfileResult.Success(profile.copy(presence = resolvePresence(session)))
            }
            is ProfileResult.Failure -> profileResult
        }
    }
}
```

### 5. RealKaliumProfileApiClient
```kotlin
class RealKaliumProfileApiClient(
    private val runtime: RealKaliumProfileRuntime
) : ProfileApiClient {
    override fun fetchProfile(session: AuthSession): ProfileResult {
        val sessionScope = runtime.resolveSessionScope(session)  // ← SYNC HAPPENS HERE
        return when (val selfUser = runtime.getSelfUser(sessionScope)) {
            is ProfileStepResult.Success -> ProfileResult.Success(...)
            is ProfileStepResult.Failure -> selfUser.toProfileFailure()
        }
    }
}
```

### 6. SdkKaliumProfileRuntime.resolveSessionScope() - **SYNC TRIGGER**
```kotlin
class SdkKaliumProfileRuntime : RealKaliumProfileRuntime {
    override fun resolveSessionScope(session: AuthSession): ProfileStepResult<...> {
        val qualifiedId = session.userId.toQualifiedId()
        
        return runBlocking {
            try {
                // ⬇️  THIS IS WHERE SYNC HAPPENS ⬇️
                if (!cliMode.disableSessionSyncWait) {
                    coreLogic.sessionScope(qualifiedId) {
                        syncExecutor.request { 
                            waitUntilLiveOrFailure()  // ← BLOCKS HERE
                        }
                    }
                }
                // ⬆️  AFTER THIS RETURNS, SYNC IS COMPLETE ⬆️
                
                ProfileStepResult.Success(...)
            } catch (error: Throwable) {
                ProfileStepResult.Failure(...)
            }
        }
    }
}
```

**What `waitUntilLiveOrFailure()` does** (in Kalium):
```
while (true) {
    val syncState = observeSyncState().firstOrNull()
    
    when (syncState) {
        is SyncState.Live -> return success()  // ← Return here
        is SyncState.SlowSync -> continue
        is SyncState.GatheringPendingEvents -> continue
        is SyncState.Waiting -> continue
        is SyncState.Failed -> return failure()  // ← Or here
    }
}
```

### 7. Once Sync Complete, Query Database
```kotlin
class SdkKaliumProfileRuntime {
    override fun getSelfUser(sessionScope: ...): ProfileStepResult<...> {
        val qualifiedId = sessionScope.userId.toQualifiedId()
        
        return runBlocking {
            val selfUser: SelfUser = coreLogic.sessionScope(qualifiedId) {
                users.getSelfUser()  // ← Query local database
                                    // (data now available because sync completed)
            }
            ProfileStepResult.Success(KaliumSelfUser(...))
        }
    }
}
```

### 8. Return to User
```
Name: Alice Smith
Email: alice@example.com
Handle: asmith
Presence: online
```

---

## Mimicking Wiretool Behavior in Wire-CLI

If you want wire-cli to behave more like the iOS/Desktop app, consider:

### Current Model (CLI)
- **Synchronous**: Command blocks until sync completes
- **Deterministic**: Each command shows complete, up-to-date data
- **Simple**: No background threads to manage

### Wiretool Model (App)
- **Asynchronous**: Sync runs in background while app is usable
- **Progressive**: UI shows partial data, updates as sync completes
- **Complex**: Need background sync thread + UI update callbacks

### If You Want Async Behavior in CLI

```kotlin
// Hypothetical future design:

class AsyncProfileService {
    // Start sync in background (non-blocking)
    fun startSync(): Deferred<Unit> = globalScope.async {
        coreLogic.sessionScope(userId) {
            syncExecutor.request { waitUntilLiveOrFailure() }
        }
    }
    
    // Get current profile (may be partial if sync in progress)
    fun getCurrentProfile(): ProfileResult {
        val selfUser = coreLogic.sessionScope(userId) {
            users.getSelfUser()
        }
        return ProfileResult.Success(selfUser.toProfile())
    }
    
    // Usage in command:
    // val syncJob = startSync()  // Start in background
    // val profile = getCurrentProfile()  // Get whatever is available
    // echo(profile)
    // syncJob.join()  // Wait for background sync (optional)
}
```

**But this adds complexity:**
- ❌ Dealing with partial data
- ❌ Race conditions (update local DB while querying)
- ❌ User confusion (why is data different on second run?)

**Current design is better for CLI** because:
- ✅ Simple contract: "Run this command, get complete data"
- ✅ No surprises: Same command always returns same data
- ✅ Easy to test: Deterministic behavior
- ✅ Can skip sync with `WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT` if needed

---

## Performance Implications

### First Run (After Login)
```
$ wire profile
# SlowSync: 5-30 seconds
#   - Fetch conversations, users, team, cryptography
# IncrementalSync: 1-10 seconds
#   - Fetch pending events
# Total: 6-40 seconds
```

### Subsequent Runs (Same Session)
```
$ wire profile
# SlowSync: SKIPPED (already did it)
# IncrementalSync: 0-2 seconds
#   - Fetch events since last sync
#   - Usually 0 events (connection already live)
# Total: < 1 second
```

### With Sync Wait Disabled
```
$ WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT=true wire profile
# First run: < 100ms (but data is empty/stale)
# Subsequent: < 100ms (returns whatever is in local DB)
```

---

## Summary Table: When Sync Happens

| Trigger | Timing | Behavior |
|---------|--------|----------|
| Login | Never | Registers device only |
| First data access (profile/presence/conversation) | Automatic | Blocks until Live |
| Second+ data access (same session) | Automatic | Minimal wait (catch-up only) |
| Doctor commands | Never (observes current state) | Non-blocking |
| Session timeout (> 30 days offline) | On next data access | Full SlowSync restart |
| Network disconnect/reconnect | Continuous (background) | Auto-retries, catches up |

---

## Files to Reference

**Sync triggering:**
- `src/main/kotlin/wirecli/profile/RealKaliumProfileApiClient.kt:100` - `resolveSessionScope()` calls `waitUntilLiveOrFailure()`
- `src/main/kotlin/wirecli/presence/RealKaliumPresenceApiClient.kt:116` - Same pattern for presence

**Environment variable handling:**
- `src/main/kotlin/wirecli/runtime/KaliumCliMode.kt` - Parses `WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT`

**Kalium sync orchestration:**
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/SyncManager.kt` - Orchestrates SlowSync + IncrementalSync
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/slow/SlowSyncManager.kt` - Phase 1
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/incremental/IncrementalSyncManager.kt` - Phase 2

---

**Document Status**: Complete  
**Last Updated**: March 16, 2026

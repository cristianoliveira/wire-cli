# Sync Workflow Diagrams

Visual representations of when and how sync happens in wire-cli.

## 1. Complete CLI Lifecycle with Sync

```
┌─────────────────────────────────────────────────────────────────────┐
│                        WIRE-CLI LIFECYCLE                           │
└─────────────────────────────────────────────────────────────────────┘

    $ wire login --email alice@example.com
           ↓
    ┌──────────────────────────────────┐
    │ 1. AuthenticationCommand.run()    │
    │    - Prompt for password          │
    │    - POST /login                  │
    │    - Register device              │
    │    - Persist tokens               │
    │                                   │
    │ ❌ NO SYNC HAPPENS HERE           │
    │ (Just auth + device registration) │
    └──────────────────────────────────┘
           ↓
    Output: "Login successful ✓"
    
    
    $ wire profile
           ↓
    ┌──────────────────────────────────┐
    │ 2. ProfileCommand.run()           │
    │    - Resolve session              │
    │                                   │
    │    ✅ SYNC TRIGGERED HERE:        │
    │    - coreLogic.sessionScope() {   │
    │      waitUntilLiveOrFailure()     │ ← BLOCKS
    │    }                              │
    │                                   │
    │    Sync Phases:                   │
    │    ├─ SlowSync (5-30s)            │
    │    │  └─ Fetch metadata           │
    │    ├─ IncrementalSync (1-10s)     │
    │    │  └─ Fetch pending events     │
    │    └─ State = Live                │
    │                                   │
    │    - Query local DB               │
    │    - Return profile               │
    └──────────────────────────────────┘
           ↓
    Output: Name, email, handle, presence
    
    
    $ wire conversation list
           ↓
    ┌──────────────────────────────────┐
    │ 3. ConversationListCommand.run()  │
    │                                   │
    │    ✅ SYNC TRIGGERED AGAIN        │
    │    - resolveSessionScope() calls  │
    │      waitUntilLiveOrFailure()     │
    │                                   │
    │    ⚡ FAST THIS TIME (< 1s)       │
    │    - Sync already completed       │
    │    - Just fetch events since      │
    │      last checkpoint              │
    │                                   │
    │    - Query all conversations      │
    │    - Return list                  │
    └──────────────────────────────────┘
           ↓
    Output: Table of conversations


    $ wire logout
           ↓
    ┌──────────────────────────────────┐
    │ 4. LogoutCommand.run()            │
    │    - POST /logout                 │
    │    - Clear local tokens           │
    │                                   │
    │ ❌ NO SYNC HAPPENS HERE           │
    │ (Sync is invalidated anyway)      │
    └──────────────────────────────────┘
           ↓
    Output: "Logout successful ✓"
```

---

## 2. Sync Triggering: Data Access Commands vs Status Commands

```
┌──────────────────────────────────────────────────────────────┐
│         SYNC TRIGGERING PATTERNS                             │
└──────────────────────────────────────────────────────────────┘

DATA ACCESS COMMANDS (Block for Sync)
════════════════════════════════════

    wire profile
        ↓
    SessionBackedProfileService.getCurrentProfile()
        ↓
    RealKaliumProfileApiClient.fetchProfile()
        ↓
    SdkKaliumProfileRuntime.resolveSessionScope()
        ├─ ✅ TRIGGERS SYNC
        ├─ coreLogic.sessionScope(qualifiedId) {
        │   syncExecutor.request { waitUntilLiveOrFailure() }
        │ }
        │
        ├─ SlowSync: ▓▓▓▓░░░░░░  5-30s
        ├─ IncrementalSync: ▓▓░░░░░░░░  1-10s
        └─ When done: SyncState = Live
        ↓
    SdkKaliumProfileRuntime.getSelfUser()
        ├─ Query local DB (now populated)
        └─ Return data
        ↓
    ProfileCommand.run()
        └─ Output profile
        ↓
    ✅ Total: 6-40s (first run), < 1s (after)


STATUS COMMANDS (Non-Blocking)
══════════════════════════════

    wire doctor status
        ↓
    SyncCommand.run()
        ↓
    SyncService.getCurrentSyncStatus()
        ├─ ❌ Does NOT trigger sync
        ├─ Instead: observeSyncState().firstOrNull()
        │   (Reads current state, doesn't wait)
        └─ Maps state to SyncStatus + metrics
        ↓
    SyncOutputFormatter.formatStatusHuman()
        └─ Print status
        ↓
    ✅ Total: < 100ms (instantaneous)
```

---

## 3. Sync State Machine During CLI Execution

```
┌──────────────────────────────────────────────────────────────┐
│  SYNC STATE TRANSITIONS (When CLI Command Accesses Data)     │
└──────────────────────────────────────────────────────────────┘

FIRST RUN (wire profile)
════════════════════════

    [Waiting] ← Initial state on session init
        ↓
    CLI calls: waitUntilLiveOrFailure()
        ↓
    [SlowSync] ← Kalium automatically starts
    ├─ Fetch self user
    ├─ Fetch conversations (100+)
    ├─ Fetch all users
    ├─ Establish Proteus sessions
    ├─ Join MLS groups
    └─ Save checkpoint
    │
    ├─ ▓▓▓▓▓░░░░░  5-30 seconds
    ↓
    [GatheringPendingEvents] ← Transition happens automatically
    ├─ Fetch events since checkpoint
    ├─ Process events
    └─ When no more events: transition to Live
    │
    ├─ ▓▓░░░░░░░░  1-10 seconds
    ↓
    [Live] ← Sync complete
    ├─ In real-time event stream (WebSocket)
    └─ Continues indefinitely
    │
    ↓ Return to CLI: waitUntilLiveOrFailure() SUCCEEDS
    
    CLI can now query database: users.getSelfUser()
    ✅ Returns fresh data


SECOND RUN (wire profile)
═════════════════════════

    [Live] ← Already synced from previous run
        ↓
    CLI calls: waitUntilLiveOrFailure()
        ↓
    Already in Live state → Return immediately
    │
    ├─ ▓░░░░░░░░░  < 1 second
    ↓
    CLI can query database
    ✅ Returns data (same as before)


IF NETWORK GLITCH
═════════════════

    [Live]
        ↓ (network error)
        ↓
    [GatheringPendingEvents] ← Auto-recover
    ├─ Resume from checkpoint
    ├─ Fetch missed events
    ├─ When caught up: transition to Live
    │
    ├─ ▓▓░░░░░░░░  1-5 seconds (auto-recovery)
    ↓
    [Live] ← Back to normal


IF OFFLINE > 30 DAYS
════════════════════

    [Live]
        ↓ (offline for 40+ days)
        ↓
    Next CLI command
        ↓
    [Failed] ← Kalium detects event window exceeded
        ↓
    Auto-restart SlowSync
        ├─ [SlowSync] → [GatheringPendingEvents] → [Live]
        │
        ├─ ▓▓▓▓▓░░░░░  5-30 seconds (full re-sync)
        ↓
    [Live]
    ✅ Data rebuilt from scratch
```

---

## 4. Code Path: Where Sync is Triggered

```
┌─────────────────────────────────────────────────────────────────┐
│  CODE EXECUTION PATH - SYNC TRIGGERING                          │
└─────────────────────────────────────────────────────────────────┘

$ wire profile

    main()
    └─ RootCommand().subcommands(ProfileCommand).main(args)
       └─ ProfileCommand.run()
          └─ profileService.getCurrentProfile()
             └─ SessionBackedProfileService.getCurrentProfile()
                └─ apiClient.fetchProfile(session)
                   └─ RealKaliumProfileApiClient.fetchProfile()
                      │
                      ├─ runtime.resolveSessionScope(session)
                      │  └─ SdkKaliumProfileRuntime.resolveSessionScope()
                      │     │
                      │     ├─ runBlocking {
                      │     │   if (!cliMode.disableSessionSyncWait) {
                      │     │     coreLogic.sessionScope(qualifiedId) {
                      │     │       syncExecutor.request {
                      │     │         waitUntilLiveOrFailure()  ← ✅ SYNC TRIGGERED
                      │     │       }
                      │     │     }
                      │     │   }
                      │     │ }
                      │     │
                      │     │ ⏸️  BLOCKS HERE UNTIL SYNC COMPLETES
                      │     │    SlowSync + IncrementalSync run
                      │     │    Client becomes "Live"
                      │     │ ⏸️  THEN RETURNS
                      │     │
                      │     └─ return Success(sessionScope)
                      │
                      └─ runtime.getSelfUser(sessionScope)
                         └─ SdkKaliumProfileRuntime.getSelfUser()
                            └─ coreLogic.sessionScope(qualifiedId) {
                                 users.getSelfUser()  ← Query local DB
                               }
                            └─ return SelfUser(name, email, handle)
                      
                      └─ return ProfileResult.Success(profile)

                   └─ echo("Name: ${profile.name}")
                      echo("Email: ${profile.email}")
                      echo("Handle: ${profile.handle}")


ENVIRONMENT CONTROL

If: WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT=true

    Same code path, BUT:
    
    ├─ runBlocking {
    │   if (!cliMode.disableSessionSyncWait) {  ← FALSE
    │     // SKIPPED
    │   }
    │ }
    
    └─ Skip the waitUntilLiveOrFailure() call
    └─ Return immediately
    └─ Query whatever is in local DB (may be empty/stale)
    └─ Total time: < 100ms
```

---

## 5. Timeline Comparison: Wiretool vs Wire-CLI

```
┌────────────────────────────────────────────────────────────┐
│  WIRETOOL (iOS/Desktop App) vs WIRE-CLI (CLI)             │
└────────────────────────────────────────────────────────────┘

WIRETOOL
════════

    App Launch
    ↓
    1. Initialize Kalium (lazy)
    2. Start sync in BACKGROUND thread
    3. Show "Syncing..." overlay
    4. User can tap conversations → shows empty/partial list
    5. Sync progresses: SlowSync → IncrementalSync → Live
    6. UI updates progressively as sync completes
    7. Full data available when sync = Live
    8. App runs indefinitely


WIRE-CLI
════════

    $ wire login
    ↓
    1. Initialize Kalium (lazy)
    2. Authenticate, register device
    3. Return immediately (❌ no sync)
    ↓ Prompt: $


    $ wire profile
    ↓
    1. Resolve session scope
    2. ✅ TRIGGER SYNC (BLOCKING)
    3. SlowSync + IncrementalSync run
    4. Wait until sync = Live (can be 5-40s)
    5. Query database (now populated)
    6. Print profile
    7. Exit command
    ↓ Prompt: $


KEY DIFFERENCES
═══════════════

Wiretool:
  - Sync in BACKGROUND thread
  - UI updates PROGRESSIVELY
  - App ALWAYS RUNNING
  - User sees partial data during sync

Wire-CLI:
  - Sync is BLOCKING
  - Returns COMPLETE data
  - Process EXITS after command
  - User sees complete or nothing

If you want async behavior in wire-cli:
  → Set WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT=true
  → Sync runs in background (inside Kalium)
  → Return data immediately (may be stale)
  → Trade latency for completeness
```

---

## 6. Decision Tree: Which Command Triggers Sync?

```
┌────────────────────────────────────────────────────────────┐
│  DECISION TREE: SYNC TRIGGERING                            │
└────────────────────────────────────────────────────────────┘

Does this command need DATA from database?
│
├─ YES: wire profile
│   ├─ Needs: self user profile
│   └─ ✅ TRIGGERS SYNC (blocks until Live)
│
├─ YES: wire presence get
│   ├─ Needs: self user availability status
│   └─ ✅ TRIGGERS SYNC (blocks until Live)
│
├─ YES: wire presence set <status>
│   ├─ Needs: authorization check first
│   └─ ✅ TRIGGERS SYNC (blocks until Live)
│
├─ YES: wire conversation list
│   ├─ Needs: all conversations
│   └─ ✅ TRIGGERS SYNC (blocks until Live)
│
├─ YES: wire conversation get <id>
│   ├─ Needs: conversation details
│   └─ ✅ TRIGGERS SYNC (blocks until Live)
│
├─ NO: wire login
│   ├─ Just: authenticate, register device
│   └─ ❌ NO SYNC (data not needed)
│
├─ NO: wire logout
│   ├─ Just: invalidate tokens
│   └─ ❌ NO SYNC (data not needed)
│
├─ NO: wire doctor status
│   ├─ Returns: current sync state (not database)
│   └─ ❌ NO SYNC WAIT (observes only, non-blocking)
│
└─ NO: wire doctor diagnose
    ├─ Returns: health checks (not database)
    └─ ❌ NO SYNC WAIT (observes only, non-blocking)


PATTERN
═══════

  If command calls:
    → resolveSessionScope(session)
       with cliMode.disableSessionSyncWait == false
      then ✅ SYNC IS TRIGGERED

  If command calls:
    → observeSyncState().firstOrNull()
      then ❌ NO SYNC (just observes current state)
```

---

## 7. Sync Duration: First vs Subsequent Runs

```
┌────────────────────────────────────────────────────────────┐
│  PERFORMANCE: COLD vs WARM START                           │
└────────────────────────────────────────────────────────────┘

COLD START (First ever command after login)
════════════════════════════════════════════

    $ wire profile

    Execution trace:
    ├─ 0-1s:   Startup (parse CLI, initialize)
    ├─ 1-2s:   Initialize Kalium CoreLogic (lazy load)
    ├─ 2-3s:   Authenticate session (internal token check)
    ├─ 3-35s:  SLOWSYNC
    │          ├─ Fetch self user
    │          ├─ Fetch conversations (may be 100+)
    │          ├─ Fetch users (all members)
    │          ├─ Fetch team info
    │          ├─ Establish crypto sessions
    │          └─ Save checkpoint
    │          │
    │          Graph: ▓▓▓▓▓▓▓░░░░░  10-30 seconds
    │
    ├─ 35-45s: INCREMENTALSYNC
    │          ├─ Fetch events since checkpoint
    │          ├─ Process events
    │          └─ Reach "Live" state
    │          │
    │          Graph: ▓▓░░░░░░░░░  1-10 seconds
    │
    ├─ 45-46s: Query database + format output
    └─ 46s:    Output results
    
    Total: 45-50 seconds


WARM START (Subsequent commands, same session)
═══════════════════════════════════════════════

    $ wire presence get

    Execution trace:
    ├─ 0-1s:   Startup (parse CLI, initialize)
    ├─ 1-2s:   Initialize Kalium CoreLogic (reuse, cached)
    ├─ 2-3s:   Authenticate session (instant, cached)
    ├─ 3-4s:   Sync check
    │          ├─ Already in "Live" state
    │          ├─ Resume from last checkpoint
    │          ├─ Check for missed events
    │          └─ Usually 0 events (we're caught up)
    │          │
    │          Graph: ▓░░░░░░░░░░  < 1 second
    │
    ├─ 4-5s:   Query database + format output
    └─ 5s:     Output results
    
    Total: 4-6 seconds


WITH SYNC WAIT DISABLED
═══════════════════════

    $ WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT=true wire profile

    Execution trace:
    ├─ 0-1s:   Startup + initialize
    ├─ 1-2s:   Skip sync wait
    ├─ 2-3s:   Query database (whatever is there)
    │          ├─ First time: empty database
    │          ├─ Subsequent: whatever was synced before
    │          │
    │          Graph: ▓░░░░░░░░░░  < 1 second
    │
    ├─ 3-4s:   Format output
    └─ 4s:     Output results
    
    Total: 3-5 seconds (regardless of sync status)
    
    ⚠️  Data may be incomplete or stale!
    ✅ But much faster


COMPARATIVE TABLE
═════════════════

Scenario                        Time      Data Quality
────────────────────────────────────────────────────────
First command (cold start)      45-50s    ✅ Complete
Second+ command (warm start)    4-6s      ✅ Complete
With DISABLE_SYNC_WAIT (1st)    3-5s      ❌ Empty/stale
With DISABLE_SYNC_WAIT (2nd+)   3-5s      ⚠️  Partial/stale
```

---

## 8. Error Scenarios and Recovery

```
┌────────────────────────────────────────────────────────────┐
│  ERROR SCENARIOS & AUTOMATIC RECOVERY                      │
└────────────────────────────────────────────────────────────┘

SCENARIO 1: Network Error During Sync
══════════════════════════════════════

    $ wire profile

    Execution:
    ├─ SlowSync in progress
    ├─ Network error occurs (timeout, DNS, etc.)
    │
    ├─ Kalium catches error
    ├─ Sync state: Failed → Waiting
    ├─ Triggers exponential backoff retry
    │  ├─ Retry 1: wait 1s, then retry
    │  ├─ Retry 2: wait 2s, then retry
    │  ├─ Retry 3: wait 4s, then retry
    │  └─ Continue until success or max retries
    │
    ├─ waitUntilLiveOrFailure() waits during retries
    ├─ Eventually succeeds or fails
    │
    └─ CLI either returns data or error message
    
    User experience:
    $ wire profile
    ← (appears frozen, 30-60s)
    → Either returns profile OR error
    
    Recovery: Automatic, user just waits


SCENARIO 2: Offline for 40 Days, Then Reconnect
════════════════════════════════════════════════

    Session created: Jan 1
    Machine offline: Jan 1 - Feb 10 (40 days)
    Next CLI command: Feb 10

    $ wire profile

    Execution:
    ├─ Kalium loads session
    ├─ Attempts to resume from last checkpoint (Jan 1)
    ├─ Server responds: "Checkpoint outside event window"
    │  └─ Error: SyncEventOrClientNotFound
    │
    ├─ Kalium detects offline condition
    ├─ Automatically triggers SlowSync (not IncrementalSync)
    ├─ Full state rebuild (5-30 seconds)
    │
    └─ Then IncrementalSync catches up (40 days of events? Or default retention)
    
    User experience:
    $ wire profile
    ← (takes longer than usual: 10-60 seconds)
    → Eventually returns profile with full state rebuilt
    
    Recovery: Automatic, user waits longer


SCENARIO 3: Invalid/Expired Session Token
══════════════════════════════════════════

    User logged in 6 months ago
    Token expired on server
    Next CLI command today

    $ wire profile

    Execution:
    ├─ Kalium loads token from ~/.wire/kalium/
    ├─ Attempts authentication with token
    ├─ Server responds: 401 Unauthorized
    │  └─ Token expired
    │
    ├─ Kalium has no refresh token (depends on implementation)
    ├─ Session is invalidated
    ├─ Error: UnauthorizedRequest
    │
    └─ CLI returns: "Your session is invalid or expired. Please log in again."
    
    User experience:
    $ wire profile
    Error: Your session is invalid or expired. Please log in again.
    
    Recovery: Manual, user must `wire login` again


SCENARIO 4: MLS Group Out of Sync
══════════════════════════════════

    During IncrementalSync:
    ├─ Receive message from MLS group
    ├─ Group tree out of date (missed epoch update)
    ├─ Kalium detects: MLSGroupOutOfSync
    │
    ├─ Error handling:
    │  ├─ Request group state from server
    │  ├─ Update local group tree
    │  ├─ Retry message decryption
    │  └─ Succeed
    │
    └─ User doesn't notice (automatic recovery)
    
    User experience:
    $ wire profile
    ← (takes normal time, transparent recovery)
    → Returns profile
    
    Recovery: Automatic, transparent to user
```

---

## Summary: Quick Reference

**When does sync happen?**
- ✅ On first data access (profile, presence, conversation commands)
- ✅ Blocks CLI until sync reaches "Live" state
- ✅ Automatic exponential backoff on network errors
- ❌ Does NOT happen on login/logout
- ❌ Does NOT happen on status/doctor commands

**How long does it take?**
- 🐢 First run: 45-50 seconds (SlowSync + IncrementalSync)
- 🚀 Subsequent runs: 4-6 seconds (just catch-up)
- ⚡ With DISABLE_SYNC_WAIT: 3-5 seconds (but incomplete data)

**Can I control it?**
- ✅ Set `WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT=true` to skip wait
- ✅ Use `wire doctor status` to check sync progress without waiting
- ❌ Cannot manually trigger sync (it's automatic)


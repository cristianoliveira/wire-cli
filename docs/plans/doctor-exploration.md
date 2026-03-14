# Doctor (Sync Health) Exploration

## Implementation Note: Beta Breaking Change

**Version**: v0.0.1-beta — Breaking changes are allowed without deprecation periods.

**Change**: `wire sync` command will be directly renamed to `wire doctor` with **no deprecation period**. This is acceptable under v0.0.1-beta policy (see `AGENTS.md`).

---

## Problem: Why Users Care About Sync State

Imagine you just logged in to Wire after being offline for a few hours. Your messages are loading, your presence is being synchronized, and you're wondering: **"Am I ready to send messages? Are my encryption keys in place? Is my device synced?"** There's no simple CLI command to answer this.

**The DevOps Pain Point**: You're deploying a Wire-based notification service. Your bot logs in successfully (auth works), but it can't send messages. Is it because the sync layer isn't live yet? Are encryption keys still being negotiated? Is there a network partition? Without a sync health check, you're left grepping logs and hoping.

**The User Frustration**: You work across time zones. You close your laptop, fly to a different continent, and open it 12 hours later. Your presence shows "online," but your messages aren't being delivered. You restart the app, wait, and still nothing. A `wire doctor` command that tells you "Sync is live, encryption is ready, but you have 15 pending messages—check your network" would be worth its weight in gold.

**The Compliance Requirement**: Some organizations need to audit: "Is this account's sync stack healthy?" Not just for troubleshooting, but for compliance reporting. A JSON output of sync health metrics enables automation and dashboards.

---

## Philosophy: What is `wire doctor` and What is it Not?

### The Core Idea

**`wire doctor` is not about managing sync—it's about observing sync.** Kalium's sync layer handles synchronization automatically: it merges remote and local state, negotiates encryption keys, and keeps your account consistent. That's Kalium's job. `wire doctor` is the CLI's job: **to give you a window into that sync state** so you can see what's happening and make informed decisions.

Think of it like this: Your car's transmission shifts automatically. You don't manage it. But your dashboard shows you the engine temperature, RPM, and fuel level. That's what `wire doctor` does for your account. It's the health dashboard.

### What Does `wire doctor` Do? (And What Doesn't It?)

| | **`wire doctor` Does This** | **Kalium Handles That** |
|---|---|---|
| **Observe sync state** | ✓ Show "sync is live" or "initializing" | — |
| **Report encryption readiness** | ✓ Show "MLS is ready" or "migrating" | — |
| **List pending messages** | ✓ Aggregate and report count | — |
| **Trigger sync from CLI** | ✗ | ✓ Automatic background sync |
| **Resolve merge conflicts** | ✗ | ✓ Kalium merges automatically |
| **Manage encryption keys** | ✗ | ✓ Kalium negotiates MLS keys |
| **Monitor in real-time** | ✗ (outside MVP scope) | ✓ Real-time event stream |

**Key distinction**: `wire doctor status` is read-only. It's introspection. It doesn't change state; it reports it.

### Three Core Use Cases

#### 1. **DevOps Automation: "Is the bot ready?"**
**Scenario**: You deploy a Wire-based notification bot. It logs in successfully, but you don't know if it's ready to send messages yet.

**How `wire doctor` helps**: Run `wire doctor status --json` in your startup script. Check the returned JSON:
```json
{
  "status": "ready",
  "sync": { "live": true },
  "encryption": { "ready": true }
}
```
If status is "ready", your bot can start sending. If "initializing", retry in 5 seconds. No more log grepping. No more crossing your fingers.

**Real-world benefit**: Reduce bot startup time from "2 minutes of uncertainty" to "10 seconds of certainty."

---

#### 2. **Customer Support / Troubleshooting: "What's wrong with their account?"**
**Scenario**: A user reports: "My messages aren't being delivered." You need to diagnose quickly.

**How `wire doctor` helps**: Run `wire doctor status --verbose` on their account. Get a report:
```
Sync:            Live (last update 2s ago)
Encryption:      Ready (MLS 100% complete)
Devices:         3 active (all healthy key packages)
Pending Messages: 42 (last received 15s ago)
Sync Lag:        150ms
```
From this, you know: "Sync is healthy, but 42 messages are pending. Their network might be slow. Suggest they check connectivity."

**Real-world benefit**: Reduce "I don't know what's wrong" support time from 30 minutes of investigation to 30 seconds of diagnosis.

---

#### 3. **Power User Security Audit: "Am I actually synced?"**
**Scenario**: You use Wire for sensitive communication. You just logged in on a new device and want to verify you're truly synced before starting important conversations.

**How `wire doctor` helps**: Run `wire doctor status`. See:
```
Status: Ready ✓
Sync:   Live (last update 1s ago)
Encryption: Ready (MLS ready)
```
You know your account is healthy. Your encryption keys are negotiated. You can start communicating with confidence.

**Real-world benefit**: Peace of mind in < 1 second instead of "hope I'm synced" anxiety.

---

### MVP Scope: What's Included? What's Deferred?

**In MVP** (1.5-day implementation):
- ✅ `wire doctor status` – Basic health check (auth, sync, encryption, last sync time)
- ✅ `--verbose` flag – Detailed metrics (sync lag, pending messages, MLS status, device key-package counts)
- ✅ `--json` output – Machine-readable, stable schema
- ✅ Simple diagnostics – "Ready" vs. "Initializing" vs. "Degraded" with recovery suggestions
- ✅ Exit codes – 0 (healthy), 1 (degraded), 11 (unauthorized), 13 (server error)
- ✅ `--wait-for-ready` flag – Optional, useful for startup scripts

**Deferred to Phase 2**:
- ❌ `wire doctor reset` – Force re-sync (complex, lower priority)
- ❌ `wire doctor diagnose` – Step-by-step diagnosis (can start with simple suggestions in MVP)
- ❌ Real-time monitoring (`wire doctor watch`) – Out of scope for now
- ❌ Metric export to monitoring systems – Can add later

**Philosophy**: MVP is intentionally narrow. It solves the three core use cases. Advanced features (reset, detailed diagnosis, monitoring) follow once we validate that basic status + verbose is valuable.

---

## Personas & Why They Need Sync Diagnostics

### Persona 1: DevOps / Support Engineer
**Profile**: Carlos runs infrastructure for a distributed team using Wire. His job includes deploying and maintaining a Wire-based notification bot, handling account recovery tickets, and diagnosing "why isn't this working?" Support tickets.

**Pain Points**:
- Deploys a new bot instance; it logs in fine, but doesn't send its first message for 2 minutes. Is that expected? Is the sync layer initializing?
- A user reports "My messages aren't syncing"; Carlos needs to know: "Is your sync live? Are your encryption keys ready? Are you missing any messages?"
- During account recovery (e.g., password reset), Carlos needs to verify the account is ready to use without manually testing
- Can't write a health-check script that tells him "This account is 100% ready" vs. "Still initializing, retry in 5 seconds"

**Why Doctor Matters**: Carlos needs a `wire doctor` command that runs once and outputs a clear health report: "Auth: ✓ Ready | Sync: ✓ Live | Encryption: ✓ Ready | Pending Messages: 0 | Last Sync: 2s ago". This unblocks automation, debugging, and customer support.

### Persona 2: End User / Power User
**Profile**: Diana uses Wire on her laptop and phone. She occasionally encounters situations where messages don't seem to be flowing (or she's paranoid about whether she's actually synced), and she wants to quickly verify her account is healthy.

**Pain Points**:
- Just logged in after being offline; wants to verify "Am I synced?" before starting important conversations
- Frequently asks herself: "Are my encryption keys ready? Will messages I send now actually be received?"
- Uses Wire for sensitive communication; wants to audit that her account encryption is solid and all devices are synced
- Has a flaky network and wants to know if message delivery is blocked because of sync lag or network issues

**Why Doctor Matters**: Diana needs a simple `wire doctor status` command that tells her at a glance: "Sync is live and healthy" or "Sync is initializing, wait 30 seconds". She doesn't need to understand MLS or key packages—she just wants to know: "Am I ready to send?"

---

## Realistic User Stories

### Story 1: Check Overall Sync & Encryption Readiness (Must Have)
**Problem**: Users have no way to know if their account is fully synced and encryption is ready.

**User Type**: DevOps engineer, support staff, power user

**Title**: "Run a health check to see if my account is ready to communicate"

**Story**:
> As a user concerned about message delivery, I want to run a single command that tells me if my account's sync layer is live and encryption is ready so I can be confident that messages I send will be delivered.

**Acceptance Criteria**:
- Given I am authenticated, when I run `wire doctor status`, then I see a clear health report showing: auth status, sync status (live/initializing/degraded), encryption readiness (ready/pending/error), and when last sync occurred
- Given the account is fully ready, the output shows all green checkmarks (or "Ready" / "✓") and exit code is 0
- Given the account is still initializing (e.g., MLS migration in progress), the output shows "Initializing" for relevant fields and exit code 1 (degraded but not broken)
- Given there's a critical error (auth expired, sync failed), the output shows the issue clearly with exit code 13 (server error) or 11 (unauthorized)
- When `--json` flag is used, output includes: `{ status: "ready"|"initializing"|"degraded"|"error", auth: "ok", sync: "live"|"initializing", encryption: "ready"|"pending", last_sync_ms: 1234, uptime_ms: 5000, ... }`
- Optional: `--wait-for-ready` flag blocks until sync is live (up to 30 seconds), useful for startup scripts

**MVP Acceptance**: Command runs, shows basic status; report is human-readable and machine-parseable

---

### Story 2: Get Detailed Health Metrics & Diagnostics (Must Have)
**Problem**: Users want more detail: "What's the actual sync lag? How many pending messages? Is MLS migration done?"

**User Type**: DevOps engineer, support staff

**Title**: "Get detailed health metrics for troubleshooting and monitoring"

**Story**:
> As a support engineer responding to a sync complaint, I want to run a command that gives me detailed diagnostics (sync lag, pending messages, MLS status, key-package health) so I can pinpoint the issue and provide actionable recovery steps.

**Acceptance Criteria**:
- Given I run `wire doctor status --verbose`, then I see detailed metrics: sync lag (ms since last sync), number of pending messages, MLS migration status (percentage complete or "done"), key-package count per device, event-stream cursor position
- Given there are pending messages, the output shows: "Pending Messages: 42 (last received 15s ago)"
- Given MLS migration is in progress, the output shows: "MLS Migration: 75% complete (estimated 10s remaining)"
- Given key packages are low, the output shows: "Key Packages (Laptop): 150/300 (low, refilling...)"
- When `--json` flag is used, output includes all diagnostic fields in a flat or nested structure (e.g., `metrics: { pending_messages: 42, sync_lag_ms: 150, mls_migration_pct: 75, ... }`)
- Given a specific issue is detected, the output includes a "recovery_suggestion": e.g., "Sync lag is high; try: `wire doctor reset` or check your network"
- Given no issues are detected, recovery_suggestion field is null or "All systems healthy"

**MVP Acceptance**: Shows at least 5 key metrics; suggestions are basic but helpful

---

### Story 3: Diagnose Sync Lag & Message Delivery Issues (Should Have)
**Problem**: Users get stuck on "Why aren't my messages sending?" A targeted diagnosis tool helps.

**User Type**: Power user, support engineer

**Title**: "Diagnose why a message isn't being delivered or received"

**Story**:
> As a user experiencing message delivery delays, I want to run a diagnostic that tells me if the issue is sync-related (our encryption, event queue), network-related, or server-side so I can take appropriate action.

**Acceptance Criteria**:
- Given I run `wire doctor diagnose`, then the CLI performs a series of checks and outputs a diagnosis report
- The report includes: "Auth Status: Connected" → "Sync Status: Live" → "Event Queue: Empty" → "Key Packages: Healthy" → "Network: OK"
- If any check fails, the report shows: "Sync Status: Lag detected (250ms). Last event received 3s ago. Retrying..." with a suggestion
- If MLS is pending, the report shows: "Encryption: MLS migration in progress (80% done). Message delivery may be blocked until complete."
- Given network issues are suspected, the report suggests: "Network appears slow. Try: `wire doctor reset` or check your internet connection."
- Given the issue is resolved (sync catches up), the report shows: "Diagnosis complete: All checks passed ✓"
- Exit code 0 if healthy, exit code 1 if degraded, exit code 13 if server error

**MVP Acceptance**: Runs basic checks; output is clear and actionable

---

### Story 4: Initiate Sync Reset (Could Have) `[Could]`
**Problem**: Users sometimes need to "force" a re-sync if they suspect data loss or corruption.

**User Type**: Support engineer, power user (advanced)

**Title**: "Manually trigger a sync reset or re-download state"

**Story**:
> As a support engineer helping a user with stale data, I want to trigger a sync reset from the CLI so the account re-downloads its full state without waiting for automatic refresh.

**Acceptance Criteria**:
- Given I run `wire doctor reset --force` (with confirmation or `--yes` flag), then the CLI initiates a full re-sync of the account state
- The CLI shows a progress indicator or message: "Resetting sync... This may take 30-60 seconds."
- Once complete, the output shows: "Sync reset complete. Full state re-downloaded. You may now resume using Wire."
- Given the reset fails, the output shows the error and suggests contacting support
- Exit code 0 on success, exit code 13 on server error, exit code 15 on permission error

**MVP Acceptance**: This story is deferred to phase 2b (post-MVP); focus on status and diagnostics first

---

## Minimum Viable Slice (1.5-Day MVP)

**Definition**: A working doctor (sync health) diagnostics feature that enables users and operators to verify account readiness and troubleshoot sync issues.

**In scope for MVP**:
1. `wire doctor status` – Show basic health: auth, sync, encryption, last sync time
2. `--verbose` flag – Show detailed metrics: sync lag, pending messages, MLS status, key-package counts
3. `--json` output with stable schema for both basic and verbose modes
4. Simple diagnostics: "All healthy" vs. "Initializing" vs. "Degraded" with a recovery suggestion
5. Exit codes: 0 (healthy), 1 (degraded/initializing), 11 (unauthorized), 13 (server error)
6. Optional `--wait-for-ready` flag (block until sync is live, max 30 seconds)
7. Unit tests for health aggregator + Bats integration tests

**Out of scope for MVP**:
- `wire doctor diagnose` (detailed step-by-step diagnosis)
- `wire doctor reset` (force re-sync)
- Real-time monitoring mode (`wire doctor watch`)
- Event-stream inspection (cursor position, lag estimation from events)
- Metric export to monitoring systems

**Why this scope**: Status + verbose cover 85% of real-world support cases. Detailed diagnosis and reset can follow in phase 2b.

---

## Health Aggregation Model

The sync health feature aggregates status from multiple sources:

```
┌─────────────────────────────────────────────────────┐
│ wire doctor status                                  │
└─────────────────────────────────────────────────────┘
         │
         ├─ AuthScope → auth is valid, session alive?
         ├─ SyncScope → sync layer live, last sync timestamp?
         ├─ ClientScope → active device count, key-package health?
         ├─ ConversationScope → event queue empty, pending messages?
         └─ CryptoScope (internal) → MLS migration status, encryption ready?
         │
         └─ Aggregate & Output
            ├─ Plain text (for terminals)
            ├─ JSON (for scripting)
            └─ Recovery suggestions (if any issues detected)
```

### Output Format (MVP)

**Plain Text (default)**:
```
wire-cli doctor status

Status: Ready ✓

Auth:       Connected (expires in 7 days)
Sync:       Live (last update 2s ago)
Encryption: Ready (MLS ready)
Devices:    3 active (2 with healthy key packages, 1 refilling)

No issues detected.
```

**With --verbose**:
```
Status: Ready ✓

Auth:                Connected (expires in 7 days)
Sync:                Live (last update 2s ago)
Encryption:          Ready (MLS ready, 100% complete)
Devices:             3 active
  - Laptop:          key-packages: 250/300 (healthy)
  - Phone:           key-packages: 50/300 (low, refilling...)
  - Tablet:          key-packages: 280/300 (healthy)
Pending Messages:    0
Sync Lag:            2ms
Event Stream:        Healthy (cursor at event 4521)

Uptime:              5m 32s
Last Diagnostic:     now

No issues detected.
```

**JSON (--json)**:
```json
{
  "status": "ready",
  "auth": {
    "connected": true,
    "expires_in_days": 7
  },
  "sync": {
    "live": true,
    "last_sync_ms": 2000
  },
  "encryption": {
    "ready": true,
    "mls_status": "ready",
    "mls_migration_pct": 100
  },
  "devices": {
    "active_count": 3,
    "healthy_count": 2,
    "devices": [
      {
        "id": "device-abc",
        "type": "laptop",
        "key_packages_available": 250,
        "key_packages_max": 300
      }
    ]
  },
  "pending_messages": 0,
  "sync_lag_ms": 2,
  "uptime_ms": 332000,
  "timestamp": "2024-03-13T14:32:10Z"
}
```

---

## Integration Notes

### Dependency on Device Management Feature
Sync health and device management are complementary:
- **Device Management** answers "What devices are active?"
- **Sync Health** answers "Are my devices and sync stack healthy?"

In the MVP, sync status will show device key-package health (as aggregated metrics). The detailed per-device information will be available through `wire client show <device-id>`.

### Command Taxonomy
```
wire doctor status                              # Basic health check
wire doctor status --verbose                    # Detailed metrics
wire doctor status --wait-for-ready --timeout 30  # Block until ready
wire doctor status --json                       # Machine-readable output
wire doctor diagnose                            # Targeted diagnostics
wire doctor reset [--force] [--yes]             # Force re-sync (Phase 2)
```

### Error Model
| Scenario | Exit Code | Message | Recovery |
|----------|-----------|---------|----------|
| Unauthorized (not logged in) | 11 | "Session expired. Run `wire login` to re-authenticate." | Re-login |
| Sync still initializing | 1 | "Sync initializing (80% complete). Use --wait-for-ready to block." | Wait or retry |
| Server error (network, backend) | 13 | "Failed to check sync status: [error]" | Retry or check network |
| Auth expiring soon | 1 | "Sync ready, but auth expires in 1 day. Re-login to refresh." | Re-login |

---

## Implementation Readiness

### Kalium Surface Ready?
✅ **Mostly**. Required:
- `SyncScope.waitUntilLiveOrFailure()` ✓ (already used in profile/presence)
- `ClientScope.clientList()` ✓ (for device health)
- MLS status (may be internal; may need to infer from crypto scope)
- Pending message count (may need to infer from conversation state)

### Schema Stability?
✅ **Mostly**. Device and sync status are stable. Key-package counts may vary slightly. MLS migration status may be partially internal.

### Unknowns?
- MLS migration percentage: Check if exposed in public API or if we infer from device state
- Event-stream cursor: Check if SyncScope exposes last event position for lag estimation
- Pending message count: May need to aggregate from conversation snapshots

### Testing Surface?
✅ **Yes**. Stub backend supports sync status. Real backend supported for integration tests.

---

## Recommended Execution Order

Given the two features are complementary but independent:

### Option A: Sequential (Recommended)
1. **Day 1**: Implement Device Management (`wire client list`, `wire client delete`)
   - Simpler service layer; proves command wiring pattern
   - Unblocks security workflows (device revocation)
2. **Day 2–3**: Implement Sync Health (`wire sync status`, `--verbose`, diagnostics)
   - Builds on device management patterns
   - Enables operational workflows

**Why**: Device management is smaller and independent. Once done, you have a pattern to apply to sync health. Users get immediate value (device revocation on day 1).

### Option B: Parallel (If Pairing)
- **Day 1**: Both features' service layers (health aggregator + client service)
- **Day 2**: Both features' CLI commands + wiring
- **Day 3**: Testing + integration

**Why**: If two engineers are available, this is faster. Both features share architectural patterns.

### Option C: Sync-First (If Priority is Diagnostics)
1. **Day 1**: Implement Sync Health
   - Higher customer support value
   - Unblocks troubleshooting immediately
2. **Day 2**: Implement Device Management
   - Follows same patterns

**Why**: If the primary pain is "why isn't my account syncing?", prioritize sync health. Device revocation can follow.

---

## Related Documentation
- Feature proposal: `docs/plans/possible-features.md` (section: Sync Health & Observability)
- Architecture patterns: `docs/Architecture.md` (command → service → api-client wiring)
- Kalium surface: `docs/KALIUM_INTEGRATION.md`
- Error model: existing `wire-cli` exit codes and profiles

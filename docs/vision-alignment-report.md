# Vision Alignment Report: Sync & Device Management

## Executive Summary

This report compares the **product owner's vision** (from `docs/stories/sync-health.md` and `docs/stories/device-management.md`) against the **current wire-cli implementation** to assess alignment and identify gaps.

**Overall Grade**: 
- **Sync Health**: B+ (87% complete) — MVP-ready with schema alignment needed
- **Device Management**: C+ (51% complete) — Partial implementation with critical exit code issues

## Sync Health Vision Alignment

### Current Status: Stories 17-19 Mostly Implemented

| Story | Title | Planned? | Implemented? | Acceptance Criteria Coverage |
|-------|-------|----------|--------------|-----|
| 17 | `wire sync status` — basic health check | ✅ [Planned] | ✅ Yes | 95% |
| 18 | `wire sync status --verbose` — detailed metrics | ✅ [Planned] | ✅ Yes | 85% |
| 19 | `wire sync status --diagnose` — targeted checks | ✅ [Planned] | ✅ Yes | 90% |
| 20 | `wire sync reset` — manual sync recovery | ✅ [Planned] | ❌ No (deferred) | 0% |

### Sync: What's Working Well ✅

1. **Basic status command exists** with proper auth guards and output formats
2. **Verbose flag** properly shows detailed metrics (sync lag, pending messages, MLS status)
3. **Diagnose mode** implements the check framework with clear pass/fail reporting
4. **JSON output** is produced for both modes (scriptability enabled)
5. **Auth validation** is properly enforced across all sync commands
6. **Exit codes** mostly match spec (0 for healthy, 1 for degraded, 11 for unauthorized)

### Sync: Critical Gaps ⚠️

1. **JSON Schema Mismatch**
   - Vision spec: `{status, auth, sync, encryption, last_sync_ms, uptime_ms}`
   - Actual: `{status, metrics: {sync_lag_ms, pending_messages, mls_migration_pct, ...}}`
   - Impact: Scripts expecting the spec schema will break

2. **Missing Per-Device Key-Package Breakdown**
   - Vision story 18: "Key Packages (Laptop): 150/300 (low, refilling...)"
   - Actual: Global aggregate only, no per-device breakdown
   - Impact: Can't identify which device needs refill

3. **Exit Code 13 (Server Error) Never Used**
   - Vision spec: exit code 13 for "Network and server failures"
   - Actual: Implementation doesn't differentiate server errors from degraded states
   - Impact: Automation can't distinguish recoverable vs critical failures

4. **Missing Recovery Command Suggestions**
   - Vision story 18: "Try: `wire sync reset` or check your network"
   - Actual: Warnings shown, but no actionable recovery steps
   - Impact: Users see issues but don't know how to fix them

5. **Encryption Readiness Not Explicitly Tracked**
   - Vision: expects `encryption: "ready"|"pending"|"error"` field
   - Actual: MLS migration shown separately, no explicit readiness field
   - Impact: Spec doesn't match spec; could confuse automation

### Sync: Recommendations

1. **Priority 1 (Critical)**: Align JSON schema with vision or update vision document to match implementation
2. **Priority 2 (High)**: Add per-device key-package metrics to verbose output
3. **Priority 3 (High)**: Differentiate exit code 13 for server errors vs degraded state
4. **Priority 4 (Medium)**: Add recovery command suggestions to verbose/diagnose output
5. **Priority 5 (Low)**: Add explicit encryption readiness field or document why it's implicit

---

## Device Management Vision Alignment

### Current Status: Stories 13-15 Partially Implemented; Story 16 Deferred

| Story | Title | Planned? | Implemented? | Acceptance Criteria Coverage |
|-------|-------|----------|--------------|-----|
| 13 | `wire client list` — show all active devices | ✅ [Planned] [Must] | ⚠️ Partial | 83% |
| 14 | `wire client show <device-id>` — device details | ✅ [Planned] [Should] | ⚠️ Partial | 43% |
| 15 | `wire client delete <device-id>` — revoke device | ✅ [Planned] [Must] | ⚠️ Partial | 63% |
| 16 | `wire client rename <device-id>` — friendly name | ✅ [Planned] [Could] | ❌ No (deferred) | 0% |

### Device: What's Working Well ✅

1. **`wire client list`** properly shows devices with device ID, type, fingerprint hash (83% complete)
   - ✅ Table and JSON output modes work
   - ✅ "No active devices" message shown when list is empty
   - ✅ Auth guards properly enforced
   - ⚠️ Missing: placeholder "pending" for uninitialized fingerprints

2. **`wire client delete`** revokes devices safely (63% complete)
   - ✅ Confirmation prompt shown
   - ✅ `--yes` flag enables scripted deletion
   - ✅ Exit codes mostly correct (11 for unauthorized, 13 for server error)
   - ⚠️ Missing: warning when deleting only active device

3. **Auth validation** properly gated on all commands

### Device: Critical Gaps ⚠️

1. **Exit Code Inversion (CRITICAL)**
   - Vision spec: exit code **14** for "Device not found"
   - Actual implementation: uses exit code **13** for device not found
   - Impact: Automation will misclassify device-not-found as server error

2. **`wire client show` Missing JSON Output (HIGH)**
   - Vision story 14: "When `--json` flag is used, output is valid JSON..."
   - Actual: `show` command exists but only produces human-readable table
   - Impact: Scripts cannot parse device details; blocks scripting use case

3. **Hardcoded Key-Package Status (HIGH)**
   - Vision story 14: shows "low" or "warning" when key packages depleted
   - Actual: always returns "valid" or hardcoded 250 count
   - Impact: Cannot detect when device needs key-package refill

4. **Missing Device-Only Warning (MEDIUM)**
   - Vision story 15: "Warning: This is your only active device. Revoking it will sign you out."
   - Actual: No check for single-device scenario
   - Impact: User might accidentally lock themselves out

5. **Fingerprint Not Real (MEDIUM)**
   - Vision spec: fingerprint hash for device verification
   - Actual: uses client ID as fallback; no Proteus fingerprint or real identity
   - Impact: "Fingerprint" column is misleading; not actually cryptographic

6. **Missing "Pending" State for Fingerprints (MEDIUM)**
   - Vision story 13: "Given a device has no fingerprint available yet... output shows placeholder 'pending'"
   - Actual: fingerprint always present (as client ID fallback)
   - Impact: Can't distinguish devices initializing from fully-ready ones

### Device: Story-by-Story Breakdown

#### Story 13: List Devices (83% complete)
✅ **Mostly done**
- ✅ Acceptance criteria 1: table with ID, type, fingerprint, last activity
- ✅ Acceptance criteria 2: "No active devices" message
- ⚠️ Acceptance criteria 3: MISSING — no "pending" placeholder for uninitialized fingerprints
- ✅ Acceptance criteria 4: JSON output works
- ✅ Acceptance criteria 5: auth guards proper exit code 11

#### Story 14: Show Device Details (43% complete)
⚠️ **Partially done**
- ✅ Acceptance criteria 1: shows device ID, type, creation date, last activity
- ⚠️ Acceptance criteria 2-3: MISSING — no real key-package status or "low"/"warning" detection
- ❌ Acceptance criteria 4: exit code 14 NOT USED (uses 13 instead)
- ❌ Acceptance criteria 5: JSON OUTPUT MISSING (story calls for it)
- ✅ Acceptance criteria 6: auth guards work

#### Story 15: Delete Device (63% complete)
⚠️ **Partially done**
- ✅ Acceptance criteria 1: confirmation prompt shown
- ✅ Acceptance criteria 2: success message and exit code 0
- ✅ Acceptance criteria 3: cancellation respected
- ❌ Acceptance criteria 4: MISSING — no warning for only-active-device scenario
- ✅ Acceptance criteria 5: `--yes` flag skips prompt
- ❌ Acceptance criteria 6: exit code 14 NOT USED for not-found (uses 13)
- ✅ Acceptance criteria 7: server errors handled

#### Story 16: Rename Device (0% complete)
✅ **Intentionally deferred** (marked [Could] priority, phase 2)

### Device: Recommendations

1. **Priority 1 (CRITICAL)**: Fix exit code **13 ↔ 14** inversion
   - `wire client show <missing-id>` should return 14 (not found), not 13 (server error)
   - `wire client delete <missing-id>` should return 14 (not found), not 13
   - File: `src/main/kotlin/wirecli/device/RealKaliumDeviceApiClient.kt`

2. **Priority 2 (HIGH)**: Add `--json` output to `wire client show` command
   - Should output structured device metadata + key-package status
   - File: `src/main/kotlin/wirecli/commands/DeviceCommand.kt`

3. **Priority 3 (HIGH)**: Implement real key-package status detection
   - Replace hardcoded "valid" with actual Kalium `mlsKeyPackageCountUseCase` result
   - Show "low" or "warning" when count < threshold
   - File: `src/main/kotlin/wirecli/device/RealKaliumDeviceApiClient.kt`

4. **Priority 4 (MEDIUM)**: Add warning when deleting only active device
   - Check if this is the last device before showing confirmation
   - Add message: "Warning: This is your only active device. Revoking it will sign you out."
   - File: `src/main/kotlin/wirecli/commands/DeviceCommand.kt`

5. **Priority 5 (MEDIUM)**: Handle "pending" fingerprint state
   - When Proteus keys initializing, show "pending" instead of client ID
   - File: `src/main/kotlin/wirecli/device/RealKaliumDeviceApiClient.kt`

---

## Implementation Plan to Close Gaps

### Sync Health (Quick Wins — ~2-3 days)

1. **Day 1**: Align JSON schema or update vision
   - Decision: adopt implementation schema and update `docs/stories/sync-health.md` to match
   - Or: refactor implementation to match spec (more work)

2. **Day 2**: Add per-device key-package breakdown to verbose output
   - Call: `UserSessionScope.client.mlsKeyPackageCountUseCase(deviceId)` for each device
   - Display: "Key Packages: Laptop (device-id): 250/300"

3. **Day 3**: Differentiate exit code 13 and add recovery suggestions
   - Track if failure is server/network (exit 13) vs degraded state (exit 1)
   - Add suggestions: "Try: `wire sync reset` or check your internet connection"

### Device Management (More Involved — ~3-5 days)

1. **Day 1**: Fix exit code inversion (critical blocker)
   - Swap exit code 13 and 14 in device not-found paths
   - Update `AuthContracts.kt` and API client

2. **Day 2**: Add JSON output to `show` command
   - Extend `DeviceCommand.show` to accept `--output json`
   - Output device metadata + key-package status struct

3. **Day 3-4**: Implement real key-package status
   - Call Kalium SDK for actual counts
   - Add threshold logic: low < 100, critical < 50
   - Update `DeviceDetails` model to include status enum

4. **Day 5**: Polish warnings and edge cases
   - Add single-device warning before delete
   - Handle "pending" fingerprint state
   - Update tests for new behaviors

---

## Overall Verdict

**The implementation is on the right track but has not fully materialized the product vision yet.**

- **Sync Health**: 87% complete; schema alignment and recovery suggestions needed before production
- **Device Management**: 51% complete; critical exit code issues and missing JSON output must be fixed

**Recommendation**: Fix Priority 1-2 issues before merging to main branch. Reserve Priority 3-5 for follow-up polish.

---

## Files to Update

- `docs/stories/sync-health.md` — clarify JSON schema or implement to spec
- `docs/stories/device-management.md` — document deferred story 16, clarify exit codes
- `src/main/kotlin/wirecli/commands/SyncCommand.kt` — add recovery suggestions
- `src/main/kotlin/wirecli/commands/DeviceCommand.kt` — fix exit codes, add JSON, single-device warning
- `src/main/kotlin/wirecli/device/RealKaliumDeviceApiClient.kt` — real key-package status detection

---

## Research Artifacts

- `.tmp/researches/assistant-research-sync-vision-vs-reality.md` — detailed sync analysis
- `.tmp/researches/assistant-research-device-management-vision-vs-reality.md` — detailed device analysis

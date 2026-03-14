# Doctor (Sync Health) Stories

**Breaking Change (v0.0.1-beta)**: `wire sync` → `wire doctor`  
This is a breaking rename that requires no deprecation period under beta policy. All references and commands transition directly to `wire doctor` semantics.

---

## Planned Stories

### 17) Doctor: `wire doctor status` checks overall account readiness `[Planned] [Must]`
As a user concerned about message delivery, I want to run a single command that tells me if my account's sync layer is live and encryption is ready so I can be confident that messages I send will be delivered.

Acceptance criteria:
- Given I am authenticated, when I run `wire doctor status` (or `wire doctor`), then I see a clear health report showing: auth status, sync status (live/initializing/degraded), encryption readiness (ready/pending/error), and when last sync occurred.
- Given the account is fully ready, the output shows all green checkmarks (or "Ready" / "✓") and exit code is 0.
- Given the account is still initializing (e.g., MLS migration in progress), the output shows "Initializing" for relevant fields and exit code 1 (degraded but not broken).
- Given there's a critical error (auth expired, sync failed), the output shows the issue clearly with exit code 13 (server error) or 11 (unauthorized).
- When `--json` flag is used, output includes structured metadata: `{ status: "ready"|"initializing"|"degraded"|"error", auth: "ok", sync: "live"|"initializing", encryption: "ready"|"pending", last_sync_ms: 1234, uptime_ms: 5000 }`.
- Given I am not authenticated or my session is invalid, when I run the command, then access is denied with a login prompt and non-zero exit.

Notes:
- MVP scope: shows basic status; report is human-readable and machine-parseable.

---

### 18) Doctor: `wire doctor status --verbose` displays detailed metrics `[Planned] [Must]`
As a support engineer responding to a sync complaint, I want to run a command that gives me detailed diagnostics (sync lag, pending messages, MLS status, key-package health) so I can pinpoint the issue and provide actionable recovery steps.

Acceptance criteria:
- Given I run `wire doctor status --verbose`, then I see detailed metrics: sync lag (ms since last sync), number of pending messages, MLS migration status (percentage complete or "done"), key-package count per device, and event-stream status.
- Given there are pending messages, the output shows: "Pending Messages: 42 (last received 15s ago)".
- Given MLS migration is in progress, the output shows: "MLS Migration: 75% complete (estimated 10s remaining)".
- Given key packages are low on any device, the output shows: "Key Packages (Laptop): 150/300 (low, refilling...)".
- When `--json` flag is used, output includes all diagnostic fields in a structured format (e.g., `metrics: { pending_messages: 42, sync_lag_ms: 150, mls_migration_pct: 75 }`).
- Given a specific issue is detected, the output includes a recovery suggestion (e.g., "Sync lag is high; try: `wire doctor reset` or check your network").
- Given no issues are detected, recovery suggestion field is null or "All systems healthy".
- Given I am not authenticated or my session is invalid, when I run the command, then access is denied with a login prompt and non-zero exit.

Notes:
- Shows at least 5 key metrics; suggestions are basic but helpful.

---

### 19) Doctor: `wire doctor diagnose` performs targeted diagnostics `[Planned] [Should]`
As a user experiencing message delivery delays, I want to run a diagnostic that tells me if the issue is sync-related (our encryption, event queue), network-related, or server-side so I can take appropriate action.

Acceptance criteria:
- Given I run `wire doctor diagnose`, then the CLI performs a series of checks and outputs a diagnosis report.
- The report includes: "Auth Status: Connected" → "Sync Status: Live" → "Event Queue: Empty" → "Key Packages: Healthy" → "Network: OK".
- If any check fails, the report shows: "Sync Status: Lag detected (250ms). Last event received 3s ago. Retrying..." with a suggestion.
- If MLS is pending, the report shows: "Encryption: MLS migration in progress (80% done). Message delivery may be blocked until complete."
- Given network issues are suspected, the report suggests: "Network appears slow. Try: `wire doctor reset` or check your internet connection."
- Given the issue is resolved (sync catches up), the report shows: "Diagnosis complete: All checks passed ✓".
- When `--json` flag is used, output includes diagnostic details: `{ checks: [ { name: "Auth", status: "ok" }, { name: "Sync", status: "lag_detected", lag_ms: 250 } ] }`.
- Exit code 0 if healthy, exit code 1 if degraded, exit code 13 if server error, exit code 11 if unauthorized.
- Given I am not authenticated or my session is invalid, when I run the command, then access is denied with a login prompt and non-zero exit.

Notes:
- Runs basic checks; output is clear and actionable.

---

### 20) Doctor: `wire doctor reset` initiates a manual sync reset `[Planned] [Could]`
As a support engineer helping a user with stale data, I want to trigger a sync reset from the CLI so the account re-downloads its full state without waiting for automatic refresh.

Acceptance criteria:
- Given I run `wire doctor reset --force` (with confirmation or `--yes` flag), then the CLI initiates a full re-sync of the account state.
- The CLI shows a progress message: "Resetting sync... This may take 30-60 seconds."
- Once complete, the output shows: "Sync reset complete. Full state re-downloaded. You may now resume using Wire."
- Given the reset fails, the output shows the error and suggests contacting support.
- When `--yes` flag is used, the confirmation prompt is skipped (non-interactive mode for scripting).
- Exit code 0 on success, exit code 13 on server error, exit code 15 on permission error, exit code 11 if unauthorized.
- Given I am not authenticated or my session is invalid, when I run the command, then access is denied with a login prompt and non-zero exit.

Notes:
- This story is deferred to phase 2 (post-MVP); focus on status and diagnostics first.

## Current CLI Contract (Doctor)

### `wire doctor status`

- Success output shows health in human-readable format: auth, sync, encryption, last sync time.
- `--json` flag outputs structured metadata with status, component details, and timestamps.
- Healthy account returns exit code `0`.
- Initializing or degraded account returns exit code `1`.
- Unauthorized/missing sessions return exit code `11` with re-auth guidance.
- Network and server failures return exit code `13` with actionable retry messages.

### `wire doctor status --verbose`

- Shows detailed metrics: sync lag, pending messages, MLS status, key-package counts per device, event-stream status.
- Includes recovery suggestions if issues are detected.
- `--json` output includes all diagnostic fields in a nested or flat structure.
- Same exit codes as basic status.

### `wire doctor diagnose`

- Performs step-by-step checks and outputs a diagnosis report.
- Includes check names and statuses; shows lag and other specific metrics if detected.
- Suggests recovery actions (e.g., sync reset, network checks).
- Exit code 0 if healthy, 1 if degraded, 13 if server error, 11 if unauthorized.

### `wire doctor reset [--force] [--yes]`

- Confirmation prompt is mandatory unless `--yes` flag is provided.
- Shows progress indicator during reset.
- Success returns exit code `0` with completion message.
- Server errors return exit code `13` with guidance to contact support.
- Permission errors return exit code `15`.
- Unauthorized/missing sessions return exit code `11`.

# Device Management Exploration

## Problem: Why Users Care About Device Lifecycle

Today, Wire users have no way to inspect or revoke their active devices from the command line. Here's what that means in practice:

**The Security Nightmare**: You're at a co-working space and lose your laptop. You log into Wire from a web browser, navigate through the UI, find "Settings → Devices," and revoke the old one. But what if you can't access the UI? What if you're scripting account recovery? What if you're an admin trying to audit which devices a compromised account was accessing?

**The Compliance Gap**: Organizations need to know which devices are actively syncing messages. If a device goes missing or is suspected of compromise, there's no programmatic way to revoke it or check its status. A CLI that supports device management unlocks automation—you can rotate devices in bulk, audit active clients on a schedule, and respond to security incidents faster.

**The Encryption Readiness Problem**: With MLS (Message Layer Security), Wire now rotates key packages. Devices need to maintain a healthy key-package supply. There's no simple way to check if a device is "ready to receive messages" without digging into logs. A developer or SRE building integrations needs this visibility.

## Personas & Why They Need Device Management

### Persona 1: Power User / Security-Conscious Developer
**Profile**: Alice runs a distributed team of engineers. She uses Wire for team coordination, syncs multiple devices (laptop, phone, tablet), and takes security seriously. She sometimes works offline and needs to verify her devices are in sync.

**Pain Points**:
- Lost her phone last week; wants to revoke it immediately without navigating a UI
- Periodically audits which devices she's actively using (she should only have 3, but somehow has 5)
- Before sharing sensitive information, wants to verify all her devices are "healthy" (key packages in stock, encryption ready)
- Works in automation scripts and needs a programmatic way to check device health

**Why Device Management Matters**: Alice needs `wire client list` to see her active devices, `wire client show <id>` to inspect fingerprints and key-package status, and `wire client delete <id>` to revoke compromised or unused devices—all from the terminal. This is faster than opening the UI, especially from scripts or remote sessions.

### Persona 2: Team Lead / Admin
**Profile**: Bob manages a team of 30 people. He uses Wire as the primary communication platform and occasionally needs to audit account security, onboard new team members, and respond to security incidents (e.g., "Is a former contractor's device still active?").

**Pain Points**:
- When a team member reports a compromised account, Bob needs to know: "What devices are active on that account right now?" (to assess impact scope)
- New team members sometimes forget to remove devices from old setups; Bob wants to help them clean up without explaining the UI
- During offboarding, Bob wants to verify that a departing team member's devices are revoked as part of account deprovisioning
- Can't audit active devices across the team; no way to generate a report

**Why Device Management Matters**: Bob needs `wire client list` to see all active devices on an account, confirm key-package health (is this device able to receive messages?), and `wire client delete` with a confirmation flow to safely revoke devices. This enables one-off security audits and scripted offboarding workflows.

## Realistic User Stories

### Story 1: List All Active Devices (Must Have)
**Problem**: Users have no visibility into which devices are actively connected to their account.

**User Type**: Power user, security-conscious developer, admin

**Title**: "List all my active devices with basic metadata"

**Story**:
> As a user concerned about account security, I want to see all active devices on my account (including device type, fingerprint, and last activity) so I can audit which devices are currently connected and notice any unauthorized access.

**Acceptance Criteria**:
- Given I am authenticated, when I run `wire client list`, then I see a table/list of all active devices with columns: device ID, device type (if available), fingerprint hash, creation date, and last activity timestamp
- Given no devices are active (hypothetically), when I run the command, then I see a message "No active devices found"
- Given a device has no fingerprint available yet (e.g., key packages being initialized), then the output shows a placeholder (e.g., "pending") and succeeds
- When `--json` flag is used, output is valid JSON with consistent schema

**MVP Acceptance**: Command runs without error, shows at least 2–3 devices with readable metadata; pagination not required for MVP

---

### Story 2: Show Detailed Device Information (Must Have)
**Problem**: Users can see a list of devices but can't inspect individual device details (is it active? Is it healthy? What are the key metrics?).

**User Type**: Power user, security-conscious developer

**Title**: "Inspect a specific device's health and details"

**Story**:
> As a security-conscious developer, I want to view detailed information about a specific device (including key-package status and encryption readiness) so I can verify it's healthy before relying on it or decide if it needs to be revoked.

**Acceptance Criteria**:
- Given I know a device ID, when I run `wire client show <device-id>`, then I see detailed information: device ID, type, fingerprint, creation date, last activity, and key-package status
- Given a device is ready to receive messages, when I view it, then key-package status shows "ready" or a healthy count (e.g., "250 key packages available")
- Given a device has low or no key packages, then status shows "low" or "warning" and suggests refill
- Given the device ID does not exist, when I run the command, then I see an error message "Device not found" with exit code 14 (not found)
- When `--json` flag is used, output is valid JSON with schema including device metadata and health indicators

**MVP Acceptance**: Command runs, shows device details and key-package status; refill automation not required for MVP

---

### Story 3: Revoke a Device (Must Have)
**Problem**: Users can't remove compromised or old devices from the CLI; they must use the UI or web client.

**User Type**: Power user, admin (especially post-security incident)

**Title**: "Revoke an active device with safe confirmation"

**Story**:
> As a user who just realized my old laptop was compromised, I want to revoke a device from the CLI so I can immediately remove it without navigating a UI or browser.

**Acceptance Criteria**:
- Given I know the device ID I want to revoke, when I run `wire client delete <device-id>`, then the CLI prompts me to confirm the action (e.g., "You are about to revoke device ABC123. This is irreversible. Type 'yes' to confirm")
- Given I confirm deletion, when the device is revoked successfully, then I see a success message "Device ABC123 revoked successfully" and exit code 0
- Given I do not confirm (press Ctrl+C or type "no"), then the operation is cancelled and no device is deleted
- Given the device is my only active device, then the CLI shows a warning (but still allows deletion): "Warning: This is your only active device. Revoking it will sign you out."
- When `--yes` flag is used, the confirmation prompt is skipped (non-interactive mode for scripting)
- Given the device ID does not exist, when I attempt deletion, then I see "Device not found" with exit code 14
- Given deletion fails due to a server/network error, then I see the error with exit code 13 and can retry

**MVP Acceptance**: Device is deleted; prompt and `--yes` flag work correctly; error handling is clear

---

### Story 4: Rename a Device (Could Have) `[Could]`
**Problem**: Users want to label their devices for easier identification ("my work laptop" vs. "alice-ipad").

**User Type**: Power user with many devices

**Title**: "Assign a friendly name to a device for easier identification"

**Story**:
> As a power user with multiple devices, I want to rename a device with a friendly label (e.g., "MacBook Pro 2024") so I can quickly identify it when viewing the device list.

**Acceptance Criteria**:
- Given I know a device ID, when I run `wire client rename <device-id> --name "My Work Laptop"`, then the device is renamed and I see a confirmation message
- Given I view the device list, then the friendly name is displayed alongside the device ID
- Given a friendly name is longer than 50 characters, then the CLI truncates it with a warning
- Given no `--name` argument is provided, then the CLI shows an error "Please provide a device name with --name"

**MVP Acceptance**: This story is deferred to phase 2b (post-MVP); focus on list, show, delete first

---

## Minimum Viable Slice (1-Day MVP)

**Definition**: A working device management feature that enables users and admins to audit and revoke devices.

**In scope for MVP**:
1. `wire client list` – Show all active devices with basic metadata (ID, type, fingerprint, last activity)
2. `wire client delete <id>` – Revoke a device with interactive confirmation; support `--yes` flag for scripting
3. Exit codes and error handling: 0 (success), 11 (unauthorized), 13 (server error), 14 (not found)
4. `--json` output for both commands with stable schema
5. Unit tests for service layer + Bats integration tests (stub backend)

**Out of scope for MVP**:
- `wire client show <id>` (defer to day 1.5 if time allows)
- Key-package health indicators (show "pending" placeholder; full health aggregation comes in sync-health feature)
- Device renaming
- Bulk operations
- Device-type filtering

**Why this scope**: List + delete cover 80% of real-world use cases (auditing and emergency revocation). Show and rename can follow in a quick phase-2a iteration if feedback demands them.

---

## Integration Notes

### Dependency on Sync Health Feature
Device management and sync health are complementary:
- **Device Management** answers "What devices are active?"
- **Sync Health** answers "Is each device healthy and ready?"

In the MVP, `wire client show` will show basic key-package status (as a placeholder). The full health diagnosis (including MLS readiness, sync lag, encryption checks) will be handled by `wire doctor` / `wire sync status` in the sync-health feature.

### Command Taxonomy
```
wire client list                  # List all devices
wire client delete <device-id>    # Revoke a device
wire client show <device-id>      # (Phase 2a) Show device details
wire client rename <device-id>    # (Phase 2b) Rename device
```

### Error Model
| Scenario | Exit Code | Message | Recovery |
|----------|-----------|---------|----------|
| Unauthorized (not logged in) | 11 | "Session expired. Run `wire login` to re-authenticate." | Re-login |
| Device not found | 14 | "Device <id> not found. Run `wire client list` to see active devices." | List devices |
| Server error (network, backend) | 13 | "Failed to revoke device: [underlying error]" | Retry or contact support |
| Permission denied (e.g., revoking someone else's device) | 15 | "Permission denied. You can only manage your own devices." | Check device ownership |

---

## Implementation Readiness

### Kalium Surface Ready?
✅ **Yes**. `ClientScope` exposes:
- `clientList()` → returns list of active clients with metadata
- `clientDelete(clientId)` → revokes a client
- `clientInfo(clientId)` → fetches detailed client info (fingerprint, timestamps)

### Schema Stability?
✅ **Yes**. Client metadata is stable in both stub and real backends. Key-package counts may vary, but schema is predictable.

### Unknowns?
- Key-package refill API: check if it's exposed as a public method or internal-only. If internal, we'll show counts from `clientInfo()` only.

### Testing Surface?
✅ **Yes**. Stub backend supports device list and deletion. Real backend supported for integration tests.

---

## Related Documentation
- Command taxonomy: `docs/plans/possible-features.md` (section: Device/Client Management)
- Architecture patterns: `docs/Architecture.md` (command → service → api-client wiring)
- Error model: existing `wire-cli` exit codes and profiles

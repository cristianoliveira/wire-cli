# Device Management Stories

## Planned Stories

### 13) Device Management: `wire client list` displays all active devices `[Planned] [Must]`
As a user concerned about account security, I want to see all active devices on my account so I can audit which devices are currently connected and notice any unauthorized access.

Acceptance criteria:
- Given I am authenticated, when I run `wire client list`, then I see a table/list of all active devices with columns: device ID, device type, fingerprint hash, and last activity timestamp.
- Given no devices are active, when I run the command, then I see a message "No active devices found".
- Given a device has no fingerprint available yet (e.g., key packages initializing), then the output shows a placeholder (e.g., "pending") and succeeds.
- When `--json` flag is used, output is valid JSON with a consistent schema.
- Given I am not authenticated or my session is invalid, when I run `wire client list`, then access is denied with a login prompt and non-zero exit.

Notes:
- MVP scope: no pagination required; table format for terminals, JSON for scripting.

---

### 14) Device Management: `wire client show <device-id>` displays device details `[Planned] [Should]`
As a security-conscious developer, I want to view detailed information about a specific device (including key-package status and encryption readiness) so I can verify it's healthy before relying on it or decide if it needs to be revoked.

Acceptance criteria:
- Given I know a device ID, when I run `wire client show <device-id>`, then I see detailed information: device ID, type, fingerprint, creation date, last activity, and key-package status.
- Given a device is ready to receive messages, when I view it, then key-package status shows "ready" or a healthy count (e.g., "250 key packages available").
- Given a device has low or no key packages, then status shows "low" or "warning" and suggests refill.
- Given the device ID does not exist, when I run the command, then I see an error message "Device not found" with exit code 14.
- When `--json` flag is used, output is valid JSON with schema including device metadata and health indicators.
- Given I am not authenticated or my session is invalid, when I run the command, then access is denied with a login prompt and non-zero exit.

Notes:
- Refill automation not required for MVP; display counts from backend only.

---

### 15) Device Management: `wire client delete <device-id>` revokes a device `[Planned] [Must]`
As a user who just realized my old laptop was compromised, I want to revoke a device from the CLI so I can immediately remove it without navigating a UI or browser.

Acceptance criteria:
- Given I know the device ID I want to revoke, when I run `wire client delete <device-id>`, then the CLI prompts me to confirm the action (e.g., "You are about to revoke device ABC123. This is irreversible. Type 'yes' to confirm").
- Given I confirm deletion, when the device is revoked successfully, then I see a success message "Device ABC123 revoked successfully" and exit code 0.
- Given I do not confirm (press Ctrl+C or type "no"), then the operation is cancelled and no device is deleted.
- Given the device is my only active device, then the CLI shows a warning (but still allows deletion): "Warning: This is your only active device. Revoking it will sign you out."
- When `--yes` flag is used, the confirmation prompt is skipped (non-interactive mode for scripting).
- Given the device ID does not exist, when I attempt deletion, then I see "Device not found" with exit code 14.
- Given deletion fails due to a server/network error, then I see the error with exit code 13 and can retry.
- Given I am not authenticated or my session is invalid, when I run the command, then access is denied with a login prompt and non-zero exit.

Notes:
- Confirmation flow is mandatory for safety; `--yes` flag enables scripted revocation.

---

### 16) Device Management: `wire client rename <device-id>` assigns a friendly name to a device `[Planned] [Could]`
As a power user with multiple devices, I want to rename a device with a friendly label (e.g., "MacBook Pro 2024") so I can quickly identify it when viewing the device list.

Acceptance criteria:
- Given I know a device ID, when I run `wire client rename <device-id> --name "My Work Laptop"`, then the device is renamed and I see a confirmation message.
- Given I view the device list, then the friendly name is displayed alongside the device ID.
- Given a friendly name is longer than 50 characters, then the CLI truncates it with a warning.
- Given no `--name` argument is provided, then the CLI shows an error "Please provide a device name with --name".
- Given the device ID does not exist, when I run the command, then I see "Device not found" with exit code 14.
- Given I am not authenticated or my session is invalid, when I run the command, then access is denied with a login prompt and non-zero exit.

Notes:
- This story is deferred to phase 2 (post-MVP); focus on list, show, delete first.

## Current CLI Contract (Device Management)

### `wire client list`

- Success output is a table or JSON with device metadata (ID, type, fingerprint, last activity).
- Missing or pending fingerprints render as `pending`.
- Unauthorized/missing sessions return exit code `11` with re-auth guidance.
- Network and server failures map to exit code `13` with actionable retry messages.

### `wire client show <device-id>`

- Success output is structured (table or JSON) showing device details and key-package status.
- Non-existent device returns exit code `14` with guidance to run `wire client list`.
- Unauthorized/missing sessions return exit code `11`.

### `wire client delete <device-id>`

- Confirmation prompt is mandatory; `--yes` flag bypasses it for scripting.
- Success returns exit code `0` with a confirmation message.
- Non-existent device returns exit code `14`.
- Server errors return exit code `13`.
- Unauthorized/missing sessions return exit code `11`.

### `wire client rename <device-id> --name <label>`

- Success returns a confirmation message.
- Non-existent device returns exit code `14`.
- Missing `--name` returns exit code `2` (usage error).

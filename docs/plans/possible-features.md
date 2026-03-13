# Possible Features for `wire-cli`

## Context

This proposal is based on a focused review of:

- `.local/kalium` (SDK capabilities and extension points)
- `.local/wiretui` (proven user workflows and UX patterns)
- current `wire-cli` implementation (existing commands and architecture)

Current `wire-cli` baseline is strong for auth/profile/presence, but still narrow compared to Kalium/WireTUI.

## Prioritized Feature Candidates

### Now (high value, lower risk)

1. **Secure login input + non-echo credentials**
   - Add `--password-stdin` and hidden prompt support.
   - Why: immediate security improvement; avoids leaking credentials in history/process list.
   - Complexity: **M**.

2. **2FA login flow support**
   - Handle second-factor challenge paths in `login`.
   - Why: required for real-world auth parity and fewer login dead-ends.
   - Complexity: **M**.

3. **Session/account command group (`wire sessions ...`)**
   - Add `list`, `use`, `remove` (with safe confirmation / `--yes`).
   - Why: unlocks multi-account workflows already proven in WireTUI/Kalium.
   - Complexity: **M**.

4. **Auth/session diagnostics (`wire auth status|diagnose`)**
   - Report active account, backend/server, token/session health, and actionable errors.
   - Why: big supportability win with small implementation footprint.
   - Complexity: **S/M**.

### Next (medium scope, strong operator value)

5. **Device/client management (`wire client ...`)**
   - List clients, inspect fingerprint/key package state, delete client.
   - Why: account hygiene and incident recovery; already available in Kalium surfaces.
   - Complexity: **M**.

6. **Server selection workflow (`wire server select`)**
   - Support explicit backend choice (`--backend`) and optional guided confirmation flow.
   - Why: reduces backend misconfiguration and improves operator confidence.
   - Complexity: **M**.

7. **Diagnostics and sync status (`wire sync watch`, `wire diagnostics ...`)**
   - Expose sync liveness/health and recent categorized failures.
   - Why: better observability for automation and CI/scripting contexts.
   - Complexity: **S/M**.

8. **Contacts/search essentials (`wire contacts find`, `wire search global`)**
   - Domain-aware search and machine-readable output (`--json`).
   - Why: high daily utility for support and operations workflows.
   - Complexity: **M/L**.

### Later (larger feature surface)

9. **Backup/restore command suite (`wire backup ...`)**
   - Backup create/list/restore with eligibility checks and progress output.
   - Why: portability and recovery capabilities expected from mature clients.
   - Complexity: **L**.

10. **Conversation operations (`wire convo ...`, `wire groups ...`)**
    - Group/channel create, member management, read-state, guest links, moderation actions.
    - Why: biggest parity gap vs Kalium sample CLI and WireTUI workflows.
    - Complexity: **L**.

## Recommended Delivery Plan

### Slice 1 (security + reliability)

- Secure credential input
- 2FA handling
- `auth status/diagnose`

Outcome: production-safer login and faster troubleshooting.

### Slice 2 (multi-account operations)

- `wire sessions` command group
- logout safety modes (`soft|hard`) with explicit flags

Outcome: practical account lifecycle management without TUI dependency.

### Slice 3 (operator toolkit)

- `wire client` management
- `wire sync`/diagnostics
- server selection flow

Outcome: stronger support and SRE-style workflows.

## Design Guardrails

- Keep current architecture: **command -> service -> api client -> runtime**.
- Make every new command script-friendly (`--json`, stable exit codes, no implicit prompts).
- Use prompts only in explicitly interactive mode.
- Add typed error mapping (avoid string-matching exceptions where possible).
- Keep destructive operations gated with confirmations or `--yes`.
- Update docs/stories in the same PR as implementation to avoid plan drift.

## Feature Backlog (one-line view)

- `login`: secure input + 2FA
- `sessions`: list/use/remove
- `auth`: status/diagnose
- `client`: list/delete/fingerprint
- `server`: select/show
- `sync`: watch/status
- `diagnostics`: health/log summary
- `contacts`: find/connect
- `search`: global with sort/output modes
- `backup`: create/restore/status
- `convo/groups`: create/manage/membership

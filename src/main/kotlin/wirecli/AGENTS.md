# Main Module Guidance

This package contains production CLI code. Keep modules small, explicit, and testable.

## Dependency direction

- `Main.kt` -> `commands` + `runtime` only.
- `commands` -> feature service interfaces only.
- Feature services -> feature API client interfaces.
- Real API clients -> Kalium SDK.
- Stubs -> deterministic in-memory behavior for tests and `WIRE_BACKEND=stub`.
- `runtime` wires concrete implementations. Avoid moving wiring into commands or services.
- `config` and `validation` are shared support modules. Keep them dependency-light.

## Modules

### `commands/`

CLI parsing and output formatting. Commands should:
- define flags, arguments, help text, and exit behavior;
- call feature services;
- keep stdout clean for scripting;
- avoid Kalium SDK imports and business rules.

### `runtime/`

Composition root and backend selection. Runtime should:
- create real or stub backends;
- own lifecycle and shutdown;
- inject configured services into commands;
- keep feature modules free from global wiring.

### `auth/`

Authentication and session persistence. Owns:
- login/logout orchestration;
- active session store;
- session inventory serialization;
- auth-related exit codes and failures.

Other modules should ask auth services for active session state instead of reading session files directly.

### `profile/`

User profile reads/updates. Profile may depend on presence contracts for display needs, but keep profile-specific failures and formatting local.

### `presence/`

Presence read/update behavior and normalization. Keep unsupported backend values explicit as unknown-like states instead of silently dropping them.

### `device/`

Device listing, info, verification, and deletion. Keep user-facing device failure mapping local to this module.

### `conversation/`

Conversation list/get/create/delete and member count. Owns conversation display models and formatters. Do not place message behavior here.

### `message/`

Message send/fetch/watch/search/reaction/typing behavior. Owns message runtime timeout handling, message failure mapping, and message output views.

### `sync/`

Sync status, diagnostics, force-sync, reset, per-conversation diagnostics, and sync output formatting. Keep health calculation and output views separate from Kalium adapter code.

### `config/`

Kalium CLI configuration helpers. Keep environment/feature flag interpretation explicit and centralized.

### `validation/`

Shared validation helpers. Add here only when at least two modules need the same validation rule.

## Adding a feature

1. Start in the feature `*Contracts.kt`: model inputs, outputs, failures, and service/client interface.
2. Write tests for service behavior and failure paths.
3. Implement stub client behavior for deterministic tests.
4. Add real Kalium adapter only at the boundary.
5. Wire implementation in `runtime/KaliumRuntime.kt`.
6. Add command under `commands/` and Bats coverage when CLI behavior changes.

## Testing expectations

- Unit tests live under matching `src/test/kotlin/wirecli/<module>/` package.
- Test success and failure paths.
- Prefer fake/stub clients over mocking Kalium internals.
- Bats tests should assert CLI contract: args, stdout/stderr, exit codes, and JSON shape when relevant.

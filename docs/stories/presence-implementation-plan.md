# Presence Implementation Plan

## Goal
Deliver `docs/stories/presence.md` end-to-end with:
- `wire presence get`
- `wire presence set <status>`
- normalized presence in `wire profile`
- consistent auth/network/server failure handling and exit codes.

## What Needs To Be Implemented

### Required behavior
- Presence domain values normalized to: `online`, `busy`, `away`, `offline`, `unknown`.
- Shared normalization rules across `presence get` and `profile`.
- Setter accepts only writable values: `online`, `busy`, `away`, `offline`.
- `presence get` and `presence set` fail non-zero on unauthorized and backend/network failures.
- `profile` includes `Presence` and falls back to `unknown` if presence fetch fails but profile succeeds.

### Current state
- `presence get` and `presence set <status>` are implemented.
- `wire presence` is retained as a compatibility alias for `wire presence get`.
- Set-path contracts/services/adapters are implemented for stub and real backends.
- Bats coverage includes get/set behavior, failure semantics, and profile fallback.

## Phased Plan

## Phase 1 - Contracts and CLI shape
- Introduce `presence` command group with:
  - `presence get`
  - `presence set <status>`
- Extend presence contracts to support set operations.
- Define single-source status parsing/normalization rules.
- Compatibility retained: `wire presence` aliases `presence get`.

## Phase 2 - Implement `presence get` explicitly
- Move current read logic into `PresenceGetCommand`.
- Keep auth-guarded service path unchanged.
- Preserve output as a single normalized token.

## Phase 3 - Implement `presence set <status>`
- Add set flow in:
  - `PresenceService`
  - `AuthGuardedPresenceService`
  - `SessionBackedPresenceService`
  - `RealKaliumPresenceApiClient`
  - `StubPresenceApiClient`
- Validate input in command layer and return usage guidance for invalid values.
- Print deterministic success output token (`<status>`).

## Phase 4 - Cross-command consistency
- Ensure error mapping and exit behavior match existing contract (`AUTH_FAILED`, `UNAUTHORIZED`, `NETWORK_ERROR`, `SERVER_ERROR`, `VALIDATION_ERROR`).
- Keep `profile` fallback behavior (`Presence: unknown`) for partial failures.

## Phase 5 - Tests and docs
- Add/expand Bats presence stories in `test/bats/20_presence_stories.bats`.
- Cover: success, normalization (`not_available -> away`), invalid set input, unauthorized, network/server failures, profile fallback.
- Update README and story status once tests pass.

## File-Level Implementation Targets

### Update
- `src/main/kotlin/wirecli/Main.kt`
- `src/main/kotlin/wirecli/commands/PresenceCommand.kt`
- `src/main/kotlin/wirecli/runtime/KaliumRuntime.kt`
- `src/main/kotlin/wirecli/presence/PresenceContracts.kt`
- `src/main/kotlin/wirecli/presence/AuthGuardedPresenceService.kt`
- `src/main/kotlin/wirecli/presence/SessionBackedPresenceService.kt`
- `src/main/kotlin/wirecli/presence/RealKaliumPresenceApiClient.kt`
- `src/main/kotlin/wirecli/presence/StubPresenceApiClient.kt`
- `test/bats/20_presence_stories.bats`
- `README.md`

## Validation Plan
- Unit tests for normalization and set validation.
- Bats tests for story scenarios and exit codes.
- Full gate: `gradle check` and Bats suite passing.

## Definition of Done
- `presence get` and `presence set` fully implemented and discoverable in help.
- Story normalization and fallback rules satisfied.
- Unauthorized and backend failure handling is actionable and deterministic.
- Test suite covers story acceptance scenarios and passes.

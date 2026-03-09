# Real Authentication System Design

## Purpose
Define the production-ready system design for real authentication in `wire-cli`, replacing current stub transport while preserving command UX contracts from `docs/Stories.md`.

## Scope
- Real implementation for `login`, `logout`, and `profile`.
- Session persistence and cross-process reuse.
- Unauthorized/error handling with stable non-zero exit codes.
- Security hardening for credential input, token storage, and redaction.

## Non-Goals
- Full interactive multi-step TUI flows.
- Multi-account management UI beyond deterministic active-session policy.
- Realtime observer framework for long-lived terminal sessions.

## Requirements

### Functional
- `login` authenticates valid credentials and persists auth state.
- `logout` invalidates active session and removes local persisted active auth state.
- `profile` requires valid session, fetches current user, and prints stable readable output.
- Expired/invalid/missing session states return actionable re-auth guidance.

### Non-Functional
- Deterministic command behavior and exit codes.
- Safe handling of secrets at rest and in logs.
- Testability across unit, command, and bash integration layers.
- Incremental rollout with stub fallback for local testing.

## Architecture Overview

```text
CLI Commands (Clikt)
  -> AuthSessionService / ProfileService
      -> KaliumRuntime (CoreLogic + scopes)
          -> AuthenticationScope / GlobalScope / UserSessionScope
              -> Wire backend APIs

Support modules:
- SessionStore (secure local persistence)
- AuthErrorMapper + ExitCodes
- ProfileFormatter + ErrorPresenter
```

## Validated Reference Flow

The canonical Wire auth flow has been verified against local examples in `.local/kalium/sample/cli` and should be treated as source-of-truth behavior:

1. Resolve versioned auth scope from server config.
2. Execute login.
3. Handle 2FA branches (`Missing2FA`, `Invalid2FA`) with retry guidance.
4. Persist authenticated account via `addAuthenticatedAccount(...)`.
5. Resolve user session scope.
6. Ensure/register client (`client.getOrRegister(...)`).
7. Start sync for session liveness (`keepSyncAlwaysOn()`) when needed by follow-up operations.

Login is not complete until account persistence and session bootstrap succeed.

## Component Design

### 1) Command Layer (`commands/*`)
- Parses args/options and maps to typed inputs.
- Invokes service methods only.
- Renders stdout/stderr and returns mapped exit code.
- No SDK orchestration logic.

### 2) Runtime Layer (`runtime/KaliumRuntime`)
- Owns SDK bootstrap and lifecycle for command execution.
- Provides accessors for:
  - versioned auth scope resolution,
  - global session/account operations,
  - user session scope retrieval.
- Uses stable root path so sessions survive process restarts.

### 3) Auth Layer (`auth/*`)
- `AuthSessionService` orchestrates login/logout/session checks.
- `SessionResolver` decides active valid session and diagnostics view.
- `AuthErrorMapper` converts SDK variants to user-facing messages and exit codes.
- `SessionStore` persists minimal local active-session metadata.

### 4) Profile Layer (`profile/*`)
- `ProfileService` fetches current user via authenticated session scope.
- `ProfileFormatter` outputs stable fields (`Name`, `Email`) and safe fallback for optional fields.

## Canonical Flows

## Login Flow
1. Resolve server config and versioned authentication scope.
2. Call login with credential inputs.
3. If success, persist authenticated account in global scope.
4. Resolve user session scope.
5. Register/get client when needed.
6. Keep sync alive only for required command lifecycle window.
7. Persist local active-session marker and return success.

### Login Invariants
- Login is not complete until account persistence succeeds.
- Client registration branches must be explicitly handled.
- Any failure path must return deterministic exit code and actionable text.

## Profile Flow
1. Resolve active valid session from persisted state.
2. Deny immediately if missing/invalid/expired.
3. Fetch current user from authenticated scope.
4. Render stable output and return success.

## Logout Flow
1. Resolve active session.
2. Call authenticated logout with deterministic completion.
3. Remove local persisted active auth state.
4. Return success (or actionable failure if backend/local cleanup fails).

## Failure and Branch Handling

### Auth Scope / Server Resolution
- Unsupported or unknown server version -> explicit upgrade/support guidance.
- Server/network bootstrap failures -> network/server exit codes.

### Authentication
- Invalid credentials -> auth failed.
- Missing/invalid 2FA -> dedicated branch and retry guidance.
- Suspended/pending account states -> clear account-state errors.

### Branch Mapping Contract

| Kalium branch/category | CLI behavior requirement |
|---|---|
| Invalid credentials | `AUTH_FAILED` with concise retry guidance |
| Missing 2FA | dedicated non-zero branch asking for 2FA code |
| Invalid 2FA | dedicated non-zero branch with retry guidance |
| Pending activation / suspended | non-zero account-state message |
| Client limit / registration failure | non-zero with actionable client cleanup guidance |
| Network bootstrap/server failure | `NETWORK_ERROR` or `SERVER_ERROR` with short next step |

### Client Registration
- Too many clients -> actionable cleanup guidance.
- Missing auth factors for registration -> retry guidance.
- Generic registration failure -> non-zero with retry/support hint.

### Protected Command Authorization
- Missing session -> unauthorized with login instruction.
- Invalid/expired session -> unauthorized with re-auth instruction.

## Exit Code Contract

```kotlin
object ExitCodes {
    const val OK = 0
    const val AUTH_FAILED = 10
    const val UNAUTHORIZED = 11
    const val NETWORK_ERROR = 12
    const val SERVER_ERROR = 13
    const val VALIDATION_ERROR = 14
    const val UNKNOWN_ERROR = 1
}
```

## Data and Persistence Design

### Session Data Model (minimal)
- `userId`
- `server`
- `issuedAt` / `expiresAt` (when available)
- local active-session marker metadata

### Persistence Rules
- Use state/secret location, not generic config-only path.
- Enforce strict permissions:
  - directory `0700`
  - file `0600`
- Use atomic writes (temp file + rename).
- Keep serialized format stable and versioned.

### Path Topology
- Keep Kalium data root stable across runs (for example `~/.wire/kalium`).
- Keep CLI active-session marker separately and minimal.
- Path override precedence for marker file:
  1. `WIRE_SESSION_FILE`
  2. `XDG_CONFIG_HOME`
  3. `HOME`

## Security Design

### Credential Input
- Default to hidden interactive prompt for password.
- Support `--password-stdin` for automation.
- Avoid plain `--password` as primary path.

Current state note:
- Current implementation still supports `--password` directly; treat secure input modes as required hardening before production real-auth default.

### Secret Handling
- Never log password/token/session secrets.
- Central redaction utility for all error/log surfaces.
- Avoid storing unnecessary secret fields locally.

### Transport and Trust
- Enforce secure endpoint usage by default.
- Explicitly gate insecure development modes if ever allowed.

## Observability and Operations
- Emit concise user-facing errors; keep internal details in debug logs only.
- Classify failures into retryable/non-retryable.
- Retry only transient network/server failures with bounded backoff.
- Provide a deterministic `auth status`-style check in later phase for supportability.

## Testing Strategy

### Unit / Service
- Map SDK result variants to domain outcomes and exit codes.
- Session resolver behavior for valid vs invalid states.
- Formatter and redaction contracts.

### Command-Level
- Parse + invoke + output/exit assertions for `login/logout/profile`.

### Bash Integration (BDD)
- Scenario-driven tests for stories:
  - login success/failure,
  - cross-process persistence,
  - logout invalidation,
  - unauthorized profile behavior.
- Isolated temp HOME/session per test.

## Rollout Plan

### Phase 1: Real Auth Core
- Introduce real auth/profile clients and wire runtime selection.
- Implement canonical login sequence and profile fetch.
- Ensure profile path waits for sync liveness before fetching self user where required by SDK behavior.

### Phase 2: Hardening
- Enforce secure input/storage/redaction.
- Expand branch coverage for 2FA/client-limit/expired-session paths.

### Phase 3: Operational Maturity
- Add real-auth integration lane in CI with masked secrets.
- Add supportability commands and richer diagnostics.

## Open Design Decisions
- Whether to expose soft logout as a user-selectable mode (current implementation defaults to hard logout behavior).
- Final session file schema/versioning strategy.
- Explicit handling policy for multi-account active-session selection.

## Related Documents
- `docs/Stories.md`
- `docs/architecture/wire-sdk-cli-design.md`
- `.tmp/docs/real-authentication-implementation-research.md`

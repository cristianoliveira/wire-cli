# Feature plan: User search and connections

## Pre-analysis

### Problem
Current CLI can act on known user/conversation/device IDs, but it has weak discovery. Users and automation need a way to find people and manage connection lifecycle without the Wire UI.

### SDK capability
Kalium exposes:

`session.search`
- `searchUsers`
- `searchByHandle`
- `federatedSearchParser`
- `isFederationSearchAllowedUseCase`

`session.connections`
- `sendConnectionRequest`
- `acceptConnectionRequest`
- `cancelConnectionRequest`
- `ignoreConnectionRequest`
- `blockUser`
- `unblockUser`

`session.users`
- `getSelfUser`
- `observeSelfUser`
- `getUserInfo`
- `getKnownUser`
- `getAllKnownUsers`
- profile update helpers already partly used by `profile` commands

Docs:
- `docs/kalium/features.md`
- `docs/kalium/api-surface.md`

### Current code anchors
- Profile precedent:
  - `src/main/kotlin/wirecli/profile/ProfileContracts.kt`
  - `SessionBackedProfileService.kt`
  - `RealKaliumProfileApiClient.kt`
  - `StubProfileApiClient.kt`
- Runtime wiring:
  - `src/main/kotlin/wirecli/runtime/KaliumRuntime.kt`
- Command registration:
  - `src/main/kotlin/wirecli/Main.kt`
  - `src/main/kotlin/wirecli/commands/RootCommand.kt`

### Proposed CLI

```bash
wire user me [--json]
wire user get <user-id> [--json]
wire user search <query> [--limit <n>] [--json]
wire user handle <handle> [--json]

wire connection request <user-id> [--message <text>] [--json]
wire connection accept <user-id> [--json]
wire connection cancel <user-id> [--json]
wire connection ignore <user-id> [--json]
wire connection block <user-id> [--yes] [--json]
wire connection unblock <user-id> [--json]
```

### Scope cut
MVP should implement:
1. `wire user search <query>`
2. `wire user get <user-id>`
3. `wire connection request <user-id>`
4. `wire connection block|unblock <user-id>`

Defer accept/cancel/ignore until pending-request representation is inspected.

### Risks / unknowns
- Federated search may require domain parsing and server capability checks.
- User result ranking/pagination may vary by backend.
- Connection request result types need source inspection.
- Blocking is destructive enough to require `--yes` or clear confirmation.
- Stub backend needs deterministic users and connection states.

## Test-driven plan

### 1. Add contracts first
Create `src/main/kotlin/wirecli/user/UserContracts.kt` and `connection/ConnectionContracts.kt` or a combined small domain if simpler.

Tests first:
- search query cannot be blank
- limit has sane min/max
- user ID validates non-blank
- block requires explicit confirmation at command layer
- SDK failures map to stable domain errors

### 2. Stub adapters
Implement deterministic stub APIs:
- known users list
- search by display name/email/handle
- connection states: none, pending, connected, blocked

### 3. Services
Add:
- `SessionBackedUserService`
- `AuthGuardedUserService`
- `SessionBackedConnectionService`
- `AuthGuardedConnectionService`

Follow existing feature pattern: command -> service -> API client -> runtime.

### 4. Commands
Add:
- `UserCommand.kt`
- `UserSearchCommand.kt`
- `UserGetCommand.kt`
- `ConnectionCommand.kt`
- connection subcommands

Register in `Main.kt`.

### 5. Real Kalium adapter spike
Inspect vendor source for exact signatures under:
- `SearchScope`
- `ConnectionScope`
- `UserScope`

Then implement only MVP methods.

### 6. Output formats
Plain output should be readable:

```text
id: ...
name: ...
handle: ...
team: ...
connection: ...
```

JSON must be stable for scripts:

```json
{
  "schemaVersion": 1,
  "users": []
}
```

### 7. Quality gates
Run:

```bash
make format
make lint
make test-unit
```

## Acceptance criteria

- `wire user search` returns deterministic stub results and real backend mapped results.
- `wire user get` shows one user or clear not-found error.
- `wire connection request` handles already-connected/pending states clearly.
- `wire connection block` requires `--yes` or safe confirmation.
- JSON output is schema-versioned.
- No raw tokens, emails beyond intentional user-facing output, or SDK internals are logged.

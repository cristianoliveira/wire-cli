# wire-cli Ideas

## Candidate Command Taxonomy (inspired by Slack/Discord CLIs)

This is a proposal for organizing `wire-cli` into clear command families that work well for both humans and automation.

### 1) `wire auth`

- `wire auth login`
- `wire auth logout`
- `wire auth status`
- `wire auth diagnose`
- `wire auth list` (profiles/accounts)
- `wire auth use <profile>`

Why this family exists:
- Keeps all identity and token/session concerns in one predictable place.

### 2) `wire sessions`

- `wire sessions list`
- `wire sessions use <session-id>`
- `wire sessions remove <session-id> [--yes]`
- `wire sessions current`

Why this family exists:
- Multi-account workflows are common in mature chat CLIs and reduce context switching friction.

### 3) `wire convo`

- `wire convo list`
- `wire convo show <conversation-id>`
- `wire convo create --name <name> [--member <id> ...]`
- `wire convo members add/remove <conversation-id> <user-id>`
- `wire convo archive <conversation-id>`
- `wire convo unarchive <conversation-id>`

Why this family exists:
- Conversation lifecycle and metadata operations are usually grouped together for discoverability.

### 4) `wire chat`

- `wire chat send <conversation-id> --text "..."`
- `wire chat edit <conversation-id> <message-id> --text "..."`
- `wire chat delete <conversation-id> <message-id> [--yes]`
- `wire chat react <conversation-id> <message-id> --emoji ":thumbsup:"`
- `wire chat watch <conversation-id>`

Why this family exists:
- Message-level operations are the most frequent tasks and should be short, scriptable, and consistent.

### 5) `wire search`

- `wire search messages --query "..." [--conversation <id>]`
- `wire search users --query "..." [--domain <domain>]`
- `wire search global --query "..." --sort <mode>`

Why this family exists:
- Search usually spans users, messages, and conversations with shared flags.

### 6) `wire contacts`

- `wire contacts list`
- `wire contacts find --query "..."`
- `wire contacts connect <user-id>`

Why this family exists:
- Separates people discovery and relationship actions from message/conversation actions.

### 7) `wire client`

- `wire client list`
- `wire client show <client-id>`
- `wire client delete <client-id> [--yes]`
- `wire client key-packages status|refill`

Why this family exists:
- Device/client hygiene is an operational concern and benefits from dedicated commands.

### 8) `wire backup`

- `wire backup create`
- `wire backup list`
- `wire backup restore <backup-id> [--yes]`
- `wire backup status`

Why this family exists:
- Data portability/recovery should be explicit and safely gated.

### 9) `wire server`

- `wire server show`
- `wire server select [--backend <name>]`
- `wire server test`

Why this family exists:
- Backend selection and validation are frequent setup/troubleshooting tasks.

### 10) `wire sync`

- `wire sync status`
- `wire sync watch`
- `wire sync recover`

Why this family exists:
- Sync health is critical for reliability and should be visible from CLI.

### 11) `wire doctor`

- `wire doctor`
- `wire doctor auth`
- `wire doctor network`
- `wire doctor config`

Why this family exists:
- Mature CLIs include quick diagnostics to shorten debugging loops.

### 12) `wire config`

- `wire config get <key>`
- `wire config set <key> <value>`
- `wire config list`
- `wire config path`

Why this family exists:
- Keeps persistent local behavior and defaults transparent.

## Cross-Cutting Flags (recommended)

- `--output human|json|yaml`
- `--verbose` / `--log-level`
- `--limit`, `--cursor`, `--all`
- `--retry`, `--retry-max`
- `--dry-run`, `--yes`
- `--reason` (audit note for mutating operations)
- `--idempotency-key` (for write operations)

## Suggested Rollout

### Now

- `auth`, `sessions`, `doctor`
- cross-cutting output/error/safety flags

### Next

- `client`, `server`, `sync`, `search`, `contacts`

### Later

- `backup`, `convo`, `chat` advanced operations

## Kalium Feasibility Snapshot

Support levels below are based on current `.local/kalium` surfaces.

| Command family | Kalium support | Notes |
|---|---|---|
| `auth` | Partial | Strong login/2FA/domain flows; status/list/use/logout span multiple scopes. |
| `sessions` | Full | List/current/use/remove are directly available. |
| `convo` | Full | List/create/members/archive are present. |
| `chat` | Full | Send/edit/delete/react/watch primitives are present. |
| `search` | Partial | User search is strong; global message search/sort is weaker. |
| `contacts` | Full | List/find/connect primitives are present. |
| `client` | Partial | List/show/delete/status are strong; refill path is not first-class. |
| `backup` | Partial | Create/verify/restore exist; list/status/id-based restore are missing. |
| `server` | Partial | Building blocks exist; no dedicated top-level server scope. |
| `sync` | Partial | Status/watch strong; recover is fragmented across internals. |
| `doctor` | Missing | Pieces exist, but no consolidated diagnostics scope. |
| `config` | Missing | No generic runtime config get/set/list/path scope. |

## Kalium-First Opportunities (easy wins)

These can be implemented in `wire-cli` with thin adapters to existing Kalium scopes:

- `wire sessions`: `list/current/use/remove`
- `wire convo`: `list/create/members add/remove/archive`
- `wire chat`: `send/edit/delete/react/watch`
- `wire contacts`: `list/find/connect`
- `wire client`: `list/show/delete` + key-package status

## Missing Kalium Capabilities to Add (SDK backlog)

To unlock the full ideas roadmap cleanly, Kalium should expose a few additional stable APIs.

### Now (high impact for CLI)

- Stable public CLI-facing API facade (current external API boundary is still weak/placeholder).
- Promote key-package refill to first-class public `ClientScope` operation.
- Promote non-delicate sync foreground lifecycle API (`start/stop/watch`) instead of relying on delicate internals.
- Add public server operations (`store/select/test`) in global scope.
- Add `DoctorScope` with structured auth/network/config checks.

### Next

- Unified `sync recover` orchestration API.
- Backup inventory/status APIs plus id-based restore contract.
- Public one-shot conversation details by ID (`convo show` use-case).
- Public pending-contact-request read APIs in contacts/connection scope.

### Later

- Search API sort modes and stronger pagination semantics.
- Public config scope for runtime supported settings and paths.
- CLI-tailored event stream contracts with stable cursor/idempotency semantics.

## Kalium Constraints That Affect Priorities

- Some useful paths are internal/delicate today; avoid depending on them for default production commands.
- Feature support is API-version-gated (v4/v5/v6+ boundaries), so commands need capability checks.
- `wire-cli` currently uses local Kalium composite build; branch drift can change behavior/builds.
- Stub backend default can hide real-backend issues; feature rollout should require real-backend validation.
- Calling/media surfaces remain high risk on CLI JVM path and should stay out of near-term scope.

## Suggested Rollout (Kalium-adjusted)

### Now

- `auth`, `sessions`, `doctor` (CLI aggregation where needed)
- cross-cutting output/error/safety flags
- `contacts` and `client` read/write baseline

### Next

- `server`, `sync`, `convo`, `chat` core flows
- version-gated features only after capability probe checks

### Later

- `backup` list/status/id-restore
- advanced search/global sort
- richer recover/idempotency flows

## Notes

- Keep command handlers thin and preserve existing architecture: command -> service -> api client -> runtime.
- Default behavior should be non-interactive-friendly; prompts should be opt-in or explicitly interactive.
- Machine-readable output and stable exit codes are required for automation.

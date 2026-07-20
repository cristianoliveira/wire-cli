# Feature plan: Message search, delete, and reactions

## Pre-analysis

### Problem
Current CLI supports sending/fetching/watching/typing messages, but not common follow-up actions: finding messages, deleting mistakes, or reacting. Users and scripts must switch to UI or raw SDK code.

### SDK capability
Kalium `session.messages` exposes:

- `searchMessagesInConversation`
- `searchMessagesGlobally`
- `deleteMessage`
- `toggleReaction`
- `observeMessageReactions`
- `observeMessageReceipts`

Docs:
- `docs/kalium/features.md`
- `docs/kalium/messaging.md`
- `docs/kalium/api-surface.md`

### Current code anchors
- Commands: `src/main/kotlin/wirecli/commands/MessageCommand.kt`
- Existing message subcommands:
  - `MessageSendCommand.kt`
  - `MessageFetchCommand.kt`
  - `MessageWatchCommand.kt`
  - `MessageTypingCommand.kt`
- Service/contracts:
  - `src/main/kotlin/wirecli/message/MessageContracts.kt`
  - `SessionBackedMessageService.kt`
  - `RealKaliumMessageApiClient.kt`
  - `StubMessageApiClient.kt`
- Tests:
  - `src/test/kotlin/wirecli/message/`
  - `src/test/kotlin/wirecli/commands/`

### Proposed CLI

```bash
wire message search <query> [--conversation-id <id>] [--limit <n>] [--json]
wire message delete <conversation-id> <message-id> [--yes] [--json]
wire message react <conversation-id> <message-id> <emoji> [--json]
wire message reactions <conversation-id> <message-id> [--json]
wire message receipts <conversation-id> <message-id> [--json]
```

### Scope cut
MVP should implement:
1. `search`
2. `delete`
3. `react`

Defer `reactions` and `receipts` if SDK result mapping is noisy.

### Risks / unknowns
- Exact Kalium return/result types for search/delete/reaction need source inspection.
- Search pagination/ranking may differ between local cache and backend.
- `toggleReaction` behavior means command may add or remove existing reaction; UX must say `toggled` not always `added`.
- Delete permissions depend on message ownership/backend rules.

## Test-driven plan

### 1. Contract tests first
Add/extend `MessageContractsTest` for domain models:
- message search request validates non-blank query
- delete request validates conversation/message IDs
- reaction request validates non-blank emoji
- unhappy path maps permission/not-found failures

### 2. Stub service/API tests
Add tests for `StubMessageApiClient`:
- returns deterministic search results
- deletes existing message
- returns not-found for unknown message
- toggles reaction deterministically

### 3. Command tests
Add command tests:
- `wire message search hello --json` prints stable JSON
- `wire message delete conv msg --yes` exits 0
- delete without `--yes` fails or prompts safely according to local command pattern
- invalid IDs exit non-zero with actionable error

### 4. Real adapter spike
Inspect Kalium method signatures in vendor source, then implement adapter methods in `RealKaliumMessageApiClient`.

### 5. Wire command tree
Register new subcommands in `MessageCommand.kt`.

### 6. Quality gates
Run:

```bash
make format
make lint
make test-unit
```

## Acceptance criteria

- Search works in stub backend with plain and JSON output.
- Delete requires `--yes` or safe confirmation behavior.
- Reaction command reports toggle result clearly.
- All failures map to stable CLI errors and exit codes.
- No message plaintext is logged beyond intentional stdout output.

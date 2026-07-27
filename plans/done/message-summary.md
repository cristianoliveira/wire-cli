# Feature plan: Daily message summary

## Precondition

Implement after PR [#190](https://github.com/cristianoliveira/wire-cli/pull/190) merges. Build on `RecentMessagesQuery`; do not create a second message-filtering path.

## Problem

`message list` can select messages from today, one conversation, or explicit self mentions, but users still need to group structured output themselves to answer “what did I miss today?”. The CLI should provide a deterministic digest without external Python or an LLM dependency.

## Proposed CLI

```bash
wire message summary --since today
wire message summary --since 2026-07-23T08:00:00Z --received-only
wire message summary --since today --conversation-id <id> --mentions-me --json
```

Supported query options should mirror `message list`:

- `--since <today|ISO-date|ISO-timestamp>`
- `--conversation-id <id>`
- `--received-only`
- `--mentions-me`
- `--limit <1-100>`
- `--no-cache`
- `--json`
- `--full` for untruncated latest-message content

Extract and reuse the existing `--since` parser rather than copying it from `MessageListCommand`.

## Summary contract

Group selected messages by conversation, then sender. For each sender include:

- sender ID and display name
- message count
- latest message ID, timestamp, and content

For each conversation include:

- conversation ID and name
- total selected message count
- latest message timestamp
- sender summaries

Sort conversations by latest timestamp descending. Sort senders within a conversation by latest timestamp descending, then stable IDs for ties.

JSON should use a stable envelope:

```json
{
  "since": "2026-07-23T00:00:00Z",
  "conversations": [],
  "returnedMessages": 0,
  "truncated": false
}
```

`truncated` must be truthful. Request one extra message internally when an explicit/default summary limit is applied, trim before aggregation, and set `truncated` when the extra item exists. Never report page size as total.

## Design

- `MessageSummaryCommand` owns CLI parsing, service selection, rendering, and exit mapping.
- `MessageService.listRecentMessages(RecentMessagesQuery)` remains the only message read/filter path.
- A pure message-domain summarizer owns grouping and deterministic ordering; keep grouping logic out of the command.
- `MessageSummaryFormatter` owns human and JSON rendering.
- Runtime composition registers the command; it must not construct clients or read sessions.
- Summary is extractive and local: no network service receives message plaintext beyond the existing Wire fetch.

## Test-driven plan

### 1. Pure summary tests

Write failing tests for:

- messages from multiple conversations group independently
- repeated messages from one sender produce correct count and latest message
- multiple senders remain separate and deterministically ordered
- timestamp ties use stable sender/conversation IDs
- empty input produces an explicit empty summary
- blank display names fall back to IDs
- truncated input is represented without claiming a total

### 2. Command contract tests

Write failing tests proving:

- `--since today` resolves from an injected clock
- ISO date and timestamp values map into `RecentMessagesQuery`
- conversation, received-only, and mentions-me filters are forwarded unchanged
- invalid `--since`, blank conversation ID, and invalid limit fail before service access
- `--no-cache` uses the server-backed list path
- service failures preserve stderr and exit behavior

### 3. Formatter tests

Cover human and JSON output for:

- populated summary
- empty summary
- truncated summary
- escaped multiline content
- `--full` versus preview content

### 4. Implementation

1. Extract reusable since parsing from `MessageListCommand` with its existing tests preserved.
2. Add summary domain models and pure grouping logic.
3. Add human and JSON formatters.
4. Add and register `MessageSummaryCommand` under `message`.
5. Reuse `RecentMessagesQuery` and existing daemon/server paths.
6. Add deterministic stub data only where needed for end-to-end behavior.

### 5. Installed CLI coverage

Add Bats stories for:

- `message summary --since today`
- JSON schema and empty results
- conversation and self-mention scoping
- deterministic sender grouping
- invalid since/limit values
- unauthorized and network failures

## Acceptance criteria

- “What did I miss today?” requires one flat CLI call and no external aggregation script.
- Summary groups by conversation and sender with latest message per sender.
- All filters reuse `RecentMessagesQuery` semantics.
- Human stdout remains deterministic and script-friendly.
- JSON is valid, stable, and explicit about truncation.
- Happy, empty, validation, unauthorized, network, and server paths are tested.
- `make all` passes.

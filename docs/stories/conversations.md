# Conversations Stories

## Planned Stories

### 21) Conversations: `wire conversation list` displays all conversations `[Planned] [Must]`
As an LLM agent integrator, I want to list all conversations on my account so I can discover conversation IDs dynamically and route messages without hardcoding.

Acceptance criteria:
- Given I am authenticated, when I run `wire conversation list`, then I see a table/list of all conversations with columns: conversation ID, type (DM/group/channel), name/participants, participant count, and last activity timestamp.
- Given a conversation has no activity yet, when I view it, then last activity shows a placeholder (e.g., "never") and succeeds.
- Given there are no conversations, when I run the command, then I see "No conversations found" and exit code 0.
- When `--json` flag is used, output is valid JSON with consistent schema: `[{ id, type, name, participant_count, last_activity_ms, created_at_ms }]`.
- When `--json-lines` flag is used, output is newline-delimited JSON (one conversation per line), parseable by `jq` and shell scripts.
- Given I am not authenticated, when I run the command, then access is denied with "Session expired. Run `wire login` to re-authenticate." and exit code 11.
- Given the server returns an error, when I run the command, then I see the error with exit code 13 and guidance to retry.
- Response time is <2 seconds for typical accounts (≤100 conversations).

Notes:
- MVP scope: list all conversations with basic metadata; table and JSON formats.
- Conversation type values: "dm", "group", "channel".

---

### 22) Conversations: `wire conversation list --filter-type=...` filters by conversation type `[Planned] [Must]`
As a power user, I want to filter conversations by type so I can focus on group conversations or direct messages without viewing the entire list.

Acceptance criteria:
- Given I run `wire conversation list --filter-type=dm`, then I see only direct message conversations.
- Given I run `wire conversation list --filter-type=group`, then I see only group conversations.
- Given I run `wire conversation list --filter-type=channel`, then I see only channel conversations.
- Given I run `wire conversation list --filter-type=group,channel` (comma-separated), then I see both group and channel conversations, excluding DMs.
- Given an invalid type is specified (e.g., `--filter-type=invalid`), then I see error "Invalid type. Valid types are: dm, group, channel" and exit code 2.
- When `--json` flag is combined with `--filter-type`, the JSON output includes only filtered conversations.
- Given there are no conversations matching the filter, then I see "No conversations matching filter" and exit code 0.
- Given I am not authenticated, then exit code 11.

Notes:
- Filtering is client-side (fast, no server round-trip).
- All filter combinations work with `--json` and `--json-lines` output.

---

### 23) Conversations: `wire conversation list --json|--json-lines` outputs structured formats `[Planned] [Must]`
As an automation engineer, I want JSON and JSON Lines output so I can parse conversation lists in shell scripts and integrate with CI/CD pipelines.

Acceptance criteria:
- When `--json` flag is used, output is valid JSON array: `[{ id, type, name, participant_count, last_activity_ms, created_at_ms }, ...]`.
- When `--json-lines` flag is used, output is newline-delimited JSON (one conversation per line), parseable by `jq`, `grep`, and `awk`.
- Both formats include all core metadata fields; no truncation or abbreviation.
- Both formats can be piped to `jq` without errors: `wire conversation list --json-lines | jq '.id'`.
- When `--json` is combined with `--filter-type` or `--sort-by`, the output is filtered and sorted before JSON encoding.
- Given I am not authenticated, then exit code 11 (before attempting JSON output).
- JSON output is valid even if there are no conversations: `[]` (empty array for `--json`), nothing (no output for `--json-lines`).

Notes:
- JSON schema is stable and consistent across MVP and future versions.
- Both formats support piping to other tools; no special characters or escaping surprises.

---

### 24) Conversations: `wire conversation list --sort-by=...` sorts conversations `[Planned] [Should]`
As a support engineer, I want to sort conversations by last activity or name so I can quickly find recent conversations or locate a specific group by name.

Acceptance criteria:
- Given I run `wire conversation list --sort-by=last_activity`, then conversations are sorted by last activity timestamp, most recent first.
- Given I run `wire conversation list --sort-by=name`, then conversations are sorted alphabetically by name (A-Z).
- Given I run `wire conversation list --sort-by=participant_count`, then conversations are sorted by participant count, highest first.
- Given an invalid sort key is specified (e.g., `--sort-by=invalid`), then I see error "Invalid sort. Valid options are: last_activity, name, participant_count" and exit code 2.
- When `--sort-by` is combined with `--filter-type`, conversations are filtered first, then sorted.
- When `--json` flag is used with `--sort-by`, the JSON output is sorted before encoding.
- Sorting is fast (<100ms for typical accounts); result is deterministic and repeatable.
- Given I am not authenticated, then exit code 11.

Notes:
- MVP scope includes sorting; search is deferred to phase 2a.
- Sorting is client-side (all conversations loaded into memory and sorted).

---

### 25) Conversations: `wire conversation list --search=...` searches conversations by name `[Planned] [Should]`
As a power user with 50+ conversations, I want to search by name so I can quickly find "alerts-channel" or "finance-team" without scrolling.

Acceptance criteria:
- Given I run `wire conversation list --search=alerts`, then only conversations with "alerts" (case-insensitive) in the name are returned.
- Given I run `wire conversation list --search=bob@example.com`, then conversations with "bob" in the name or participant list are returned.
- Given no conversations match the search, then I see "No conversations matching search: 'alerts'" and exit code 0.
- When `--search` is combined with `--filter-type` and `--sort-by`, all three filters apply (search AND filter AND sort).
- When `--json` flag is used with `--search`, the JSON output includes only matching conversations.
- Search is case-insensitive and supports partial name matching (substring search).
- Given an empty search string (`--search=""`), then no filter is applied and all conversations are returned.
- Given I am not authenticated, then exit code 11.

Notes:
- MVP scope: DEFERRED to phase 2a [Should]; focus on basic listing and type filtering first.
- Search is client-side (substring matching on name and participant fields).
- Future: fuzzy search and relevance ranking can be added in phase 2b.

---

## Current CLI Contract (Conversations)

### `wire conversation list`

- Success output is a table (default) or JSON with conversation metadata (ID, type, name, participant count, last activity).
- Table format is human-readable; JSON/JSON Lines are machine-parseable.
- Unauthorized/missing sessions return exit code `11` with re-auth guidance.
- Network and server failures return exit code `13` with actionable retry messages.
- Empty conversation list returns a "No conversations found" message with exit code `0`.

### `wire conversation list --filter-type=<type>`

- Filters conversations by type: "dm", "group", "channel" (comma-separated for multiple).
- Invalid type returns exit code `2` with valid type enumeration.
- Filtered results are shown in table or JSON format as requested.
- Same exit codes as basic list command.

### `wire conversation list --sort-by=<key>`

- Sorts conversations by: "last_activity" (most recent first), "name" (A-Z), "participant_count" (highest first).
- Invalid sort key returns exit code `2` with valid key enumeration.
- Sorting is deterministic; repeated calls return same order.
- Works with `--filter-type` and `--json` flags.

### `wire conversation list --search=<query>`

- Searches conversations by name (substring, case-insensitive).
- Returns only conversations matching the query.
- No results returns exit code `0` with "No conversations matching search" message.
- Deferred to phase 2a; not implemented in MVP.

### `wire conversation list --json`

- Success output is valid JSON array: `[{ "id": "conv-abc", "type": "dm", "name": "alice@example.com", "participant_count": 2, "last_activity_ms": 1234567890000, "created_at_ms": 1234567000000 }, ...]`
- Empty list is valid: `[]`
- Unauthorized returns exit code `11` before JSON output.
- Server error returns exit code `13` with error message (not JSON).

### `wire conversation list --json-lines`

- Success output is newline-delimited JSON, one conversation per line.
- Each line is valid JSON object.
- Parseable by `jq`, `grep`, `awk`, and similar tools.
- Empty result produces no output (exit code `0`).
- Unauthorized returns exit code `11`.

### Example Table Output

```
CONVERSATION ID       TYPE      NAME                    PARTICIPANTS  LAST ACTIVITY
conv-abc123xyz        dm        alice@example.com       2              2 minutes ago
conv-group-001        group     engineering-team        12             5 minutes ago
conv-channel-alerts   channel   alerts-and-incidents    45             30 seconds ago
```

### Example JSON Output

```json
[
  {
    "id": "conv-abc123xyz",
    "type": "dm",
    "name": "alice@example.com",
    "participant_count": 2,
    "last_activity_ms": 1701234567000,
    "created_at_ms": 1700000000000
  },
  {
    "id": "conv-group-001",
    "type": "group",
    "name": "engineering-team",
    "participant_count": 12,
    "last_activity_ms": 1701234200000,
    "created_at_ms": 1600000000000
  }
]
```

### Example JSON Lines Output

```
{"id":"conv-abc123xyz","type":"dm","name":"alice@example.com","participant_count":2,"last_activity_ms":1701234567000,"created_at_ms":1700000000000}
{"id":"conv-group-001","type":"group","name":"engineering-team","participant_count":12,"last_activity_ms":1701234200000,"created_at_ms":1600000000000}
```

### Exit Codes

| Code | Scenario | Recovery |
|------|----------|----------|
| 0 | Success (list returned, or no conversations found) | None |
| 1 | Degraded sync (can't list reliably; try again) | Run `wire doctor` or wait for sync |
| 2 | Usage error (invalid filter, sort, or flag) | Check `wire conversation list --help` |
| 11 | Unauthorized (session expired or invalid) | Run `wire login` |
| 13 | Server error (network, backend failure) | Retry or contact support |

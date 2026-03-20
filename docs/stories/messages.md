# Messages Stories

## Planned Stories

### 26) Messages: `wire message send` sends plain text message `[Planned] [Must]`
As a developer building a CLI bot, I need to send plain text messages to a conversation so I can implement automated responses.

Acceptance criteria:
- Given I am authenticated, when I run `wire message send --conversation-id <id> "hello world"`, then the message is sent successfully with exit code 0.
- When message is sent, the output shows the message ID and timestamp, confirming successful delivery.
- Given I have long text (1000+ characters), when I send it, then it is transmitted correctly and fully delivered.
- When text contains special characters (quotes, newlines, emojis), it is escaped properly and sent without corruption.
- Given I am not authenticated, when I run the command, then exit code 11 (UNAUTHORIZED) with a login prompt.
- Given the conversation doesn't exist, when I send a message, then exit code 14 (NOT FOUND) with a clear error message.
- Given rate limit is exceeded, when I try to send, then exit code 12 (RATE LIMITED) with retry guidance (e.g., "Retry in 30 seconds").
- When message is sent via stdin pipe (e.g., `echo "hi" | wire message send --conversation-id <id>`), it works correctly.
- Message delivery time is < 1 second for typical network conditions.

Notes:
- MVP scope: plain text only. Rich formatting (bold, italics, links) deferred to Phase 2b.

---

### 27) Messages: `wire message fetch` fetches recent messages `[Planned] [Must]`
As a developer, I need to fetch recent messages from a conversation so I can analyze conversation history and build reactive bots.

Acceptance criteria:
- Given I am authenticated, when I run `wire message fetch --conversation-id <id>`, then I get the last 10 messages by default.
- Each message includes: sender_id, sender_name, timestamp (ISO8601), content, and message_id.
- Messages are ordered by timestamp (oldest first).
- Given an empty conversation, when I fetch messages, then exit code is 0 with an empty result (not treated as an error).
- When `--limit 5` is specified, then exactly 5 messages are returned (or fewer if fewer exist).
- When `--limit` exceeds max (e.g., 200 requested), then it is capped at safe limit (100) with a warning.
- Default text output format is human-readable: `[timestamp] sender_name: content`.
- Given I am not authenticated, when I run the command, then exit code 11 (UNAUTHORIZED).
- Given the conversation doesn't exist, when I fetch, then exit code 14 (NOT FOUND).

Notes:
- MVP scope: basic fetching with default limit. Pagination and advanced filtering deferred to Phase 2.

---

### 28) Messages: `wire message fetch --from <user-id>` filters by sender `[Planned] [Must]`
As a bot developer, I need to filter messages by sender so I can distinguish between system messages and user messages, or react only to specific users.

Acceptance criteria:
- Given I run `wire message fetch --conversation-id <id> --from <user-id>`, then only messages from that user are returned.
- Works in combination with `--limit` parameter: `--from <user-id> --limit 5` returns up to 5 messages from that user.
- Works with `--format` parameter: `--from <user-id> --format json` returns filtered results in JSON.
- If no messages match the filter, an empty result is returned (exit code 0, not an error).
- Can combine multiple filters: `--from <user-id> --limit 5 --format json` works correctly.
- Example: filtering to messages from "alice@domain" returns only Alice's messages in order.

Notes:
- Reduces noise for bots. Server-side filtering preferred if Kalium SDK supports it, otherwise client-side.

---

### 29) Messages: `wire message fetch --format json|text|jsonlines` outputs multiple formats `[Planned] [Must]`
As a developer, I need multiple output formats so I can pipe messages to standard Unix tools (jq, grep, awk) for automation.

Acceptance criteria:
- Given I run `wire message fetch --format text`, then output is human-readable with format `[timestamp] sender_name: content`.
- When `--format json` is used, output is valid JSON with a `messages` array containing message objects.
- When `--format jsonlines` is used, one JSON object per line (streaming-friendly format), where each line is valid JSON.
- JSON output works with `jq` pipelines without errors: `wire message fetch --format json | jq '.messages[0].content'`.
- JSON Lines output can be piped to while loops for processing: `wire message fetch --format jsonlines | jq -r '.content'`.
- Each message object includes: id, sender_id, sender_name, timestamp, and content (all required fields).
- Timestamps in JSON are ISO8601 format (machine-parseable, e.g., "2023-11-15T14:30:00Z").
- Default format (no flag) is human-readable text.
- Works with all other filters: `--from <user> --limit 5 --format json` applies all filters then formats output.

Notes:
- JSON output enables Unix piping and automation. Essential for scripting and CI/CD integration.

---

### 30) Messages: `wire message fetch --follow` streams new messages in real-time `[Planned] [Should]`
As a bot developer, I want to watch a conversation for new messages in real-time (like `inotify`) so I can build reactive bots that respond immediately when messages arrive.

Acceptance criteria:
- Given I run `wire message fetch --conversation-id <id> --follow`, then the command keeps the connection open and streams new messages as they arrive.
- First, recent messages (default 10 or `--limit N` if specified) are fetched and displayed.
- After initial history, new messages are streamed as they arrive (delivery latency < 1 second).
- Default text format: one message per line with `[timestamp] sender_name: content`.
- With `--format jsonlines`, each new message is a JSON object on its own line (streaming-friendly).
- Can be interrupted gracefully with Ctrl+C (exit code 0, not an error).
- If connection drops, graceful reconnection is attempted automatically (up to 3 retries with exponential backoff).
- Works with filters: `--follow --from <user-id>` streams only that user's messages.
- Can combine with `--limit`: `--follow --limit 20` fetches last 20 messages from history, then streams new arrivals.
- If a network error occurs and retries exhausted, exit code 13 with recovery guidance (e.g., "Connection lost. Check your network or try again.").
- Can use `--since <timestamp>` to start streaming from a specific point (ISO8601 format, Phase 2a extension).
- Can use `--new-only` flag to skip history and only stream new arrivals (Phase 2a extension).

Notes:
- Phase 2a (deferred from MVP). Enables reactive bots without polling. Depends on Kalium SDK streaming support.
- Test with bash: `wire message fetch --conversation-id <id> --follow | grep 'ping'`

---

### 31) Messages: `wire message fetch --follow --from <user-id>` filters streaming messages `[Planned] [Should]`
As a bot developer, I want to filter streaming messages by sender so I can watch only specific users without parsing every message.

Acceptance criteria:
- Given I run `wire message fetch --conversation-id <id> --follow --from <user-id>`, then only messages from that user are streamed in real-time.
- Reduces bandwidth and processing overhead (server-side filtering preferred if available).
- Works with output formats: `--follow --from <user> --format jsonlines` streams filtered results as JSON objects.
- Can combine with `--limit` and other filters: `--follow --from <user> --limit 5 --format json`.
- Example use case: `wire message fetch --conversation-id <id> --follow --from system@domain` watches for messages only from "system@domain" to react on alerts.

Notes:
- Phase 2a (deferred from MVP). Simple extension combining --follow and --from logic.

---

## Current CLI Contract (Messages)

### `wire message send`

- **Input**: `--conversation-id <id>` (required), message text (from argument or stdin)
- **Success Output**: Message ID, timestamp, exit code 0
  - Example: `"Message sent successfully. ID: msg-abc123, sent at: 2023-11-15T14:30:00Z"`
- **Error Output**: Error message with exit code (11/12/13/14)
- **Rate Limiting**: Graceful handling with exit code 12 and retry guidance
- **Stdin Support**: `echo "hello" | wire message send --conversation-id <id>` works correctly

### `wire message fetch`

- **Input**: 
  - `--conversation-id <id>` (required)
  - `--limit N` (optional, default 10, max 100)
  - `--from <user-id>` (optional, filter by sender)
  - `--format text|json|jsonlines` (optional, default text)
- **Success Output**:
  - Text format (default): Human-readable `[timestamp] sender_name: content` (one per line)
  - JSON format: Valid JSON with `messages` array
  - JSON Lines format: One JSON object per line
- **Exit Codes**: 0 (success), 11 (unauthorized), 14 (not found), 13 (server error)

### `wire message fetch --follow`

- **Input**: 
  - `--conversation-id <id>` (required)
  - `--limit N` (optional, fetches N historical messages before streaming)
  - `--from <user-id>` (optional, filter streaming messages)
  - `--format text|json|jsonlines` (optional, default text)
  - `--since <timestamp>` (optional, Phase 2a, start from specific point)
  - `--new-only` (optional, Phase 2a, skip history, stream only new)
- **Behavior**: Fetches recent messages, then streams new arrivals in real-time
- **Stream Format**: One message per line (text) or one JSON object per line (jsonlines)
- **Interruption**: Ctrl+C exits gracefully with exit code 0
- **Reconnection**: Automatic retry (3 attempts, exponential backoff) on connection drop
- **Exit Codes**: 0 (success/graceful interrupt), 13 (network error), 11 (unauthorized)

### `wire message typing`

- **Input**:
  - `<conversation-id>` (required)
  - `--while-pid <pid>` (required)
- **Behavior**:
  - Emits `STARTED` immediately.
  - Sends heartbeat `STARTED` updates every few seconds while the PID is alive.
  - Emits `STOPPED` when the PID exits.
  - Fails validation when PID is invalid or not running.
- **Exit Codes**: 0 (success), 11 (unauthorized), 12 (network/timeout), 13 (server/not found), 14 (validation)

### Message Object Schema

```json
{
  "id": "msg-abc123xyz",
  "sender_id": "user-12345@domain.com",
  "sender_name": "Alice Johnson",
  "timestamp": "2023-11-15T14:30:00Z",
  "content": "Hello, this is a message"
}
```

### Exit Codes

| Code | Scenario | Recovery |
|------|----------|----------|
| 0 | Success (message sent, fetched, or graceful interrupt) | None |
| 1 | Degraded sync (try again, run `wire doctor`) | Run `wire doctor status` |
| 2 | Usage error (invalid flag or syntax) | Check `wire message --help` |
| 11 | Unauthorized (session expired or invalid) | Run `wire login` to re-authenticate |
| 12 | Rate limited (exceeded send quota) | Retry after indicated delay |
| 13 | Server or network error | Retry or check `wire doctor diagnose` |
| 14 | Not found (conversation doesn't exist) | Verify ID with `wire conversation list` |

### Example Pipelines

#### Send a message
```bash
wire message send --conversation-id conv-abc123 "hello world"
```

#### Fetch recent messages
```bash
wire message fetch --conversation-id conv-abc123 --limit 5
```

#### Filter messages by sender
```bash
wire message fetch --conversation-id conv-abc123 --from alice@domain --limit 10
```

#### JSON output for machine parsing
```bash
wire message fetch --conversation-id conv-abc123 --format json | jq '.messages[] | .content'
```

#### JSON Lines for streaming/piping
```bash
wire message fetch --conversation-id conv-abc123 --format jsonlines | \
  jq -r 'select(.content | contains("ping"))'
```

#### Watch for messages in real-time
```bash
wire message fetch --conversation-id conv-abc123 --follow | grep "alert"
```

#### Stream only from a specific user
```bash
wire message fetch --conversation-id conv-abc123 --follow --from system@domain
```

#### Send typing status (Kalium-compatible STARTED/STOPPED)
```bash
long-running-task &
wire message typing conv-abc123 --while-pid $!
```

#### Keep typing while a background task runs
```bash
long-running-task &
wire message typing conv-abc123 --while-pid $!
```

#### Bot example: React to "ping" with "pong"
```bash
wire message fetch --conversation-id conv-abc123 --follow --format jsonlines | \
  jq -r 'select(.content | contains("ping")) | .sender_id' | \
  while read sender; do
    wire message send --conversation-id conv-abc123 "pong!"
  done
```

#### Count messages per sender
```bash
wire message fetch --conversation-id conv-abc123 --limit 100 --format jsonlines | \
  jq -r '.sender_name' | sort | uniq -c
```

#### Export messages to CSV (Phase 2)
```bash
wire message fetch --conversation-id conv-abc123 --format jsonlines | \
  jq -r '[.timestamp, .sender_name, .content] | @csv'
```

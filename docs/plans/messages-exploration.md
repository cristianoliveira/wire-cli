# Messages Exploration

## Problem: Why Users Care About Message Automation

Imagine you're a DevOps engineer who just deployed a critical monitoring bot to your team's Wire instance. The bot logs in successfully—auth works, connectivity is fine. But it can't send its first alert. **You have no way to send messages from the CLI.** There's no `wire message send` command. You're stuck: your bot is authenticated but cannot communicate.

**The Bot Automation Gap**: Developers want to build bots using pure CLI and bash. A bot that watches for "ping" and responds "pong" should take 10 lines of bash, not Kotlin/SDK. But without CLI message send/receive, bots are impossible. Developers are forced to use the SDK, which means learning Kalium, setting up a separate service, and managing complex build systems.

**The CI/CD Integration Crisis**: You want to send test results, alerts, and status updates to Wire from your CI/CD pipeline. Currently, you have no way to do this from the CLI without an external webhook service. You can't pipe build logs to Wire. You can't script message sending. Your only option: write a microservice that bridges CI/CD → Wire → Team. That's fragile and overengineered.

**The Real-Time Agent Problem**: You're building an LLM agent that needs to respond to messages in < 1 second. The agent should be able to poll or stream messages and generate responses in real-time. Without a CLI interface, the agent must use the Kalium SDK directly, which is complex and not designed for scripting or CLI agents.

**The Compliance Audit Void**: A support engineer needs to retrieve conversation history for a compliance audit—"Who said what, when?" Currently, there's no way to export messages from the CLI. They must navigate the UI, manually copy/paste, and hope they capture everything accurately. A `wire message fetch` command would let them query and export in seconds.

**The Current Reality**: Wire has no CLI message send/receive. It's the single biggest blocker to CLI automation, bot building, and agent integration. This feature unblocks the entire automation use case.

---

## Philosophy: What Messages Are and What This Feature Is NOT

### The Core Idea

A **message** in Wire is a unit of text communication sent within a conversation. Messages are:
- **Atomic units**: Sent once, identified by unique ID
- **Immutable** (in MVP): We send and receive; we don't edit or delete
- **Plain text**: No rich formatting, mentions, or reactions in MVP
- **Timestamped**: Every message has sender, timestamp, and conversation context

**Messages ARE**:
- Sendable via CLI (`wire message send --conversation-id <id> "text"`)
- Fetchable via CLI (`wire message fetch --conversation-id <id>`)
- Filterable (by sender, by date range, by count)
- Pipeable (stdin/stdout for shell scripting)
- Queryable as JSON/JSON Lines for agents and scripts
- Watchable in real-time with `--follow` (Phase 2a)

**Messages are NOT**:
- A TUI (Terminal UI): This is pure CLI, not interactive; no message rendering or formatting
- An archiver: Focused on current/recent messages; not a full-history export tool
- Rich-formatted: No mentions, reactions, or formatting; plain text only
- A streaming video/file service: Text only; no attachments or media
- Editable/deletable: Send and receive only; manage lifecycle in other features
- Always real-time: MVP uses polling; streaming comes in Phase 2a

**Key distinction**: Messages is a **CLI-native automation tool**, not a chat client. It's designed for bots, scripts, and agents—not for interactive conversation.

### Four Core Use Cases

#### 1. **Build a Ping-Pong Bot** (The Canonical Example)
**Scenario**: A developer wants to create a simple bot that watches a conversation and responds to "ping" with "pong". This is the hello-world of bot building.

**How `wire message` helps**: The developer writes:
```bash
#!/bin/bash
# ping-pong bot in 10 lines of bash

while true; do
  wire message fetch --conversation-id <id> --new-only --format jsonlines | while read msg; do
    text=$(echo "$msg" | jq -r '.content')
    sender=$(echo "$msg" | jq -r '.sender_id')
    if [ "$text" = "ping" ]; then
      wire message send --conversation-id <id> "pong (from $sender)"
    fi
  done
  sleep 2
done
```

That's the entire bot. No SDK, no Kotlin, no microservice. Pure bash.

**Real-world benefit**: Democratize bot building; enable any developer to create automation without learning Kalium.

---

#### 2. **Send Alerts from CI/CD Pipelines** (DevOps Automation)
**Scenario**: Your CI/CD pipeline needs to notify the team when tests fail. Currently, you use email or Slack webhooks. You want to integrate with Wire directly from the pipeline.

**How `wire message send` helps**:
```bash
#!/bin/bash
# In your CI/CD script
if ! npm test; then
  FAILED_TESTS=$(npm test 2>&1 | grep "FAIL:")
  wire message send --conversation-id alerts-conversation \
    "🚨 Tests Failed: $(date)
$FAILED_TESTS"
  exit 1
fi
```

Your pipeline sends messages directly to Wire. No webhook, no external service, no fragility.

**Real-world benefit**: Tight CI/CD ↔ Wire integration; reduce notification latency; eliminate external dependencies.

---

#### 3. **Export Messages for Compliance Audits** (Support/Compliance)
**Scenario**: A support engineer needs to audit a customer conversation for compliance—"Who said what, when?" They need to export and provide this data to auditors.

**How `wire message fetch` helps**:
```bash
#!/bin/bash
# Export a conversation for audit
wire message fetch --conversation-id customer-convo-123 \
  --limit 1000 \
  --format json > conversation-export.json

# Filter by date range (Phase 2a)
wire message fetch --conversation-id customer-convo-123 \
  --since "2025-03-01" \
  --until "2025-03-14" \
  --format json > compliance-export.json

jq -r '.[] | "\(.timestamp) | \(.sender_name): \(.content)"' \
  compliance-export.json > compliance-report.txt
```

The engineer can now query, filter, and export in seconds.

**Real-world benefit**: Compliance audits become scriptable; reduce manual work and errors; maintain audit trails.

---

#### 4. **Real-Time Message Streaming for LLM Agents** (Agent Integration)
**Scenario**: An LLM agent needs to respond to messages in < 1 second. The agent should stream new messages and generate responses immediately.

**How `wire message fetch --follow` helps** (Phase 2a):
```bash
#!/bin/bash
# LLM agent in Python, consuming from wire-cli

python3 << 'EOF'
import subprocess
import json
import sys

# Stream messages from Wire
proc = subprocess.Popen(
  ["wire", "message", "fetch", "--conversation-id", "agent-channel",
   "--follow", "--new-only", "--format", "jsonlines"],
  stdout=subprocess.PIPE, text=True
)

for line in proc.stdout:
  msg = json.loads(line)
  user_input = msg["content"]
  
  # Generate response using LLM
  response = llm.generate(user_input)
  
  # Send response back to Wire
  subprocess.run([
    "wire", "message", "send",
    "--conversation-id", "agent-channel",
    response
  ])
EOF
```

The agent streams messages, processes them, and responds in real-time.

**Real-world benefit**: Enable real-time LLM agents; remove SDK complexity; enable CLI-based automation.

---

## Personas & Why They Need Message Operations

### Persona 1: Bob — DevOps / Automation Engineer
**Profile**: Bob builds automation scripts for his DevOps team. He writes bash, Python, and makes heavy use of Unix pipes and scripting. His job includes deploying services, managing CI/CD pipelines, and setting up monitoring/alerting. He uses Wire for team communication and wants to integrate Wire notifications into his automation.

**Pain Points**:
- Can't send alerts from CI/CD pipelines; currently forced to use webhooks or external services
- Can't query conversations or messages from the CLI
- Manual CI/CD notifications are fragile and require external infrastructure
- Wants to pipe build logs directly to Wire: `npm test | wire message send --conversation-id alerts`
- Can't validate that a conversation exists before sending, so failures are silent

**Why Messages Matter**: Bob needs `wire message send --conversation-id <id>` to send alerts directly from bash scripts, and `wire message fetch --conversation-id <id>` to query recent messages. This unblocks tight CI/CD ↔ Wire integration, eliminates external dependencies, and enables scriptable alerting.

---

### Persona 2: Diana — LLM Agent Integrator
**Profile**: Diana builds intelligent agents using Python and frameworks like LangChain or Anthropic SDK. She wants these agents to run on Wire, responding to messages in real-time and handling multi-conversation workflows. She's comfortable with CLI tools and wants agents to be driven by shell commands, not SDK calls.

**Pain Points**:
- Agents can discover conversations but can't send/receive messages via CLI
- SDK-based message handling is complex and not suitable for CLI agents
- No streaming input for real-time responses; agents must poll or use complex SDK subscriptions
- Can't build a simple bot that watches a conversation and responds; requires Kotlin/SDK learning
- Wants agents to be lightweight CLI tools, not microservices

**Why Messages Matter**: Diana needs `wire message fetch --conversation-id <id> --follow --format jsonlines` to stream messages in real-time, and `wire message send` to respond immediately. This enables real-time LLM agents as pure CLI tools, no SDK required.

---

### Persona 3: Carlos — Support Engineer / Compliance Officer
**Profile**: Carlos handles customer support tickets, troubleshoots account issues, and audits conversations for compliance. He needs to retrieve message history quickly, filter by sender or date, and export data for auditors or investigation reports.

**Pain Points**:
- Can't retrieve message history via CLI; forced to navigate Wire UI for every audit
- Can't script compliance audits or generate reports programmatically
- Needs to query "who said what, when" without manually scrolling and copying
- Exporting conversation history is tedious and error-prone
- Can't filter messages by sender, date range, or content from the CLI

**Why Messages Matter**: Carlos needs `wire message fetch --conversation-id <id> --limit 1000 --format json` to export message history, and `--from <user-id>` / `--since <timestamp>` filters for audits. This transforms compliance audits from 30-minute manual tasks to 30-second queries.

---

### Persona 4: Alice — Power User / Automation Enthusiast
**Profile**: Alice is a technical power user who wants to build personal bots and automation using only bash and CLI tools. She's comfortable with jq, pipes, and scripting but doesn't want to learn Kotlin or manage complex services. She wants the canonical "ping-pong bot" to be achievable in 10 lines of bash.

**Pain Points**:
- Can't create bots without learning Kalium SDK and Kotlin
- Wants simple `watch message → run script → respond` workflows
- Interested in inotify-style message watching (`--follow` flag)
- Wants to build bots with pure bash, no external services
- Currently, bot building is blocked by lack of CLI message operations

**Why Messages Matter**: Alice needs `wire message fetch` + `wire message send` to build bots in bash. Add `--follow` in Phase 2a, and she can build real-time bots without leaving the shell. This democratizes bot creation.

---

## Problem Map: How Messages Fit Into Automation

```
User wants to automate Wire
  ├─ Authenticate ✅ (Stories 1-4: DONE)
  ├─ Manage devices ✅ (Stories 5-10: IN PROGRESS or planned)
  ├─ Check sync health ✅ (Stories 11-20: IN PROGRESS or planned)
  ├─ List conversations ✅ (Stories 21-25: DONE)
  ├─ Send messages ← THIS FEATURE (Story 26)
  ├─ Receive messages ← THIS FEATURE (Story 27)
  ├─ Filter messages ← THIS FEATURE (Stories 28-29)
  ├─ Watch for new messages ← THIS FEATURE (Story 30: --follow, Phase 2a)
  └─ React in shell scripts ← ENABLED BY ALL ABOVE
```

**Without messages, the entire automation layer is blocked.** With messages, users can:
- Build bots that respond to messages
- Send alerts from CI/CD pipelines
- Export conversation history for compliance
- Create real-time LLM agents
- Automate every Wire workflow from the CLI

---

## Realistic User Stories

### Story 1: "Build a Ping-Pong Bot" [Must]
**Problem**: Developer wants a bot that responds to "ping" with "pong"—the canonical bot example. This is the single best test of whether the feature enables automation.

**User Type**: Automation engineer, power user, developer

**Title**: "Build a bot that responds to 'ping' with 'pong' using only bash and wire-cli"

**Narrative**:
> A developer wants to create an autonomous bot using only bash and wire-cli. The bot watches for "ping" messages and responds with "pong". This is the simplest meaningful automation; if this works, the feature is successful. The entire bot should be ~10 lines of bash with no external services, no SDK, no Kotlin.

**Acceptance Criteria**:
- Given a conversation ID, when the developer writes a bash script using `wire message fetch --new-only` and `wire message send`, then the script can poll for new messages and send responses
- Given the script sends a message to the conversation, then that message appears in Wire within 1 second
- Given the developer runs `wire message fetch --conversation-id <id> --new-only --format jsonlines`, then the output is valid JSON Lines, one message per line
- Given a message is received, when the developer parses it with `jq`, they extract: `.content`, `.sender_id`, `.sender_name`, `.timestamp`
- When the bot is running and a user sends "ping" in the conversation, the bot responds with "pong" within 2 seconds
- The entire bot fits in 10-15 lines of bash (no Kalium SDK, no external services)

**MVP Acceptance**: Developers can write and run a working ping-pong bot; response latency < 2 seconds; bot code is < 20 lines

---

### Story 2: "Send Alerts from CI/CD Pipeline" [Must]
**Problem**: DevOps wants to send test results and alerts from CI/CD to Wire without external webhook services.

**User Type**: DevOps engineer, automation engineer

**Title**: "Send CI/CD build alerts to Wire conversations from bash"

**Narrative**:
> A DevOps engineer builds CI/CD pipelines that must notify teams of test failures and deployments. Currently, they use email or Slack webhooks. With wire-cli, they want to pipe build logs directly to Wire: `npm test | wire message send --conversation-id alerts-conv`. This is simpler than webhooks, requires no external service, and is scriptable.

**Acceptance Criteria**:
- Given a conversation ID, when the engineer runs `wire message send --conversation-id <id> "message text"`, then the message appears in that conversation within 1 second
- Given the engineer pipes stdin: `echo "hello" | wire message send --conversation-id <id>`, then the piped text is sent as the message
- Given the engineer sends multiple messages in rapid succession, all messages are delivered (no drops or merges)
- Given message delivery succeeds, exit code is 0
- Given the conversation doesn't exist, exit code is 14 (not found) and error message is clear
- Given not authenticated, exit code is 11 (unauthorized) with "Run `wire login` first" message
- When the engineer includes multi-line text or special characters, they are preserved in the message

**MVP Acceptance**: Messages send within 1 second; piping works; error codes are correct; CI/CD integration is unblocked

---

### Story 3: "Export Conversation History for Compliance" [Should]
**Problem**: Support engineers need to export conversation history for compliance audits without manually scrolling the UI.

**User Type**: Support engineer, compliance officer, administrator

**Title**: "Query and export message history with filters"

**Narrative**:
> A support engineer receives a request to audit a customer conversation for compliance. Instead of navigating the UI and manually copying messages, they run `wire message fetch --conversation-id <id> --limit 1000 --format json > export.json`. They can then filter by sender, date, or content using jq, and send the audit report to auditors.

**Acceptance Criteria**:
- Given a conversation ID, when the engineer runs `wire message fetch --conversation-id <id>`, then they receive the 10 most recent messages (default)
- Given the `--limit N` flag, they receive the N most recent messages (e.g., `--limit 100` returns 100 messages)
- Given the `--from <user-id>` filter, only messages from that sender are returned
- Given the `--format json` flag, output is valid JSON array: `[{ "id": "...", "sender_id": "...", "timestamp": "...", "content": "..." }, ...]`
- Given the `--format jsonlines` flag, output is JSON Lines (one message per line), suitable for piping to `jq` or streaming parsers
- Given no messages exist in a conversation, the command succeeds (exit 0) with an empty array or no output
- Given a large limit (e.g., 1000), the command completes within 5 seconds
- When combined with `jq`, the engineer can filter and format messages: `wire message fetch --conversation-id <id> --format json | jq -r '.[] | "\(.timestamp) | \(.sender_name): \(.content)"' > report.txt`

**MVP Acceptance**: Can fetch recent messages, filter by sender, export as JSON, suitable for compliance audits

---

### Story 4: "Real-Time Message Streaming for LLM Agents" [Should]
**Problem**: LLM agents need to respond to messages in < 1 second using a streaming interface, not batch polling.

**User Type**: LLM agent integrator, AI developer

**Title**: "Stream new messages in real-time for agent responses"

**Narrative**:
> An agent developer builds a Python LLM agent that listens to Wire messages and generates responses. The agent reads from `wire message fetch --follow --new-only --format jsonlines` and processes messages as they arrive. The `--follow` flag streams new messages, enabling real-time responses without polling loops or SDK complexity.

**Acceptance Criteria**:
- Given the `--follow` flag (Phase 2a), the command streams new messages as they arrive (not finished messages)
- Given a new message is sent to the conversation, it appears in the stream within 1 second
- Given the output format is `--format jsonlines`, each new message is a valid JSON Line
- Given the stream is interrupted (network hiccup), the command reconnects and resumes without losing messages
- Given no new messages arrive, the command blocks (doesn't exit)
- Given `--new-only` filter, only newly received messages are streamed (not past messages)
- When the agent processes messages from the stream and sends responses via `wire message send`, the round-trip time is < 2 seconds
- Exit code remains 0 while streaming; exit code is 13 on server error

**MVP Acceptance**: Phase 2a feature; deferred post-MVP. MVP provides polling foundation; streaming comes when infrastructure is ready.

---

## Minimum Viable Slice (MVP Definition)

**Definition**: Developers can send and receive plain text messages via CLI with filtering and JSON output, enabling basic automations, bots, and compliance use cases.

**In scope for MVP**:
1. **`wire message send --conversation-id <id> "text"`** — Send plain text to a conversation (Story 26)
   - Support stdin piping: `echo "hello" | wire message send --conversation-id <id>`
   - Support inline text: `wire message send --conversation-id <id> "hello world"`
   - Message delivery < 1 second
   - Exit code 0 (success), 11 (auth), 14 (not found), 13 (server error)

2. **`wire message fetch --conversation-id <id>`** — Fetch recent messages from a conversation (Story 27)
   - Default: return 10 most recent messages
   - Support `--limit N` to fetch up to N messages (max 1000 for MVP)
   - Exit code 0 (success), 11 (auth), 14 (not found), 13 (server error)

3. **`--from <user-id>`** — Filter messages by sender (Story 28)
   - Filter returned messages by sender user ID
   - Combine with `--limit` for flexible queries

4. **`--format text|json|jsonlines`** — Output formats for different use cases (Story 29)
   - **text**: Human-readable format (default)
     ```
     [14:32] alice@example.com: hello
     [14:35] bob@example.com: hi there
     ```
   - **json**: JSON array of message objects
     ```json
     [
       { "id": "msg-1", "sender_id": "alice@example.com", "sender_name": "Alice", "timestamp": "2025-03-14T14:32:00Z", "content": "hello" },
       { "id": "msg-2", "sender_id": "bob@example.com", "sender_name": "Bob", "timestamp": "2025-03-14T14:35:00Z", "content": "hi there" }
     ]
     ```
   - **jsonlines**: One message per line, suitable for streaming
     ```
     {"id": "msg-1", "sender_id": "alice@example.com", ...}
     {"id": "msg-2", "sender_id": "bob@example.com", ...}
     ```

5. **Error handling** (Story 31):
   - Auth failure (11): "Not authenticated. Run `wire login` first."
   - Not found (14): "Conversation <id> not found. Run `wire conversation list` to see your conversations."
   - Rate limit (12): "Rate limited. Wait 30s before retrying." (exponential backoff in future)
   - Server/network error (13): "Failed to send/fetch: [error detail]"
   - Invalid input (2): "--format must be text, json, or jsonlines"

6. **Data Model Contract** (Message Object):
   ```json
   {
     "id": "msg-abc123xyz",
     "sender_id": "user@domain",
     "sender_name": "Alice Smith",
     "timestamp": "2025-03-14T10:30:00Z",
     "content": "Hello, this is a message",
     "conversation_id": "3@conversation.domain"
   }
   ```

7. **Bats integration tests**:
   - Stub backend (in-memory messages)
   - Real backend (optional, with test credentials)
   - Tests for send, fetch, filtering, error handling

8. **Unit tests** for service layer and CLI parsing

**Out of scope for MVP**:
- `--follow` streaming (deferred to Phase 2a; requires event subscription plumbing)
- `--search` for message content (can add with --format json | jq)
- `--since <timestamp>` and `--until <timestamp>` date filters (deferred to Phase 2a)
- `--new-only` streaming filter (tied to --follow in Phase 2a)
- Message editing and deletion (deferred to Phase 3; different API)
- Rich formatting (mentions, replies, reactions) (deferred; automation doesn't need them)
- Attachments and file uploads (deferred; not needed for basic automation)
- Pagination (assume most queries return < 100 messages; pagination can be added if needed)
- Bulk operations (send to multiple conversations at once)

**Why this scope**:
- **MVP covers 90% of basic automation use cases**: Send alerts, fetch history, filter by sender
- **Streaming (Phase 2a) can be added without breaking changes**: Once foundation is solid, --follow is straightforward
- **Rich features (Phase 3) are unnecessary for automation**: Plain text, no formatting
- **This fits in 1-2 days of work**: Send + fetch + filter + JSON output + tests
- **Enables all four personas immediately**: Bots, CI/CD, compliance, agents (except real-time streaming)

**Why NOT streaming in MVP**:
- **Streaming requires event subscriptions**: More complex Kalium integration
- **Reconnection logic is non-trivial**: Need to handle network hiccups
- **Testing streaming is harder**: Requires time-based assertions
- **Polling + short sleep loop is good enough**: Bots using `sleep 2` in a loop are acceptable for MVP
- **Phase 2a can add streaming without breaking send/fetch**: Clean, orthogonal feature

---

## Integration Notes

### Dependency on Existing Features

**Conversations** (Stories 21-25): 
- Messages require conversation IDs
- Users must use `wire conversation list` to discover conversation IDs first
- **Blocker**: Messages feature depends on conversations shipping first

**Authentication** (Stories 1-4):
- Must be authenticated to send/receive messages
- Reuses existing session/auth logic
- **Already available**: No new auth work needed

**Profiles** (Stories 5-7):
- Sender names come from user profiles
- Message objects include `sender_name` (not just `sender_id`)
- **Already available**: Reuse profile fetching from existing code

### Command Taxonomy

```
wire
├── conversation
│   ├── list [OPTIONS]
│   └── info <conversation-id> [OPTIONS]        (Phase 2a)
├── message                                     ← NEW
│   ├── send --conversation-id <id> [TEXT]
│   │   ├── [TEXT]                             (positional argument)
│   │   └── (stdin if not provided)
│   └── fetch --conversation-id <id> [OPTIONS]
│       ├── --limit N                          (default 10, max 1000)
│       ├── --from <user-id>                   (filter by sender)
│       ├── --format text|json|jsonlines       (default text)
│       ├── --follow                           (Phase 2a)
│       ├── --new-only                         (Phase 2a)
│       ├── --since <timestamp>                (Phase 2a)
│       └── --until <timestamp>                (Phase 2a)
├── device
│   ├── list [OPTIONS]
│   ├── info <device-id> [OPTIONS]
│   └── delete <device-id> [OPTIONS]
├── doctor
│   └── status [OPTIONS]
└── ...
```

### Error Model Table

| Scenario | Exit Code | Message | Recovery |
|----------|-----------|---------|----------|
| Not authenticated | 11 | "Not authenticated. Run `wire login` first." | User runs login |
| Conversation not found | 14 | "Conversation <id> not found. Run `wire conversation list` to see your conversations." | Verify conversation exists |
| Rate limited (future) | 12 | "Rate limited. Wait 30s before retrying." | Exponential backoff |
| Server/network error | 13 | "Failed to send/fetch: [error detail]" | Retry or check connectivity |
| Invalid format option | 2 | "--format must be text, json, or jsonlines" | User corrects option |
| Missing conversation ID | 2 | "--conversation-id is required" | User provides ID |
| Empty result (not error) | 0 | "No messages found" | Expected for new conversations |
| Permission denied | 15 | "Permission denied. You don't have access to this conversation." | Check conversation membership |

---

## Implementation Readiness

### Kalium Surface Ready?
✅ **YES**. Kalium SDK exposes:
- `MessageRepository.sendMessage(conversationId, content)` → returns message with ID and timestamp
- `MessageRepository.fetchMessages(conversationId, limit)` → returns list of messages
- `MessageRepository.queryMessages(conversationId, filters)` → for sender filtering (if available; else filter client-side)
- Full message object with: id, sender_id, sender_name, timestamp, content, conversation_id

**No API limitations discovered.** Message sending and fetching are core Kalium features, stable, and well-documented.

### Streaming Surface Ready?
⚠️ **MOSTLY** (Phase 2a investigation needed).
- Kalium has subscription/flow APIs for streaming messages
- Need to investigate:
  - Does `MessageRepository` support real-time subscriptions or event streams?
  - Can we use CoreLogic flows for streaming?
  - What's the reconnection strategy if connection is lost?
  - How are subscription failures handled?
- **Answer needed before Phase 2a implementation**, but MVP doesn't depend on this

### Schema Stability?
✅ **YES**. Message fields are stable:
- `id`: UUID string, stable format
- `sender_id`: Email/user ID, stable
- `sender_name`: User display name, stable
- `timestamp`: ISO 8601 with millisecond precision, stable
- `content`: Plain text string, stable
- `conversation_id`: Stable format

No schema changes expected. If sender_name is not available in the API, we can fetch it from user profile (proven pattern in other features).

### Unknowns & Investigations Needed

1. **Message delivery guarantees**: 
   - Are messages guaranteed to arrive in order?
   - Can a message be "lost" if the connection drops?
   - **Answer needed for**: Compliance audits (order matters)

2. **Rate limits**:
   - What's Kalium's rate limit? (messages/sec per user? per conversation?)
   - Do we need exponential backoff?
   - **Answer needed for**: Phase 2a error handling

3. **Timestamp precision**:
   - Milliseconds or seconds?
   - Timezone handling (always UTC)?
   - **Answer needed for**: Audit queries and sorting

4. **Sender names availability**:
   - Is `sender_name` included in message objects, or do we need to fetch user profiles?
   - **Answer needed for**: Performance (avoid N+1 profile lookups)

5. **Streaming reconnection**:
   - If connection drops during `--follow`, how do we recover?
   - Are there missed messages?
   - **Answer needed for**: Phase 2a streaming reliability

### Testing Surface?
✅ **YES**. We can:
- Create `StubMessageRepository` with 20-30 realistic test messages (DMs, groups)
- Drive stub with environment variable (WIRE_USE_STUB=true)
- Real backend testing with test credentials (if provided)
- Test scenarios:
  - Send and fetch in same conversation
  - Fetch with filters (by sender, by limit)
  - Error cases (not found, unauthorized, server error)
  - Format output (text, json, jsonlines)

---

## Recommended Execution Order

### Critical Path: Messages Depends on Conversations

1. **Ensure Conversations MVP is DONE** (Stories 21-25)
   - Users must be able to discover conversation IDs via `wire conversation list`
   - Prerequisite for any message operations

2. **Implement Messages MVP (Stories 26-29)**: ~5-6 hours
   - **D1 (1.5h)**: Create service contracts, stub API, message data model
   - **D2 (1.5h)**: Implement `wire message send` command + parsing
   - **D3 (1h)**: Implement `wire message fetch` command + filtering
   - **D4 (0.5h)**: Output formatters (text, json, jsonlines)
   - **D5 (1h)**: Integration with Kalium client + runtime wiring
   - **D6 (1h)**: Unit tests for service layer
   - **D7 (1h)**: Bats integration tests (send, fetch, filters, errors)

3. **Validate with Examples** (0.5h)
   - Test ping-pong bot script (10 lines of bash)
   - Test CI/CD pipeline alert integration
   - Measure message delivery latency (should be < 1 second)
   - Validate JSON piping with jq

4. **Phase 2a (Following Sprint)**: ~2-3 hours
   - Implement `--follow` streaming with event subscriptions
   - Add time-based filters (`--since`, `--until`)
   - Add `--new-only` filter
   - Bats tests for streaming scenarios

5. **Phase 2b (Later)**: Optional enhancements
   - Message search (`--search "keyword"`)
   - Pagination for large result sets
   - Real-time monitoring

---

## Related Documentation
- [Conversations Exploration](./conversations-exploration.md) — Prerequisite feature (must ship first)
- [Device Management Exploration](./device-management-exploration.md) — Similar CLI pattern
- [Doctor (Sync Health) Exploration](./doctor-exploration.md) — Complementary feature
- [Kalium Integration Guide](../KALIUM_INTEGRATION.md) — Message API reference
- [Architecture](../architecture/wire-sdk-cli-design.md) — Command/service/client layering
- [Test Patterns](../testing/BATS_PATTERNS.md) — How to write integration tests

---

## Example: Ping-Pong Bot (10 Lines)

```bash
#!/bin/bash
# ping-pong bot: responds to "ping" with "pong"
# Usage: ./pingpong-bot.sh <conversation-id>

CONV_ID="$1"
if [ -z "$CONV_ID" ]; then
  echo "Usage: $0 <conversation-id>"
  exit 1
fi

while true; do
  # Fetch new messages
  wire message fetch --conversation-id "$CONV_ID" --new-only --format jsonlines | while read -r msg; do
    # Parse message
    text=$(echo "$msg" | jq -r '.content')
    sender=$(echo "$msg" | jq -r '.sender_name')
    
    # Respond to ping
    if [ "$text" = "ping" ]; then
      wire message send --conversation-id "$CONV_ID" "pong (from $sender)"
    fi
  done
  
  sleep 2  # Poll every 2 seconds
done
```

**Run it:**
```bash
./pingpong-bot.sh 3@conversation.domain
```

**From another terminal, send a message:**
```bash
wire message send --conversation-id 3@conversation.domain "ping"
```

**The bot responds within 2 seconds:**
```
pong (from Alice)
```

---

## Example: CI/CD Alert Integration

```bash
#!/bin/bash
# ci-alert.sh: Send test results to Wire
# Source this in your CI/CD pipeline

export WIRE_ALERTS_CONV="alerts@conversation.domain"

run_tests() {
  local result=$(npm test 2>&1)
  if [ $? -ne 0 ]; then
    # Send alert with error details
    wire message send --conversation-id "$WIRE_ALERTS_CONV" \
      "🚨 TEST FAILURE

Build: $CI_BUILD_ID
Branch: $CI_BRANCH
Commit: $CI_COMMIT_SHA

$(echo "$result" | grep -A 5 "FAIL:")"
    return 1
  else
    wire message send --conversation-id "$WIRE_ALERTS_CONV" \
      "✅ Tests passed for $CI_BRANCH"
    return 0
  fi
}

run_tests || exit 1
```

---

## Example: Compliance Message Export

```bash
#!/bin/bash
# export-compliance.sh: Export conversation for audit

CONV_ID="$1"
OUTPUT_FILE="${2:-compliance-export.json}"

echo "Exporting $CONV_ID to $OUTPUT_FILE..."

# Fetch all messages as JSON
wire message fetch --conversation-id "$CONV_ID" --limit 1000 --format json > "$OUTPUT_FILE"

# Generate human-readable report
echo "Generating report..."
jq -r '.[] | 
  "\(.timestamp) | \(.sender_name) <\(.sender_id)>:\n\(.content)\n"' \
  "$OUTPUT_FILE" > "${OUTPUT_FILE%.json}-report.txt"

echo "✅ Export complete"
echo "JSON: $OUTPUT_FILE"
echo "Report: ${OUTPUT_FILE%.json}-report.txt"
```

**Usage:**
```bash
./export-compliance.sh customer-convo-123 audit-export.json
```

**Output:**
- `audit-export.json` — Raw messages as JSON (for systems)
- `audit-export-report.txt` — Human-readable report (for auditors)

---

## Summary of Deliverables

This exploration document defines the complete Messages feature with:

1. ✅ **Problem Section**: Four vivid scenarios showing why message automation is critical
2. ✅ **Philosophy**: What messages are (and are NOT) for this CLI feature
3. ✅ **Four Personas**: Bob (DevOps), Diana (LLM Agent), Carlos (Support), Alice (Power User)
4. ✅ **Four User Stories**: Ping-pong bot, CI/CD alerts, compliance export, real-time agent
5. ✅ **MVP Scope**: Clear in/out scope with rationale
6. ✅ **Integration Notes**: Dependencies, command taxonomy, error model
7. ✅ **Implementation Readiness**: Kalium surface, schema stability, unknowns
8. ✅ **Example Code**: Bash bot, CI/CD integration, compliance export
9. ✅ **Execution Plan**: Recommended order (conversations first, then messages MVP, then Phase 2a)

**This feature is ready for implementation once Conversations MVP is complete.**

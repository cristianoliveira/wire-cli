# Conversations Exploration

## Problem: Why Users Care About Conversations

Imagine you're an LLM agent integrator building automation on top of Wire. You want to send a message to a conversation, but the Wire SDK requires a conversation ID. So you hardcode it: `conversationId = "abc123xyz"`. What happens when that team resets their workspace? Your bot breaks. What if you need to discover conversations dynamically? There's no CLI for that—you're stuck reading the agent's state file or making raw API calls.

**The LLM Agent Discovery Gap**: AI agents need visibility into conversations so they can route messages intelligently ("Send to #alerts"), handle multi-conversation workflows, and adapt to dynamic team structures. Without CLI discovery, agents are blind to the conversation topology and hardcoded to specific setups.

**The Automation Validation Problem**: You're automating Wire in your DevOps pipeline. You want to verify that a conversation exists before sending a notification. But you have no way to query "Does conversation #alerts exist?" from the command line. You're forced to write custom scripts or parse raw backend responses.

**The Support Engineer Audit Gap**: A customer reports: "I sent a message to a group conversation an hour ago, but nobody saw it." You log into their Wire account and need to verify: "What conversations does this account have? Is the group conversation active? Who are the participants?" Without a CLI, you're navigating the UI and hoping you don't miss details. A `wire conversation list` command would let you audit in seconds.

**The Power User Overwhelm**: You're a team lead with 50+ active conversations (DMs, groups, channels). You want to find recent conversations, filter out archived ones, and see who's most active in which spaces. The Wire UI shows everything, but there's no way to search, sort, or organize from the CLI. You're drowning in context.

---

## Philosophy: What Conversations Are and What This Feature Is NOT

### The Core Idea

A **conversation** in Wire is a space where people exchange messages. It can be:
- **Direct Message (1:1)**: Private conversation between two people
- **Group Conversation**: A subset of your team (3+ people) grouped by topic or project
- **Channel**: A broadcast-like space for announcements or public discussions

**Conversations ARE**:
- Static metadata containers (ID, name, type, creation date, last activity)
- Observable (list, filter, search, inspect)
- Participants can join/leave (but join/leave operations are out of scope here)

**Conversations are NOT**:
- Transient message streams (we're not streaming messages or event history)
- Modifiable (no rename, archive, or deletion from this feature)
- Creatable (no `wire conversation create` in MVP)
- Message containers we inspect (no reading past messages, no message counts)

The distinction is crucial: **this feature is about conversation discovery and inspection, not message management or conversation lifecycle operations.**

### Three Core Use Cases

#### 1. **LLM Agent Integration: "What conversations should I route to?"**
**Scenario**: You build a chatbot that listens to Wire and routes messages to different handlers. The bot needs to know: "What conversations exist? Which are DMs? Which are group channels?" Currently, this information is hardcoded or requires raw API calls.

**How `wire conversation list` helps**: Run `wire conversation list --json` in your startup script. Get back:
```json
[
  { "id": "conv-001", "type": "dm", "name": "alice@example.com", "last_activity_ms": 1234567890 },
  { "id": "conv-002", "type": "group", "name": "engineering-team", "participant_count": 12, "last_activity_ms": 1234567800 }
]
```
Your bot can now discover conversations dynamically, route based on type, and handle conversations without hardcoding IDs.

**Real-world benefit**: Remove hardcoded conversation IDs; enable multi-conversation workflows; adapt to team structure changes automatically.

---

#### 2. **Automation Validation: "Does this conversation exist?"**
**Scenario**: Your deployment pipeline sends notifications to Wire. Before sending, you want to verify the conversation exists to fail fast and clearly.

**How `wire conversation list --filter-type=group` helps**: Run the command to validate conversations exist and are of the expected type. Check exit code 0 (success) or 14 (not found). If a group conversation goes missing, your pipeline knows immediately instead of failing silently.

**Real-world benefit**: Reduce notification delivery failures; validate conversation health during deployments; enable auditable notification pipelines.

---

#### 3. **Support Engineer Audit: "What's the conversation topology for this account?"**
**Scenario**: A support ticket: "I sent a message to the finance-group an hour ago, and nobody got it." You need to verify: "Does that group exist? Who are the participants? When was the last activity?" Without a CLI, you're navigating the UI and hoping for accuracy.

**How `wire conversation list --verbose` helps**: Get a full audit report of all conversations, their participants, types, and last activity. Combined with other CLI tools (device management, doctor), you can diagnose the whole account state quickly.

**Real-world benefit**: Reduce customer support investigation time from 30 minutes to 2 minutes; audit account topology with confidence.

---

## Personas & Why They Need Conversation Discovery

### Persona 1: Alice — LLM Agent Integrator
**Profile**: Alice builds AI agents that run on Wire. Her agents need to discover conversations, route messages, and handle multi-conversation workflows. She uses Python/Node.js to script agent logic and wants a clean CLI interface for team members who don't code.

**Pain Points**:
- Hardcoded conversation IDs break when the team resets their workspace
- Agents can't dynamically discover which conversations exist
- No way to filter conversations by type (DMs vs. groups) from the CLI
- Building custom REST clients just to fetch the conversation list feels like overkill

**Why Conversations Matter**: Alice needs `wire conversation list --json` to discover conversations and `wire conversation list --filter-type=group` to focus on group conversations. This unblocks dynamic agent routing, multi-conversation workflows, and team adaptation.

---

### Persona 2: Bob — Automation Engineer / DevOps
**Profile**: Bob maintains Wire-based automation: notification pipelines, alerting systems, log aggregators. He needs to validate that conversations exist before his pipelines send messages. He thinks in JSON and exit codes.

**Pain Points**:
- Can't programmatically verify a conversation exists before sending a notification
- Notification delivery fails silently; no early validation
- No way to list conversations and parse the output in bash/Python scripts
- Support tickets: "The bot didn't send a message" — but was it because the conversation doesn't exist, or something else?

**Why Conversations Matter**: Bob needs `wire conversation list --json-lines` for parseable output, `wire conversation list --filter-type=group --json` to validate group conversations exist, and proper exit codes (0 = success, 14 = not found) to integrate with deployment pipelines.

---

### Persona 3: Carlos — Wire Support Engineer
**Profile**: Carlos handles customer support tickets for Wire. When a customer reports message delivery issues, he needs to audit their account: "What conversations do they have? Who's in each one? When was the last activity?" He works from a terminal and needs fast, clear output.

**Pain Points**:
- Can't quickly list all conversations for a customer's account from the CLI
- Auditing group membership and activity requires navigating the UI
- No way to verify a conversation exists without opening the app
- Support tickets are slow to investigate because there's no "conversation audit" command

**Why Conversations Matter**: Carlos needs `wire conversation list` with rich metadata (participants, last activity, type) and `wire conversation list --sort-by=activity` to find recently active conversations. This enables quick diagnosis of customer issues and audit trails for compliance.

---

### Persona 4: Diana — Wire Power User / Team Lead
**Profile**: Diana leads a distributed team of 20 people across 50+ conversations. She uses Wire for team coordination, project channels, and direct team communication. She wants to manage her conversation context without drowning in the UI.

**Pain Points**:
- 50+ conversations; can't easily find the ones she cares about
- Wants to see recent conversations first, but the UI doesn't sort by activity
- No way to focus on group conversations only (ignoring direct messages)
- Wants to know: "What conversations have I not checked today?" or "Which channels are most active?"

**Why Conversations Matter**: Diana needs `wire conversation list --sort-by=last_activity` to see recent conversations first, `wire conversation list --filter-type=group` to focus on team channels, and `wire conversation list` with search/filter to reduce cognitive load.

---

## Realistic User Stories

### Story 1: List All Conversations with Metadata (Must Have)
**Problem**: Users have no visibility into the conversations available on their account and cannot discover conversation IDs dynamically.

**User Type**: LLM agent integrator, automation engineer, support engineer

**Title**: "Discover all conversations on my account with rich metadata"

**Story**:
> As an LLM agent integrator, I want to list all conversations on my account (including conversation ID, type, participant count, and last activity) so I can discover conversations dynamically and route messages without hardcoding IDs.

**Acceptance Criteria**:
- Given I am authenticated, when I run `wire conversation list`, then I see a table/list of all conversations with columns: conversation ID, type (DM/group/channel), name/participants, participant count, and last activity timestamp.
- Given a conversation has no activity yet, when I view it, then last activity shows a placeholder (e.g., "never") and the command succeeds.
- Given there are no conversations, when I run the command, then I see a message "No conversations found" and exit code 0.
- When `--json` flag is used, output is valid JSON with consistent schema: `[{ id, type, name, participant_count, last_activity_ms, created_at_ms }]`.
- When `--json-lines` flag is used, output is newline-delimited JSON (one conversation per line), parseable by `jq` and shell scripts.
- Given I am not authenticated, when I run the command, then access is denied with "Session expired. Run `wire login` to re-authenticate." and exit code 11.
- Given the server returns an error, then I see the error with exit code 13 and can retry.
- Response time should be <2 seconds for typical accounts (up to 100 conversations).

**Notes**:
- MVP scope: list all conversations with basic metadata; no pagination required.
- Output formats: table (for terminals), JSON (for scripts), JSON Lines (for log processing).

---

### Story 2: Filter Conversations by Type (Must Have)
**Problem**: Users cannot distinguish between direct messages, group conversations, and channels; they need to filter by type to focus on relevant conversations.

**User Type**: LLM agent integrator, automation engineer, power user

**Title**: "Filter conversations by type (DM, group, channel)"

**Story**:
> As a power user with many conversations, I want to filter conversations by type so I can focus on group channels and ignore direct messages, or vice versa.

**Acceptance Criteria**:
- Given I run `wire conversation list --filter-type=dm`, then I see only direct message conversations.
- Given I run `wire conversation list --filter-type=group`, then I see only group conversations.
- Given I run `wire conversation list --filter-type=channel`, then I see only channel conversations.
- Given I run `wire conversation list --filter-type=group,channel` (comma-separated), then I see both group and channel conversations, but not DMs.
- Given an invalid type is specified (e.g., `--filter-type=invalid`), then I see an error "Invalid type. Valid types are: dm, group, channel" and exit code 2.
- When `--json` flag is combined with `--filter-type`, the JSON output includes only filtered conversations.
- Given there are no conversations matching the filter, then I see "No conversations matching filter" and exit code 0.
- Given I am not authenticated, then exit code 11.

**Notes**:
- MVP scope: support the three core types (DM, group, channel); filtering is client-side (fast).
- Future: status filter (active, archived) deferred to phase 2a.

---

### Story 3: Sort and Search Conversations (Should Have)
**Problem**: Users cannot find conversations by relevance (recent activity, name, participant count); they want to sort and search.

**User Type**: Power user, support engineer

**Title**: "Sort and search conversations by name, activity, or participant count"

**Story**:
> As a support engineer, I want to search conversations by name so I can quickly find "alerts-group" or "finance-team" without scrolling through the entire list.

**Acceptance Criteria**:
- Given I run `wire conversation list --sort-by=last_activity`, then conversations are sorted by last activity, most recent first.
- Given I run `wire conversation list --sort-by=name`, then conversations are sorted alphabetically by name.
- Given I run `wire conversation list --sort-by=participant_count`, then conversations are sorted by participant count, highest first.
- Given I run `wire conversation list --search=alerts`, then only conversations with "alerts" in the name are returned.
- Given I run `wire conversation list --search=bob@example.com`, then conversations with "bob" in the name or participants are returned.
- Given no conversations match the search, then I see "No conversations matching search" and exit code 0.
- When `--json` flag is used with `--search` and `--sort-by`, the output is filtered and sorted.
- Given I am not authenticated, then exit code 11.

**Notes**:
- MVP scope: basic sorting (last_activity, name, participant_count); search is deferred to phase 2a as a [Should].
- Sorting is client-side (fast for typical account sizes).

---

### Story 4: Filter by Participation Status (Should Have)
**Problem**: Users want to focus on conversations where they are active participants or filter out conversations they've left.

**User Type**: Power user, team lead

**Title**: "Filter conversations by participation status (active, left, archived)"

**Story**:
> As a team lead, I want to filter conversations by status so I can focus on conversations I'm actively part of and ignore archived or left conversations.

**Acceptance Criteria**:
- Given I run `wire conversation list --filter-status=active`, then I see only conversations I'm actively participating in.
- Given I run `wire conversation list --filter-status=left`, then I see only conversations I've left.
- Given I run `wire conversation list --filter-status=archived`, then I see only archived conversations.
- Given an invalid status is specified, then I see an error with exit code 2.
- When `--filter-status` is combined with `--filter-type`, both filters apply.
- When `--json` flag is used, each conversation includes a `status` field indicating participation state.
- Given I am not authenticated, then exit code 11.

**Notes**:
- MVP scope: DEFERRED to phase 2a [Should]; focus on basic listing and type filtering first.
- Requires additional backend metadata; can be added after MVP validation.

---

## Minimum Viable Slice (4–5 Hour MVP)

**Definition**: A working conversation discovery feature that enables users and agents to list, filter, and inspect conversations without hardcoding IDs.

**In scope for MVP**:
1. `wire conversation list` – Show all conversations with basic metadata (ID, type, name/participants, participant count, last activity)
2. `--filter-type=dm|group|channel` – Filter by conversation type
3. `--sort-by=last_activity|name|participant_count` – Sort conversations
4. `--json` and `--json-lines` output formats with stable schema
5. Exit codes: 0 (success), 11 (unauthorized), 13 (server error), 2 (usage error)
6. Rich metadata: conversation ID, type, name, participant count, last activity timestamp, created at timestamp
7. Error handling: missing conversations, auth failures, server errors
8. Response time <2 seconds for typical accounts (100 conversations)
9. Unit tests for service layer + Bats integration tests (stub backend)

**Out of scope for MVP**:
- `--search` flag (deferred to phase 2a)
- `--filter-status` flag (deferred to phase 2a)
- Pagination (assume <500 conversations per account for MVP)
- Message content or history retrieval
- Conversation creation, modification, or deletion
- Detailed participant info (avatar, status) — only names/IDs
- Conversation archive/unarchive
- Real-time subscription to conversation updates

**Why this scope**: List + type filtering cover 90% of real-world use cases (agent routing, automation validation, support audits). Search and status filtering can follow in phase 2a if feedback demands them. Message history and conversation management are separate features.

---

## Integration Notes

### Dependencies on Existing Features

**Required**:
- **Authentication** (`wire login`): Conversations feature requires an active session (exit code 11 if unauthorized).
- **Profile** (`wire profile`): May need user/account context to resolve participant names.

**Optional**:
- **Device Management** (`wire device list`): Complementary for support audits (device status + conversation topology).
- **Doctor** (`wire doctor status`): Complementary for checking sync health before listing conversations.

### Command Taxonomy

```
wire conversation list                              # List all conversations
wire conversation list --filter-type=group          # Filter by type
wire conversation list --sort-by=last_activity      # Sort by activity
wire conversation list --search=alerts              # Search (Phase 2a)
wire conversation list --filter-status=active       # Filter by status (Phase 2a)
wire conversation list --json                       # JSON output
wire conversation list --json-lines                 # JSON Lines output
```

### Error Model

| Scenario | Exit Code | Message | Recovery |
|----------|-----------|---------|----------|
| Unauthorized (not logged in) | 11 | "Session expired. Run `wire login` to re-authenticate." | Re-login |
| Server error (network, backend) | 13 | "Failed to fetch conversations: [underlying error]" | Retry or contact support |
| Invalid filter type | 2 | "Invalid type. Valid types are: dm, group, channel" | Use valid type |
| Invalid sort order | 2 | "Invalid sort. Valid options are: last_activity, name, participant_count" | Use valid sort |
| Degraded sync (can't list) | 1 | "Sync initializing; conversations may be incomplete. Run `wire doctor` to check status." | Wait or run doctor |

### Data Model Contract (Example JSON)

```json
{
  "conversations": [
    {
      "id": "conv-abc123xyz",
      "type": "dm",
      "name": "alice@example.com",
      "participant_count": 2,
      "participants": ["user-alice", "user-bob"],
      "created_at_ms": 1234567890000,
      "last_activity_ms": 1234567999000,
      "is_archived": false
    },
    {
      "id": "conv-group-001",
      "type": "group",
      "name": "engineering-team",
      "participant_count": 12,
      "participants": ["user-alice", "user-bob", ...],
      "created_at_ms": 1234567000000,
      "last_activity_ms": 1234567888000,
      "is_archived": false
    }
  ]
}
```

---

## Implementation Readiness

### Kalium Surface Ready?
✅ **Yes, with assessment**: 
- `ConversationScope` exposes:
  - `conversationList()` → returns list of conversations with metadata
  - `conversationInfo(conversationId)` → fetches detailed conversation info
  - Filtering and sorting happen client-side (all data in memory for MVP)

### Schema Stability?
✅ **Yes, with assessment**: 
- Conversation metadata is stable in both stub and real backends
- Fields: ID, type, name, participant count, last activity, created at
- Participant lists may be dynamic; use IDs rather than full profiles

### Unknowns?
1. **Conversation Status Field**: Does the backend expose conversation status (active, archived, left)? If so, at what level of detail?
2. **Participant Names**: Should we fetch participant details (names, avatars) or just IDs? Full details might slow down large conversation lists.
3. **Last Activity Timestamp**: Is this timestamp available from `ConversationScope`, or do we need to infer it from recent messages? (Assume it's available for MVP.)

### Testing Surface?
✅ **Yes**: Stub backend supports conversation list and metadata retrieval. Real backend supported for integration tests.

---

## Recommended Execution Order

1. **MVP (4–5 hours)**:
   - Implement `wire conversation list` with basic metadata
   - Add `--filter-type` support
   - Add `--sort-by` support
   - Implement `--json` and `--json-lines` output
   - Write unit tests + Bats integration tests
   - Verify exit codes and error handling

2. **Validation (1–2 hours)**:
   - Test with stub backend and real backend (if available)
   - Performance test: ensure <2s response time for 100+ conversations
   - Collect feedback from personas (LLM agent, automation, support)

3. **Phase 2a (2–3 hours)** — Deferred:
   - Add `--search` flag
   - Add `--filter-status` flag (if backend metadata available)
   - Optimize for large account sizes (500+ conversations)

---

## Related Documentation

- **Device Management Exploration**: `docs/plans/device-management-exploration.md` — Complementary feature for device audits.
- **Doctor Exploration**: `docs/plans/doctor-exploration.md` — Complementary feature for sync health checks.
- **KALIUM_INTEGRATION**: `docs/KALIUM_INTEGRATION.md` — SDK surface and method signatures.
- **Architecture**: `docs/Architecture.md` — Command → service → API-client wiring patterns.
- **Stories**: `docs/stories/conversations.md` — Structured user stories with acceptance criteria.

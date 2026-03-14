# Possible Features for `wire-cli` — Phase 2 Roadmap

**Context**: wire-cli MVP covers auth, profile, and presence (get/set). This roadmap outlines high-value features beyond core identity to support messaging, collaboration, and operations workflows.

**Current MVP baseline**: Login → Presence management → Session restoration. Next phase expands CLI into conversation, discovery, and observability domains.

**Version**: v0.0.1-beta — Breaking changes are allowed without deprecation periods. Direct renames and removals are acceptable during beta.

---

---

## Feature Candidates

### 1. **Conversation/Message Essentials** (`wire chat send|list|read`)
**Problem**: Users can't interact with conversations from CLI; no way to read/write messages programmatically.

- **Complexity**: Medium
- **Blockers**: Kalium's ConversationScope chat APIs are stable; schema for rich message types (mentions, reactions, edits) may differ between real/stub backends.
- **Dependencies**: Session-backed message service, message formatter/normalizer (analogous to presence normalizer).
- **Story headlines**:
  - "Send messages to a conversation from CLI"
  - "List recent messages in a conversation with optional filtering"

---

### 2. **Contacts/User Discovery** (`wire contacts find|list|connect`)
**Problem**: No way to search for users or manage contact relationships from CLI; manual profile IDs required.

- **Complexity**: Medium
- **Blockers**: User search ranking and domain federation may not be stable in stub backend; UI behavior for "send connection" varies by backend version.
- **Dependencies**: Domain-aware search service, contact state normalization (pending/accepted/blocked).
- **Story headlines**:
  - "Search for users by name/email with optional domain filter"
  - "Accept/block pending connection requests from CLI"

---

### 3. **Device Management** (`wire device list|show|delete`)
**Problem**: Users can't inspect or revoke devices without UI; no way to rotate devices or verify key-package health in automation.

**Note**: `wire client` command will be removed in favor of `wire device` (breaking change in beta, no deprecation period).

- **Complexity**: Small → Medium
- **Blockers**: Key-package refill status API may not be first-class in Kalium (currently internal); delete confirmation UX needs safe defaults.
- **Dependencies**: ClientScope extensions, key-package status normalizer, deletion safety guardrail (--yes flag).
- **Story headlines**:
  - "List all active devices with fingerprints and key-package status"
  - "Revoke a device with confirmation prompts"

---

### 4. **Conversation Lifecycle** (`wire convo create|list|archive|member`)
**Problem**: Group/team workflows blocked; can't create conversations or manage membership from CLI.

- **Complexity**: Large
- **Blockers**: ConversationScope.create() may require metadata schema negotiation (plain vs. MLS); member add/remove may have permission gates not visible in API signatures; guest links / public conversations unclear in Kalium surface.
- **Dependencies**: Conversation service layer, member state normalizer, explicit error mapping for permission failures, conversation type detection (1-1, group, team).
- **Story headlines**:
  - "Create a new conversation with initial members and optional name"
  - "Manage conversation membership (add/remove) with role assignment"

---

### 5. **Doctor: Sync Health & Observability** (`wire doctor status|diagnose|reset`)
**Problem**: No way to see sync/encryption readiness; users can't diagnose "why is my account not syncing?" without logs.

**Note**: `wire sync` command will be renamed to `wire doctor` (breaking change in beta, no deprecation period).

- **Complexity**: Small → Medium
- **Blockers**: Sync internals are partly delicate (not all public APIs exposed); event stream contract unclear; "watch" mode may require long-lived subscription (not ideal for one-shot CLI).
- **Dependencies**: SyncScope status API wrapper, health aggregator (auth OK? sync live? MLS ready?), streaming output handler.
- **Story headlines**:
  - "Check sync and MLS migration health with `wire doctor status`"
  - "Diagnose sync issues with `wire doctor diagnose`"
  - "Reset sync manually with `wire doctor reset`"

---

### 6. **Notifications & Event Subscription** (`wire events watch|stream`)
**Problem**: No way to subscribe to real-time updates (new messages, presence changes, typing); automation and alert workflows require polling.

- **Complexity**: Large
- **Blockers**: Event stream contracts still fluid in Kalium (cursor format, idempotency keys, reconnection semantics unclear); long-lived JVM process on CLI is risky. May require event-stream version negotiation with backend.
- **Dependencies**: EventScope subscription model, message-queue backend (to decouple fetch from render), stable cursor/idempotency schema, graceful shutdown on SIGINT.
- **Story headlines**:
  - "Stream incoming messages in real-time with optional filtering by conversation"
  - "Watch presence changes and typing notifications as they occur"

---

### 7. **Search & Message History** (`wire search messages|users|global`)
**Problem**: Can't query message history or do federated user search; no way to find old conversations.

- **Complexity**: Medium → Large
- **Blockers**: Global message search may have rank/sort modes not yet stable in Kalium; pagination semantics differ between message and user search; index lag on backends can hide recent messages.
- **Dependencies**: Unified search service (abstract message vs. user APIs), pagination normalizer (cursor vs. offset), sort-mode capability probe, full-text query validator.
- **Story headlines**:
  - "Search messages in a conversation or globally with date/author filters"
  - "Find users across domains with optional role/status filters"

---

### 8. **Team/Group & Moderation** (`wire team list|show`, `wire group moderate|restrict`)
**Problem**: No way to manage team membership, permissions, or moderate conversations; only available in UI.

- **Complexity**: Large
- **Blockers**: Team vs. conversation vs. group semantics unclear in Kalium; moderation actions (remove user, revoke permissions, restrict posting) vary by backend API version; audit trail not always exposed.
- **Dependencies**: Team service layer with role/permission normalizers, moderation action executor, member list with role visibility, timestamp-based activity log formatter.
- **Story headlines**:
  - "List teams and manage member roles (owner/member/guest)"
  - "Moderate conversations with mute/restrict/remove actions"

---

## Quick-Win Opportunities

### 🎯 **Doctor: Sync Health & Diagnostics** (Small, High Value)
- **Why small**: Status aggregation from existing SyncScope; no new backend integration required.
- **Why valuable**: Unblocks troubleshooting; "is my account ready?" is asked constantly in support.
- **Effort**: 2–3 days (service wrapper + output formatter).

### 🎯 **Device Management** (Small→Medium, High Value)
- **Why small**: Kalium ClientScope already exposes list/delete; only need CLI wiring.
- **Why valuable**: Security operations (revoke compromised device) and compliance (audit active devices).
- **Effort**: 1–2 days (with safe confirmation flow).

---

## Complexity Summary

| Feature | Complexity | Product Value | Effort | Risk |
|---------|-----------|---------------|--------|------|
| Chat send/read | Medium | High | 3–5d | Medium (message schema variance) |
| Contacts/search | Medium | High | 3–5d | Medium (domain federation) |
| Device management | Small→Med | High | 1–2d | Low (existing APIs) |
| Doctor | Small→Med | High | 2–3d | Low (aggregation only) |
| Conversation lifecycle | Large | High | 7–10d | High (permission gates, MLS) |
| Search/history | Medium→Large | Medium | 5–8d | Medium (pagination, rank) |
| Notifications/events | Large | Very High | 10–15d | High (stream stability, JVM risks) |
| Team/moderation | Large | Medium | 8–12d | High (version gates, audit trail) |

---

## Recommended Next Pick: **Device Management + Doctor**

**Why this pair**:
1. **Small but high-value**: Each is 1–3 days; unlocks security and diagnostics workflows immediately.
2. **Low implementation risk**: Both use existing Kalium APIs with minimal schema unknowns.
3. **Proves patterns**: Establishes "how we wire service → command" for future medium/large features.
4. **Unblocks feedback**: Real users can validate CLI model before investing in conversation/notifications (which are riskier).
5. **Breaking changes allowed**: Beta allows direct changes without deprecation (remove `wire client`, rename `wire sync` → `wire doctor`)

**Suggested sequencing**:
1. **Day 1**: Implement `wire device` management with safe confirmation flow (consolidate from any `wire client` legacy code).
   - Proves session-backed service pattern + error handling.
2. **Day 2**: Implement `wire doctor` aggregator (`status`, `diagnose`, `reset` subcommands).
   - Proves health/diagnostics output formatting.
3. **Day 3**: Add `--json` output to both; run Bats integration tests.
   - Proves output stability for automation.

**Validation gates**:
- All operations work in both stub and real backends.
- Help text and error messages are actionable.
- `--json` schema is versioned and stable (no unexpected key changes).
- Test coverage >80% for new service layers.

---

## Subsequent Priority (Post Device/Doctor)

1. **Chat send/read** (~5 days): Highest daily utility; unblocks automation.
2. **Contacts/search** (~5 days): Strong operator value; moderate complexity.
3. **Conversation lifecycle** (~10 days): Large but well-scoped; can follow chat.
4. **Search/history** (~8 days): Medium value until messaging is solid.
5. **Notifications/events** (~15 days): Defer until event-stream contract stabilizes in Kalium.
6. **Team/moderation** (~12 days): Lower priority; mostly admin workflows.

---

## Design Notes

- **Keep command → service → api-client → runtime pattern**: New features should not deviate from existing architecture.
- **Script-friendly first**: All new commands need `--json`, stable exit codes, no implicit prompts.
- **Schema versioning**: Output formats must be versioned (e.g., `--output json --format-version 2024-q1`).
- **Confirmation guards**: Destructive ops (delete client, remove member) require `--yes` or interactive prompt.
- **Error mapping**: Avoid string-matching on Kalium exceptions; add typed error contracts in each service layer.
- **Testing**: Stub backend + real backend matrix for all integration tests; no happy-path-only testing.

---

## Blockers & Unknowns to Validate

| Unknown | Impact | Mitigation |
|---------|--------|-----------|
| Message schema variance (rich types) | Medium | Prototype chat send in stub mode first; validate schema with Kalium maintainers. |
| Event-stream idempotency contract | High | Defer events/notifications until Kalium exposes stable cursor/retry semantics. |
| Team vs. group vs. conversation semantics | High | Spike on WireTUI code and Kalium source; document mapping before implementation. |
| MLS key-package refill API stability | Medium | Check if exposed as public ClientScope method; if internal, file Kalium RFC. |
| Moderation action permission gates | Medium | Validate permission model against backend (who can remove user from team vs. group?). |

---

## Related Documentation

- **Architecture patterns**: See `docs/Architecture.md` for command → service wiring and error-handling conventions.
- **Existing command taxonomy**: See `docs/ideas.md` for fuller feature taxonomy (sessions, config, backup, etc.).
- **Kalium integration**: See `docs/KALIUM_INTEGRATION.md` for SDK constraints and public API surface.
- **Test patterns**: See `test/` for Bats integration test patterns (stub backend, session lifecycle).

# Phase 2 Planning Documents

This directory contains discovery and planning documents for wire-cli Phase 2 features.

## Quick-Win Explorations (Recommended Next)

These are the two "small but interesting" features recommended as Phase 2 quick-wins:

### 1. Device Management Exploration
📄 **File**: `device-management-exploration.md` (1,948 words)

**Focus**: User needs around device lifecycle, security, and compliance.

**Personas**: 
- Alice (Power User/Developer) – needs to audit & revoke devices
- Bob (Team Lead/Admin) – needs to manage team device security

**User Stories**:
- List all active devices ✅ (MVP)
- Show device details ⏳ (Phase 2a)
- Revoke a device ✅ (MVP)
- Rename device ❌ (Phase 2b)

**MVP Scope**: `wire device list` + `wire device delete` (1 day)

---

### 2. Conversations Exploration
📄 **File**: `conversations-exploration.md`

**Focus**: Enable developers and LLM agents to discover and filter conversations.

---

### 3. Messages Exploration
📄 **File**: `messages-exploration.md`

**Focus**: Enable CLI-native bots and automations with message send/receive capabilities.

---

### 4. Sync Health Exploration
📄 **File**: `sync-health-exploration.md` (2,518 words)

**Focus**: User needs around sync readiness, diagnostics, and troubleshooting.

**Personas**:
- Carlos (DevOps/Support) – needs health checks and automation
- Diana (Power User) – wants to know "Am I synced?"

**User Stories**:
- Check overall readiness ✅ (MVP)
- Get detailed metrics ✅ (MVP)
- Diagnose sync issues ⏳ (Phase 2a)
- Trigger sync reset ❌ (Phase 2b)

**MVP Scope**: `wire sync status` + `--verbose` (1.5 days)

---

## Recommended Execution Order

### 🎯 Sequential (Device → Conversations → Messages → Sync) — Recommended

1. **Day 1**: Device Management MVP
   - `wire device list` + `wire device delete`
   - Establishes service pattern; unblocks security ops

2. **Day 2**: Conversations MVP
   - `wire conversation list` with filtering and metadata
   - Enables agent discovery; unblocks conversation-based workflows

3. **Day 3**: Messages MVP
   - `wire message send` + `wire message receive`
   - Enables CLI-native bots; unblocks automation workflows

4. **Days 4–5**: Sync Health MVP
   - `wire sync status` + `--verbose`
   - Builds on device patterns; unblocks diagnostics

**Why**: Device management is smaller and more independent. Conversations builds naturally after. Messages enables automation capabilities. By day 5, sync health can show device health intelligently.

### 🎯 Alternative: Parallel (If Pairing)

If two engineers are available, features can be done in parallel over 4–5 days.

### 🎯 Alternative: Conversations-First (If Agent Integration Priority)

If LLM agent integration is the immediate priority, start with conversations (days 1–2), messages (day 3), then device management (day 4), then sync health (days 5–6).

---

## Related Documentation

- **Roadmap Context**: `possible-features.md` (full Phase 2 feature taxonomy and complexity matrix)
- **Architecture**: `docs/Architecture.md` (command → service → Kalium wiring patterns)
- **SDK Constraints**: `docs/KALIUM_INTEGRATION.md` (Kalium API surface and limitations)
- **Existing Stories**: `docs/stories/` (reference implementations for profile, presence, auth)

---

## Using These Explorations

### For Team Alignment
1. Read both exploration documents to understand user pain points
2. Validate personas and use cases with actual users/support
3. Decide execution order (sequential, parallel, or sync-first)

### For Implementation
1. Use acceptance criteria as test cases
2. Reference "Minimum Viable Slice" section for scope boundaries
3. Check "Implementation Readiness" for Kalium unknowns that need spiking

### For Formal Story Creation
Convert exploration stories to `.beads/issues.jsonl` format:
- One `.beads` issue per user story
- Link with `depends-on` relationships (e.g., "Show device" depends on "List devices")
- Tag with `phase:2-quickwin` for tracking

---

## Implementation Checklist

### Pre-Implementation
- [ ] Team reviews both explorations
- [ ] Personas and pain points validated with users/support
- [ ] Execution order decided (sequential, parallel, or sync-first)
- [ ] Kalium unknowns resolved (spike if needed):
  - [ ] MLS migration % API
  - [ ] Event-stream cursor availability
  - [ ] Key-package refill API
  - [ ] Pending message count API

### During Implementation (Device Management)
- [ ] `ClientService` layer created
- [ ] `wire device list` command wired
- [ ] `wire device delete` command with confirmation/--yes
- [ ] Error handling and exit codes
- [ ] Unit + Bats integration tests (both backends)
- [ ] `--json` output with schema versioning

### During Implementation (Sync Health)
- [ ] `SyncHealthAggregator` service created
- [ ] `wire sync status` command wired
- [ ] `--verbose` flag with detailed metrics
- [ ] `--wait-for-ready` optional flag
- [ ] Recovery suggestions for degraded states
- [ ] Unit + Bats integration tests (both backends)
- [ ] Plain-text + JSON output formats

### Post-Implementation
- [ ] All integration tests passing (stub + real backends)
- [ ] Exit codes match documented error model
- [ ] `--json` schema stable (no unexpected changes)
- [ ] Help text is clear and actionable
- [ ] Edge cases handled (only device, auth expiring, etc.)

---

## Feedback & Questions

### For Team
1. Do the personas match your users?
2. Are the pain points accurate?
3. Which execution order works best for your team?

### For Support/Product
1. Which pain point is more urgent: device security or sync diagnostics?
2. Do you have ticket data supporting feature prioritization?
3. Are there compliance requirements we should address?

### For Kalium Maintainers
See "Implementation Readiness" sections in both exploration documents.

---

## Summary

Two exploration documents totaling ~4,500 words provide:
- ✅ Deep user research (personas, pain points, use cases)
- ✅ 8 realistic user stories (4 per feature, grounded in Wire/Slack-like scenarios)
- ✅ Clear MVP definitions (scope, acceptance criteria, effort estimates)
- ✅ Implementation readiness assessment (Kalium APIs, unknowns, testing surface)
- ✅ Recommended execution order (with alternatives)

**Next Step**: Team review → decide execution order → formalize to `.beads` issues → begin implementation.

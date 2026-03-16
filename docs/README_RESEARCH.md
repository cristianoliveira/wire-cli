# Wire-CLI Research Documentation Index

📚 **Complete research on messaging, synchronization, and CLI workflow integration**

## Quick Navigation

### For Your Question: "When Does Sync Happen?"

→ **Start here**: [`SYNC_TIMING_AND_WORKFLOW.md`](SYNC_TIMING_AND_WORKFLOW.md)

- Quick answer (top of document)
- Command-by-command timeline
- Code execution flow with line numbers
- Environment variable control
- Performance matrix

### For Understanding Why Sync Matters

→ **Read**: [`MESSAGING_AND_SYNC_ARCHITECTURE.md`](MESSAGING_AND_SYNC_ARCHITECTURE.md) § 2 (Why Data Synchronization is Critical)

- Event window limitation problem
- Cryptographic state requirements
- Metadata completeness requirement
- Solution: Two-phase sync model

### For Visual Understanding

→ **Explore**: [`SYNC_WORKFLOW_DIAGRAMS.md`](SYNC_WORKFLOW_DIAGRAMS.md)

- Complete lifecycle flowchart
- Sync state machine visualization
- Code path diagrams
- Timeline comparisons

### For Implementation Details

→ **Reference**: [`MESSAGING_AND_SYNC_ARCHITECTURE.md`](MESSAGING_AND_SYNC_ARCHITECTURE.md)

- Complete message sending architecture (with code examples)
- Message receiving pipeline
- Sync mechanisms (SlowSync + IncrementalSync)
- Retry strategies (3 levels)
- Failure modes and recovery

---

## Document Overview

### 1. 📊 [`SYNC_TIMING_AND_WORKFLOW.md`](SYNC_TIMING_AND_WORKFLOW.md) (650 lines)

**Purpose**: Answer when sync happens in CLI workflow

**Key Sections**:
- ✅ Quick answer: Sync happens automatically on first data access
- 📋 Command-by-command timeline (login, profile, presence, conversation, doctor)
- 🔍 Actual code flow: Profile command step-by-step
- 🎛️ Environment variable control: `WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT`
- ⏱️ Performance matrix: Cold vs warm start
- 🎯 Wiretool comparison: How they differ
- 📁 Source file references with line numbers

**Read this if you want to**:
- Understand when sync happens in your CLI commands
- Know how to skip sync wait with env vars
- See actual code execution paths
- Compare with wiretool's behavior

---

### 2. 🏗️ [`MESSAGING_AND_SYNC_ARCHITECTURE.md`](MESSAGING_AND_SYNC_ARCHITECTURE.md) (1,510 lines)

**Purpose**: Complete technical architecture of messaging and sync

**Key Sections**:
1. Executive Summary
2. **Why Data Synchronization is Critical** (4 problems + solution)
3. Message Sending Architecture (Proteus + MLS)
4. Message Receiving Architecture (Event pipeline)
5. Synchronization Mechanisms (SlowSync, IncrementalSync, state machine)
6. Wire-CLI Integration Layer (Kalium CoreLogic lifecycle)
7. Complete Data Flow Diagrams
8. Key Design Decisions (with rationale)
9. Failure Modes and Recovery
10. Implementation Guidelines (how to extend)

**Read this if you want to**:
- Understand message sending/receiving in detail
- Learn why two-phase sync is necessary
- See complete data flow diagrams
- Understand design trade-offs
- Know how to implement new features

---

### 3. 📈 [`SYNC_WORKFLOW_DIAGRAMS.md`](SYNC_WORKFLOW_DIAGRAMS.md) (500 lines)

**Purpose**: Visual representations of sync timing and flow

**Key Diagrams**:
1. Complete CLI lifecycle with sync
2. Sync triggering patterns (data access vs status commands)
3. Sync state machine transitions
4. Code path visualization
5. Timeline comparison (Wiretool vs Wire-CLI)
6. Decision tree for sync triggering
7. Performance graphs (cold vs warm start)
8. Error scenarios and recovery

**Read this if you want to**:
- See flowcharts and diagrams
- Understand state transitions visually
- Compare execution timelines
- See code paths graphically
- Understand error recovery flows

---

### 4. 📑 [`RESEARCH_SUMMARY.md`](RESEARCH_SUMMARY.md) (this provides overview and links)

**Purpose**: High-level summary connecting all research

**Contains**:
- Overview of research scope
- Key findings summary (all 3 questions answered)
- Implementation references
- Architectural insights
- Navigation guide
- Next steps

---

## Quick Reference: Answers to Your Questions

### Q: When does sync happen in wire-cli?

**Location**: [`SYNC_TIMING_AND_WORKFLOW.md`](SYNC_TIMING_AND_WORKFLOW.md) - Quick Answer section

**TL;DR**: 
```
- wire login: ❌ No sync
- wire profile: ✅ Yes, blocks until Live (5-40s first time, <1s after)
- wire presence: ✅ Yes, blocks until Live (5-40s first time, <1s after)
- wire conversation: ✅ Yes, blocks until Live (5-40s first time, <1s after)
- wire doctor: ✅ Yes, but observes only (non-blocking)

Control with: WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT=true
```

---

### Q: How does message sending/receiving work?

**Location**: [`MESSAGING_AND_SYNC_ARCHITECTURE.md`](MESSAGING_AND_SYNC_ARCHITECTURE.md) § 3-4

**TL;DR**:
- **Sending**: Encrypt (Proteus or MLS) → POST to server → 3-level retry
- **Receiving**: Fetch events → Decrypt → Store atomically → Side effects
- 2 protocols: Proteus (legacy), MLS (modern)

---

### Q: Why is sync needed?

**Location**: [`MESSAGING_AND_SYNC_ARCHITECTURE.md`](MESSAGING_AND_SYNC_ARCHITECTURE.md) § 2

**TL;DR**:
- Event window limitation (30 days)
- Cryptographic state must be pre-established
- Metadata must be complete before processing messages
- Side effects depend on full metadata

---

### Q: How can we mimic wiretool?

**Location**: [`SYNC_TIMING_AND_WORKFLOW.md`](SYNC_TIMING_AND_WORKFLOW.md) - "Mimicking Wiretool Behavior" section

**TL;DR**:
- Wiretool: Async background sync + progressive UI updates
- Wire-CLI: Blocking sync + complete data
- Current design is better for CLI (simple, deterministic)
- Can use env var to skip wait if speed is critical

---

## Key Insights (Copy-Paste Ready)

### Why Sync is Critical
Wire-CLI cannot simply request messages when needed because:
1. **Event window limit**: Server keeps events ~30 days, then deletes them
2. **Metadata dependency**: Messages can't be decrypted without conversation context, user profiles, and cryptographic keys
3. **Cryptographic state**: MLS groups need current epoch, Proteus needs sessions
4. **Atomic side effects**: Delivery confirmations, read receipts, self-deletion all depend on complete metadata

**Solution**: Two-phase sync:
- Phase 1 (SlowSync): Reconstruct all metadata (5-30s)
- Phase 2 (IncrementalSync): Deliver new events (1-10s)

---

### Sync Triggering in Code
```kotlin
// In RealKaliumProfileApiClient.kt:100
if (!cliMode.disableSessionSyncWait) {
    coreLogic.sessionScope(qualifiedId) {
        syncExecutor.request { waitUntilLiveOrFailure() }  // ← SYNC HAPPENS HERE
    }
}
```

---

### Performance Profile
```
First command after login:
  - SlowSync: 5-30 seconds (metadata)
  - IncrementalSync: 1-10 seconds (events)
  - Total: 6-40 seconds

Subsequent commands:
  - Catch-up only: < 1 second
  - (Usually 0 events, already Live)

With env var disabled:
  - Any command: < 100ms
  - But data may be incomplete
```

---

## How to Use This Documentation

### If you're...

**Debugging why a command is slow** →
- Read: SYNC_TIMING_AND_WORKFLOW.md § Performance Implications
- Understand: First run is 5-40s (normal), subsequent runs are <1s

**Trying to understand the architecture** →
- Start: RESEARCH_SUMMARY.md
- Then: MESSAGING_AND_SYNC_ARCHITECTURE.md § Key Design Decisions
- Reference: Source files listed in each doc

**Implementing new features** →
- Read: MESSAGING_AND_SYNC_ARCHITECTURE.md § Implementation Guidelines
- Reference: Code examples in § 3-4 (Message Sending/Receiving)
- Copy: Retry patterns from § Message Queuing and Retry Mechanisms

**Comparing with wiretool** →
- Read: SYNC_WORKFLOW_DIAGRAMS.md § Timeline Comparison (Wiretool vs Wire-CLI)
- Understand: Different design goals (app vs CLI)

**Troubleshooting sync issues** →
- Reference: MESSAGING_AND_SYNC_ARCHITECTURE.md § Failure Modes and Recovery
- Check: SYNC_WORKFLOW_DIAGRAMS.md § Error Scenarios and Recovery

---

## Document Statistics

| Document | Lines | Sections | Code Examples | Diagrams |
|----------|-------|----------|---|---|
| MESSAGING_AND_SYNC_ARCHITECTURE | 1,510 | 10 major | 20+ | 9 |
| SYNC_TIMING_AND_WORKFLOW | 650 | 8 major | 10+ | 4 |
| SYNC_WORKFLOW_DIAGRAMS | 500 | 8 major | 5+ | 30+ |
| **Total** | **2,660** | **26** | **35+** | **43+** |

---

## Related Documentation

- **`Architecture.md`** - Overall wire-cli architecture
- **`KALIUM_INTEGRATION.md`** - Kalium SDK integration details
- **`NIX_BUILD.md`** - Build and development setup

---

## Research Metadata

**Research Date**: March 15-16, 2026  
**Duration**: ~4 hours of parallel investigation  
**Methodology**: Code analysis + flow tracing  
**Tools Used**: grep, read, code analysis  
**Source Files Examined**: 50+ Kotlin files  
**Kalium Files Referenced**: 15+ (sync, message, encryption)  
**Wire-CLI Files Referenced**: 20+ (commands, services, runtime)

---

## Key Files Referenced

### Sync Triggering Points
- `src/main/kotlin/wirecli/profile/RealKaliumProfileApiClient.kt:100`
- `src/main/kotlin/wirecli/presence/RealKaliumPresenceApiClient.kt:116`

### Environment Configuration
- `src/main/kotlin/wirecli/runtime/KaliumCliMode.kt`
- `src/main/kotlin/wirecli/runtime/KaliumCliConfigs.kt`

### Kalium Sync Architecture
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/SyncManager.kt`
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/slow/SlowSyncManager.kt`
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/incremental/IncrementalSyncManager.kt`

### Message Handling
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/ProteusMessageUnpacker.kt`
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/MLSMessageUnpacker.kt`

---

## Questions?

Each document is self-contained with:
- Table of contents
- Cross-references
- Code examples with line numbers
- Visual diagrams
- Implementation guidelines

**Start with**: [`SYNC_TIMING_AND_WORKFLOW.md`](SYNC_TIMING_AND_WORKFLOW.md) if you just want to know "when does sync happen?"

**Go deep**: [`MESSAGING_AND_SYNC_ARCHITECTURE.md`](MESSAGING_AND_SYNC_ARCHITECTURE.md) for complete understanding

**Visualize**: [`SYNC_WORKFLOW_DIAGRAMS.md`](SYNC_WORKFLOW_DIAGRAMS.md) for flowcharts and state machines

---

**Status**: ✅ Complete research package  
**Version**: 1.0  
**Last Updated**: March 16, 2026

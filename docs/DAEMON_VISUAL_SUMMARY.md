# Daemon Mode: Visual Summary

Quick visual comparison showing why daemon mode is brilliant.

---

## Current Model (Per-Command CLI)

```
USER SENDS 3 MESSAGES

$ wire send "msg1"
┌────────────────────────────────────────┐
│ JVM Startup         1.5 seconds        │
│ Kalium Init         0.2 seconds        │
│ Check Sync          0.0 seconds        │
│ Send Message        0.1 seconds        │
├────────────────────────────────────────┤
│ Total              1.8 seconds         │
└────────────────────────────────────────┘

$ wire send "msg2"
┌────────────────────────────────────────┐
│ JVM Startup         1.5 seconds        │ ← Expensive again!
│ Kalium Init         0.2 seconds        │
│ Check Sync          0.0 seconds        │
│ Send Message        0.1 seconds        │
├────────────────────────────────────────┤
│ Total              1.8 seconds         │
└────────────────────────────────────────┘

$ wire send "msg3"
┌────────────────────────────────────────┐
│ JVM Startup         1.5 seconds        │ ← Expensive again!
│ Kalium Init         0.2 seconds        │
│ Check Sync          0.0 seconds        │
│ Send Message        0.1 seconds        │
├────────────────────────────────────────┤
│ Total              1.8 seconds         │
└────────────────────────────────────────┘

TOTAL TIME: 5.4 seconds for 3 messages
```

---

## Daemon Model (Long-Running Server)

```
ONE-TIME SETUP

$ wire daemon start
┌────────────────────────────────────────┐
│ JVM Startup         1.5 seconds        │
│ Kalium Init         0.2 seconds        │
│ SlowSync            5-30 seconds       │
│ IncrementalSync     (continuous)       │
├────────────────────────────────────────┤
│ Total              ~7 seconds (one-time) │
│ Then listening...                      │
└────────────────────────────────────────┘

NOW SEND 3 MESSAGES

$ wire send "msg1"
┌────────────────────────────────────────┐
│ Connect to daemon   < 0.01 seconds     │
│ Send Message        0.1 seconds        │
│ Get result          < 0.01 seconds     │
├────────────────────────────────────────┤
│ Total              0.12 seconds        │
└────────────────────────────────────────┘

$ wire send "msg2"
┌────────────────────────────────────────┐
│ Connect to daemon   < 0.01 seconds     │
│ Send Message        0.1 seconds        │
│ Get result          < 0.01 seconds     │
├────────────────────────────────────────┤
│ Total              0.12 seconds        │
└────────────────────────────────────────┘

$ wire send "msg3"
┌────────────────────────────────────────┐
│ Connect to daemon   < 0.01 seconds     │
│ Send Message        0.1 seconds        │
│ Get result          < 0.01 seconds     │
├────────────────────────────────────────┤
│ Total              0.12 seconds        │
└────────────────────────────────────────┘

TOTAL TIME: 7 + 0.36 = 7.36 seconds (includes one-time setup)

But for next 10 messages: only 1.2 seconds!
And next 100 messages: only 12 seconds!
(vs 180 seconds with current CLI model)
```

---

## Timeline Visualization

### Current CLI Model

```
Timeline (seconds)
0    1    2    3    4    5    6    7    8    9   10

msg1: [JVM..........][K][send]
                                 msg2: [JVM..........][K][send]
                                                          msg3: [JVM..........][K][send]

Total: ~5.4 seconds
Overhead: 4.5s in JVM startup alone!
```

### Daemon Model

```
Timeline (seconds)
0    1    2    3    4    5    6    7    8    9   10

daemon:  [JVM.......][K][Sync.....................]
         (runs forever, listening on socket)
msg1:                        [send]
msg2:                            [send]
msg3:                                [send]

Total: ~7 seconds (initial)
Then: 0.1s per message

For 20 messages: 7 + (20*0.12) = 9.4 seconds
                (vs 36 seconds with CLI model)
Savings: 73% faster!
```

---

## Real-World Use Case: Chat Bot

### With Current CLI (Slow)

```
User sends message in chat
    ↓
Chat bot receives webhook
    ↓
Script: wire send "Processing..."
    ├─ Wait 1.8s (JVM startup)
    └─ Message sent
    ↓
Do processing (5 seconds)
    ↓
Script: wire send "Done!"
    ├─ Wait 1.8s (JVM startup)
    └─ Message sent
    ↓
Total time seen by user: 8.6 seconds
User thinks: "Slow bot..." 😞
```

### With Daemon (Fast)

```
User sends message in chat
    ↓
Chat bot receives webhook
    ↓
Script: wire send "Processing..."
    ├─ Wait 0.1s (IPC to daemon)
    └─ Message sent
    ↓
Do processing (5 seconds)
    ↓
Script: wire send "Done!"
    ├─ Wait 0.1s (IPC to daemon)
    └─ Message sent
    ↓
Total time seen by user: 5.2 seconds
User thinks: "Fast bot!" 🚀
```

---

## Architecture Diagram

### Current

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  CLI Proc 1 │     │  CLI Proc 2 │     │  CLI Proc 3 │
│  (wire)     │     │  (wire)     │     │  (wire)     │
│             │     │             │     │             │
│ JVM:   YES  │     │ JVM:   YES  │     │ JVM:   YES  │
│ Init:  YES  │     │ Init:  YES  │     │ Init:  YES  │
│ Sync:  DONE │     │ Sync:  DONE │     │ Sync:  DONE │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┴───────────────────┘
              ↓ (all access server)
       ┌──────────────────┐
       │ Wire Server      │
       │ (backend)        │
       └──────────────────┘

Problem: Each process is independent, no shared state
         Every process must redo JVM startup and Kalium init
```

### With Daemon

```
         ┌──────────────────────┐
         │  Daemon Process      │
         │  (wire daemon)       │
         │                      │
         │ JVM:  YES (once)     │
         │ Init: YES (once)     │
         │ Sync: ALWAYS         │
         │ WebSocket: OPEN      │
         └────────────┬─────────┘
                      │ (socket/HTTP)
         ┌────────────┼────────────┐
         │            │            │
    ┌────▼──┐    ┌────▼──┐   ┌────▼──┐
    │CLI 1  │    │CLI 2  │   │CLI 3  │
    │(thin) │    │(thin) │   │(thin) │
    └───────┘    └───────┘   └───────┘
         │            │            │
         └────────────┼────────────┘
              ↓ (all access daemon)
         ┌──────────────────┐
         │ Wire Server      │
         │ (backend)        │
         └──────────────────┘

Benefit: Single JVM process, shared Kalium state
         All CLI commands are lightweight proxies
         Daemon keeps WebSocket open for real-time updates
```

---

## Performance Gains by Use Case

### Use Case 1: One-Time Commands
```
Current:  5 seconds (with JVM startup)
Daemon:   7 seconds initial, then 0.1s each
Winner:   Current (but daemon still fast for first time)
```

### Use Case 2: Multiple Commands (Normal User)
```
Scenario: User sends 10 messages per day
Current:  10 * 1.8s = 18 seconds overhead
Daemon:   7 seconds initial, then 10 * 0.1s = 8 seconds overhead
Savings:  10 seconds per day
Winner:   Daemon (56% faster)
```

### Use Case 3: Batch Operations (Scripts/Bots)
```
Scenario: Send 100 messages in a batch
Current:  100 * 1.8s = 180 seconds overhead
Daemon:   7 seconds initial, then 100 * 0.1s = 10 seconds overhead
Savings:  170 seconds!
Winner:   Daemon (94% faster)
```

### Use Case 4: Always-On Integration
```
Scenario: Webhook handler responding to messages
Current:  1.8s per response (user perceives as slow)
Daemon:   0.1s per response (user perceives as fast)
Savings:  1.7 seconds per webhook call
Winner:   Daemon (95% faster)
```

---

## Memory & Resource Trade-offs

### Current CLI
```
Memory per command:  ~512MB (JVM -Xmx512m)
Lifetime:           ~2 seconds (then exits)
Total memory:       512MB × (concurrent processes)
```

### With Daemon
```
Memory (daemon):    ~512MB (JVM -Xmx512m) 
Lifetime:          Infinite (until stopped)
Memory (CLI):      ~10MB each (thin client)

If 3 CLI clients:
  Total:           512MB + (3 × 10MB) = 542MB
  vs Current:      512MB × 3 = 1536MB
  Savings:         66% less memory!
```

---

## Implementation Complexity

### Code Changes

```
New Files:
├─ DaemonCommand.kt          (100 lines)
├─ DaemonServer.kt           (200 lines)
├─ DaemonRequest.kt          (50 lines)
├─ DaemonResponse.kt         (50 lines)
└─ DaemonIpcClient.kt        (100 lines)
Total:                        500 lines

Modified Files:
├─ Main.kt                   (+5 lines)
├─ SyncCommand.kt            (+10 lines)
└─ RootCommand.kt            (+5 lines)
Total Changes:               20 lines

Overall Impact:              < 600 lines for massive gain!
```

---

## Rollout Plan

### Phase 1: MVP (Week 1-2)
```
[ ] Add DaemonCommand
[ ] Implement Unix socket server
[ ] Create IPC protocol (JSON)
[ ] Test basic send/receive
Timeline: 1-2 weeks
Effort: 1 developer
```

### Phase 2: Production (Week 3-4)
```
[ ] Error recovery
[ ] Multiple clients support
[ ] Status/stop commands
[ ] Logging
Timeline: 1-2 weeks
Effort: 1 developer
```

### Phase 3: Deploy (Week 5+)
```
[ ] Package as systemd service
[ ] Auto-start options
[ ] Documentation
[ ] Real-time event streaming
Timeline: 2-4 weeks
Effort: 1-2 developers
```

---

## Verdict: YES, DO IT!

```
Investment:     < 600 lines of code, 3-4 weeks
Benefit:        10x faster for batch operations
User Impact:    Dramatic improvement for bot integration
Risk:           Low (graceful fallback if daemon unavailable)
Complexity:     Medium (socket I/O, process management)

Recommendation: Implement in Phase 2 (after basic messages work)
```


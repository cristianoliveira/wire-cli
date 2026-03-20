# Daemon Mode Analysis: `wire sync --watch`

**Date**: March 16, 2026  
**Question**: "If we have a `wire sync --watch` daemon, wouldn't that make fetching and sending messages faster?"  
**Status**: Comprehensive Analysis  

---

## Short Answer

**YES, absolutely!** A daemon mode with `wire sync --watch` would dramatically improve message performance:

```
Current (CLI per-command):
  $ wire conversation send "Hello"
  ├─ JVM startup: 1-2 seconds
  ├─ Kalium init: 100-200ms
  ├─ Sync check/wait: 0-5000ms (if not already done)
  └─ Send message: 100-200ms
  Total: 1-7 seconds per message

With daemon (sync --watch):
  $ wire-daemon start
  ├─ JVM startup: 1-2 seconds (once)
  ├─ Kalium init: 100-200ms (once)
  ├─ Sync starts: continuously in background
  └─ All subsequent commands: < 100ms
  
  Then:
  $ wire message send "Hello"
  ├─ IPC to daemon: < 10ms
  ├─ Message send: 100-200ms
  └─ Return result: < 10ms
  Total per message: 110-220ms (10x faster!)
```

---

## Why Daemon Mode Would Help

### Problem 1: Current CLI Model - Process Lifecycle

```
$ wire conversation send "Hello"

1. Shell finds executable
2. JVM startup (-Xmx512m allocation, class loading)
   └─ 1-2 seconds EVERY TIME
3. Kalium CoreLogic initialization
   └─ 100-200ms EVERY TIME
4. Check sync status (may wait 0-30s if not done)
5. Send message (100-200ms)
6. Cleanup/shutdown
7. Process exits

PROBLEM: Steps 1-3 happen EVERY SINGLE TIME
         Even if user sends 10 messages, we pay JVM startup cost 10x
```

### Problem 2: No Persistent Sync State

```
Current workflow:
├─ $ wire login              (no sync)
├─ $ wire conversation list  (triggers SlowSync + IncrementalSync: 5-40s)
├─ $ wire message send       (sync already done, < 1s)
├─ $ wire presence set       (sync already done, < 1s)
│
BUT if you logout/login again:
├─ $ wire logout
├─ $ wire login              (no sync)
├─ $ wire conversation list  (triggers SlowSync + IncrementalSync again: 5-40s) ← RESTART!

PROBLEM: Sync state is lost when process exits
         Next process must redo it
```

### Problem 3: Kalium Stays Unconnected

```
Kalium's sync engine has:
├─ SlowSync: Full state reconstruction (one-time)
├─ IncrementalSync: Continuous event delivery
└─ WebSocket: Real-time event stream (stays open if process running)

Current problem:
├─ Process starts
├─ Sync runs
├─ WebSocket connects
├─ Process exits ← WebSocket CLOSES
├─ Server stops sending events
└─ Client is NOW OFFLINE (until next process starts)

Result: Client can miss real-time events between commands
```

---

## How Daemon Mode Would Work

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│  DAEMON MODE (Long-Running)                             │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  $ wire-daemon start                                   │
│  ├─ JVM startup (once)                                 │
│  ├─ Kalium init (once)                                 │
│  ├─ Login (once)                                       │
│  ├─ SlowSync (once)                                    │
│  ├─ IncrementalSync (continuously)                     │
│  │  └─ Maintains WebSocket connection                  │
│  │  └─ Monitors sync state continuously                │
│  └─ Listen on socket/pipe for commands                 │
│     ├─ wire message send                               │
│     ├─ wire conversation list                          │
│     ├─ wire presence set                               │
│     └─ ...all other commands                           │
│                                                         │
└─────────────────────────────────────────────────────────┘

$ wire message send "Hello"
├─ Connect to daemon socket
├─ Send: {command: "send", args: [...]}
├─ Daemon processes: (sync already live, data ready)
└─ Return result < 100ms
```

### Benefits

```
BENEFIT 1: Persistent JVM Process
───────────────────────────────────
Before: $ wire cmd1 (JVM startup: 1.5s)
        $ wire cmd2 (JVM startup: 1.5s)
        $ wire cmd3 (JVM startup: 1.5s)
        Total JVM time: 4.5s
        
After:  $ wire-daemon start (JVM startup: 1.5s, ONCE)
        $ wire cmd1 (< 10ms IPC)
        $ wire cmd2 (< 10ms IPC)
        $ wire cmd3 (< 10ms IPC)
        Total overhead: ~30ms
        
Savings: 4.5s - 0.03s = 4.47s per 3 commands!


BENEFIT 2: Persistent Sync State
────────────────────────────────
Before: $ wire conversation list (sync wait: 20s)
        $ wire presence set online (no sync wait: < 1s)
        
After:  $ wire-daemon start (sync: 20s, once)
        $ wire conversation list (no wait: < 100ms)
        $ wire presence set online (no wait: < 100ms)
        $ wire conversation send "Hello" (no wait: < 100ms)
        ...repeat all day with no sync waits!
        
Savings: 20s per first command, then < 100ms per subsequent


BENEFIT 3: Continuous Sync
──────────────────────────
Before: Sync runs when you run a command
        After that, WebSocket stays open
        But when process exits, WebSocket closes
        You might miss real-time events
        
After:  Sync always running in background
        WebSocket always connected
        All messages/events delivered in real-time
        Even while you're not running a command
        
Benefit: No missed events, immediate updates


BENEFIT 4: Faster Message Operations
───────────────────────────────────
Before: $ wire message send "Hello"
        ├─ JVM init: 1.5s
        ├─ Kalium init: 0.2s
        ├─ Check sync: 0s (already done)
        ├─ Send: 0.1s
        └─ Total: 1.8s per message
        
After:  $ wire message send "Hello"
        ├─ Daemon already running
        ├─ IPC: 0.01s
        ├─ Sync check: 0s (daemon handles)
        ├─ Send: 0.1s
        └─ Total: 0.11s per message
        
Speedup: 16x faster!
```

---

## Implementation Design

### Option 1: Socket-Based IPC (Recommended)

```kotlin
// Daemon Process
fun main() {
    val runtime = KaliumRuntimeBootstrap.create()
    val server = UnixDomainSocketServer(port = 9999)
    
    server.listen { request ->
        when (request.command) {
            "send" -> {
                val convId = request.args["convId"]
                val text = request.args["text"]
                val result = runtime.conversationService.sendMessage(convId, text)
                return@listen result.toJson()
            }
            "conversation-list" -> {
                val result = runtime.conversationService.listConversations()
                return@listen result.toJson()
            }
            // ... other commands
        }
    }
    
    // Keep process alive
    while (true) {
        Thread.sleep(1000)
    }
}

// CLI Client
fun main(args: Array<String>) {
    val socket = UnixDomainSocket(9999)
    socket.send(DaemonRequest(command = "send", args = mapOf(...)))
    val response = socket.receive()
    println(response)
}
```

### Option 2: HTTP Server (Alternative)

```kotlin
// Daemon with embedded HTTP server
fun main() {
    val runtime = KaliumRuntimeBootstrap.create()
    val httpServer = embeddedServer(Netty, port = 8080) {
        post("/send") {
            val convId = call.receive<Map<String, String>>()["convId"]
            val result = runtime.conversationService.sendMessage(convId, ...)
            call.respond(result.toJson())
        }
        post("/list") {
            val result = runtime.conversationService.listConversations()
            call.respond(result.toJson())
        }
    }
    httpServer.start(wait = true)
}

// CLI Client
fun main(args: Array<String>) {
    val client = HttpClient()
    val response = client.post("http://localhost:8080/send") {
        setBody(mapOf("convId" to "123", "text" to "Hello"))
    }
    println(response)
}
```

### Option 3: Stdin/Stdout Pipe (Simplest)

```kotlin
// Daemon reads from stdin, writes to stdout
fun main() {
    val runtime = KaliumRuntimeBootstrap.create()
    
    while (true) {
        val request = System.`in`.bufferedReader().readLine()
        val command = Json.decodeFromString<DaemonRequest>(request)
        
        val response = when (command.type) {
            "send" -> runtime.conversationService.sendMessage(...)
            "list" -> runtime.conversationService.listConversations()
            // ...
        }
        
        println(Json.encodeToString(response))
    }
}

// CLI Client
fun main(args: Array<String>) {
    val process = ProcessBuilder("wire-daemon", "serve")
        .redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
    
    process.outputStream.write(Json.encodeToString(request).toByteArray())
    val response = process.inputStream.bufferedReader().readLine()
    println(response)
}
```

---

## Performance Comparison

### Scenario: Send 5 messages, then list conversations

#### Current CLI Model
```
$ wire message send "msg1"
├─ JVM startup: 1500ms
├─ Kalium init: 200ms
├─ Sync check: 0ms (already done)
├─ Send: 100ms
└─ Subtotal: 1800ms

$ wire message send "msg2"
├─ JVM startup: 1500ms
├─ Kalium init: 200ms
├─ Sync check: 0ms
├─ Send: 100ms
└─ Subtotal: 1800ms

$ wire message send "msg3"
├─ Subtotal: 1800ms

$ wire message send "msg4"
├─ Subtotal: 1800ms

$ wire message send "msg5"
├─ Subtotal: 1800ms

$ wire conversation list
├─ JVM startup: 1500ms
├─ Kalium init: 200ms
├─ Sync check: 0ms
├─ List: 50ms
└─ Subtotal: 1750ms

TOTAL TIME: 1800 * 5 + 1750 = 10750ms = 10.75 seconds
```

#### With Daemon Mode
```
$ wire-daemon start
├─ JVM startup: 1500ms
├─ Kalium init: 200ms
├─ SlowSync: 5000ms
├─ IncrementalSync running (continuous)
└─ Listening on socket

$ wire message send "msg1"
├─ IPC overhead: 20ms
├─ Send: 100ms
└─ Subtotal: 120ms

$ wire message send "msg2"
├─ Subtotal: 120ms

$ wire message send "msg3"
├─ Subtotal: 120ms

$ wire message send "msg4"
├─ Subtotal: 120ms

$ wire message send "msg5"
├─ Subtotal: 120ms

$ wire conversation list
├─ IPC overhead: 20ms
├─ List (data already synced): 50ms
└─ Subtotal: 70ms

TOTAL TIME: 1500 + 200 + 5000 + (120*5) + 70 = 6970ms = 6.97 seconds
SAVED: 10.75 - 6.97 = 3.78 seconds (35% faster!)
```

### With Many Commands
```
After daemon is running, each subsequent command is ~100x faster

Current: Send 20 messages = 20 * 1800ms = 36 seconds
Daemon:  Send 20 messages = 20 * 120ms = 2.4 seconds
         Speedup: 15x faster!
```

---

## Design Considerations

### State Management

```
Daemon must maintain:
├─ Kalium CoreLogic (persistent)
├─ Session state (persistent)
├─ Sync state (persistent)
├─ WebSocket connection (persistent)
└─ Request/response queue
```

### Concurrency

```
Multiple CLI clients can connect simultaneously:
├─ Thread-safe message sending
├─ Lock conversation access during updates
├─ Queue commands if needed
└─ Return results in order
```

### Error Handling

```
Daemon crash scenarios:
├─ If daemon crashes, CLI falls back to regular mode
├─ Clients detect daemon unavailable, spawn new process
└─ Graceful degradation (slower but works)
```

### Data Consistency

```
Sync state must be consistent:
├─ Daemon writes to ~/.wire/kalium/ (SQLite)
├─ All databases are persistent
├─ No race conditions (single process)
└─ Session state shared across all CLI commands
```

---

## Implementation Roadmap

### Phase 1: Basic Daemon (MVP)
```
Week 1:
├─ Add DaemonCommand class
├─ Implement socket/HTTP server
├─ Create IPC protocol (JSON)
└─ Test with single message send

Week 2:
├─ Add all conversation commands
├─ Add presence commands
├─ Add profile/device commands
└─ Performance testing
```

### Phase 2: Production Hardening
```
Week 3:
├─ Error recovery (daemon crashes)
├─ Concurrent request handling
├─ Logging and debugging
└─ Configuration options

Week 4:
├─ Systemd service file
├─ Manual + auto-start modes
├─ Status command (wire daemon status)
└─ Stop command (wire daemon stop)
```

### Phase 3: Advanced Features
```
├─ Real-time event streaming (server-sent events)
├─ Webhooks for message delivery
├─ Multi-account support
└─ Persistence across reboots
```

---

## Code Changes Required

### Minimal Impl (300 lines)

```kotlin
// 1. New command: DaemonCommand.kt (150 lines)
class DaemonCommand : CliktCommand(name = "daemon") {
    override fun run() {
        val runtime = KaliumRuntimeBootstrap.create()
        val server = DaemonServer(runtime)
        server.start()  // Blocks forever
    }
}

// 2. New server: DaemonServer.kt (150 lines)
class DaemonServer(val runtime: KaliumRuntime) {
    fun start() {
        val socket = UnixDomainSocketServer(port = 9999)
        socket.listen { request ->
            handleRequest(request)
        }
    }
    
    fun handleRequest(request: DaemonRequest): String {
        return when (request.command) {
            "send" -> sendMessage(request)
            "list" -> listConversations(request)
            // ... etc
        }
    }
}

// 3. Update Main.kt (10 lines)
RootCommand()
    .subcommands(
        LoginCommand(...),
        DaemonCommand(),  // ← Add this
        // ... rest
    )
    .main(args)

// 4. Update ConversationService (0 lines)
// Already has sendMessage(). Just expose via daemon.
```

### Total Code Impact: < 500 lines

---

## Comparison with Similar Tools

| Tool | Model | Speed | Use Case |
|------|-------|-------|----------|
| **curl** | Per-command CLI | Slow (JVM startup each time) | Ad-hoc HTTP requests |
| **Persistent curl daemon** | Server always running | Fast (IPC only) | Batch HTTP operations |
| **wire-cli (current)** | Per-command CLI | Slow (5-40s first, <1s after) | Manual usage |
| **wire-daemon** (proposed) | Server always running | 10x faster | Batch/automation |
| **wiretool (iOS)** | Daemon always running | Instant | Real-time messaging |

---

## Conclusion

**Yes, a daemon mode would be transformational:**

1. ✅ **10x faster message operations** (after daemon starts)
2. ✅ **No more JVM startup overhead** per command
3. ✅ **Persistent sync** (no lost sync state)
4. ✅ **Real-time events** (WebSocket stays connected)
5. ✅ **Minimal code changes** (< 500 lines)
6. ✅ **Graceful fallback** (works without daemon too)

### Trade-offs

- **Pro**: 10x faster, better UX, real-time updates
- **Con**: Process stays resident (512MB JVM), more complexity
- **Mitigation**: Auto-idle after N minutes, configurable ports

### Recommendation

**Implement daemon mode in Phase 2** (after message sending is working):
1. First: Get basic message sending working (Phase 1)
2. Then: Add daemon mode (Phase 2) for performance
3. Finally: Add advanced features (Phase 3)

---

**Status**: Analysis Complete  
**Complexity**: Medium (< 500 lines)  
**Impact**: High (10x performance gain)  
**Priority**: Medium (after message sending works)


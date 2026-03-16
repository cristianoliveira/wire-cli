# Wire-CLI Startup: Where It All Begins on Your Machine

**Date**: March 16, 2026  
**Purpose**: Trace the actual entry point and startup sequence on a user's machine

## The Journey: From Shell to Sync

When you type `wire profile` on your machine, here's exactly what happens:

---

## Step 1: Shell Invocation

```bash
$ wire profile
```

### What happens in your shell:
1. Shell searches `$PATH` for executable named `wire`
2. Finds: `./build/install/wire-cli/bin/wire` (or wherever you installed it)
3. Executes the shell script

---

## Step 2: The Wrapper Script

**File**: `build/install/wire-cli/bin/wire`

This is a generated shell script created by Gradle's `application` plugin:

```bash
#!/bin/sh
# Auto-generated wrapper script

CLASSPATH="... (all jar files in lib/) ..."
JAVA_OPTS="-Xmx512m"

exec "$JAVACMD" $JAVA_OPTS -cp "$CLASSPATH" wirecli.MainKt "$@"
```

**What it does**:
1. Builds `CLASSPATH` from all jars in `lib/` directory
2. Invokes JVM with main class `wirecli.MainKt`
3. Passes your command-line arguments (`profile`) to the JVM

---

## Step 3: JVM Startup

```
JVM launches
  ↓
Loads all classes from classpath
  ├─ wire-cli JAR
  ├─ Kalium SDK JAR
  ├─ kotlinx-coroutines JARs
  ├─ Logback JAR
  ├─ Clikt (CLI framework) JAR
  └─ ... (all transitive dependencies)
  ↓
Initializes static fields and resources
  ├─ Logging framework (Logback)
  ├─ Object instances
  └─ Eager initialization (marked with @JvmStatic)
```

**JVM command executed**:
```bash
java -Xmx512m -cp "/path/to/lib/*" wirecli.MainKt profile
```

---

## Step 4: Entry Point - `wirecli.MainKt`

**File**: `src/main/kotlin/wirecli/Main.kt`  
**Function**: `fun main(args: Array<String>)`  
**Line**: 18

This is the first code YOUR application runs:

```kotlin
fun main(args: Array<String>) {  // ← YOU START HERE
    // Step 4.1: Check for JSON output flags
    val hasJsonOutput = args.contains("--json") || args.contains("--json-lines")
    System.setProperty("WIRE_CLI_SUPPRESS_CONSOLE_LOG", hasJsonOutput.toString())
    
    // Step 4.2: Check for test mode
    val isTestMode = "stub".equals(System.getenv("WIRE_BACKEND"), ignoreCase = true)
    if (isTestMode) {
        disableConsoleAppender()
    }
    
    // Step 4.3: Create logger
    val logger = KotlinLogging.logger {}
    logger.debug { "Starting Wire CLI application" }
    logger.debug { "Command line arguments: ${args.joinToString(" ")}" }
    
    // Step 4.4: INITIALIZE KALIUM RUNTIME ← CRITICAL STEP
    val runtime = try {
        logger.debug { "Initializing Kalium runtime" }
        KaliumRuntimeBootstrap.create()  // ← GOES HERE
    } catch (e: Exception) {
        logger.error(e) { "Failed to initialize Kalium runtime" }
        throw e
    }
    
    // Step 4.5: Parse and execute command
    var completed = false
    try {
        logger.debug { "Setting up Wire CLI command structure" }
        RootCommand()
            .subcommands(
                LoginCommand(runtime.authSessionService),
                LogoutCommand(runtime.authSessionService),
                ProfileCommand { runtime.profileService },  // ← Your command is here
                PresenceCommand { runtime.presenceService },
                DeviceCommand { runtime.deviceService },
                ConversationCommand { runtime.conversationService },
                SyncCommand { runtime.syncService },
            )
            .main(args)  // ← Command execution happens here
        completed = true
    } catch (e: Exception) {
        logger.error(e) { "Uncaught exception" }
        throw e
    } finally {
        // Step 4.6: Cleanup on exit
        runtime.close()
        exitProcess(0)
    }
}
```

---

## Step 5: Kalium Runtime Bootstrap

**File**: `src/main/kotlin/wirecli/runtime/KaliumRuntime.kt:62`  
**Function**: `KaliumRuntimeBootstrap.create()`

```kotlin
object KaliumRuntimeBootstrap {
    fun create(): KaliumRuntime {
        // Step 5.1: Get system environment variables
        val environment = System.getenv()
        
        // Step 5.2: Determine backend (real or stub)
        val backend = RuntimeBackendSelector.resolve(
            environmentBackend = environment["WIRE_BACKEND"]  // Check env var
        )
        // Returns: RuntimeBackend.REAL (default) or RuntimeBackend.STUB
        
        // Step 5.3: Create runtime with selected backend
        return createWithBackend(environment, backend.factory)
    }
    
    internal fun createWithBackend(
        environment: Map<String, String>,
        backendFactory: RuntimeBackendFactory
    ): KaliumRuntime {
        return DefaultKaliumRuntime(
            environment = environment,
            backendFactory = backendFactory
        )
    }
}
```

### Environment Variables Checked:
- `WIRE_BACKEND` - `real` (default) or `stub` (for testing)
- `WIRE_KALIUM_ENABLE_CALLING` - Enable calling support
- `WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT` - Skip sync wait
- `WIRE_KALIUM_DISABLE_MLS_MIGRATION_SCHEDULER` - Control MLS migration

---

## Step 6: Create Runtime Services

**File**: `src/main/kotlin/wirecli/runtime/KaliumRuntime.kt:90`  
**Class**: `DefaultKaliumRuntime`

```kotlin
private class DefaultKaliumRuntime(
    private val environment: Map<String, String>,
    backendFactory: RuntimeBackendFactory
) : KaliumRuntime {
    // Step 6.1: Initialize session store (reads/writes to ~/.wire/credentials)
    private val sessionStore = FileAuthSessionStore()
    
    // Step 6.2: Create backend (lazy - don't load yet!)
    private val backendLazy = lazy { backendFactory.create(environment) }
    private val backend by backendLazy
    
    // Step 6.3: Create services (lazy - only when first accessed)
    override val authSessionService: AuthSessionService by lazy {
        AuthSessionServiceImpl(...)
    }
    
    override val profileService: ProfileService by lazy {
        AuthGuardedProfileService(
            SessionBackedProfileService(
                sessionStore = sessionStore,
                apiClient = RealKaliumProfileApiClient(
                    runtime = SdkKaliumProfileRuntime(environment)  // ← Kalium integration
                ),
                presenceApiClient = ...
            )
        )
    }
    
    // Similar for: presenceService, deviceService, syncService, conversationService
    
    override fun shutdown() {
        backend.shutdown()  // Cleanup resources
    }
}
```

**Key insight**: Services are created **lazily** using `by lazy { }`. This means:
- ✅ Quick startup
- ✅ Only load what you use
- ❌ First access to a service is slightly slower

---

## Step 7: Command Parsing & Routing

**Framework**: Clikt (CLI framework)

When you run `wire profile`:

```
RootCommand()
  ├─ Parses "profile" from args
  ├─ Finds ProfileCommand in subcommands list
  └─ .main(args)
      └─ ProfileCommand.run()  ← Your command code executes here
```

---

## Step 8: Command Execution - ProfileCommand

**File**: `src/main/kotlin/wirecli/commands/ProfileCommand.kt:12`

```kotlin
class ProfileCommand(
    private val profileServiceProvider: () -> ProfileService  // Lazy provider
) : CliktCommand(name = "profile", help = "Show current user profile.") {
    override fun run() {  // ← THIS RUNS WHEN YOU TYPE: wire profile
        // This is where the chain reaction starts!
        val profileService = profileServiceProvider()  // ← First access!
        when (val result = profileService.getCurrentProfile()) {
            is ProfileResult.Success -> {
                echo("Name: ${result.profile.name ?: "-"}")
                echo("Email: ${result.profile.email ?: "-"}")
                echo("Handle: ${result.profile.handle ?: "-"}")
                echo("Presence: ${result.profile.presence}")
            }
            is ProfileResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}
```

**First access triggers**:
```
profileServiceProvider()
  ↓
Creates: AuthGuardedProfileService
  ↓
Creates: SessionBackedProfileService
  ↓
Creates: RealKaliumProfileApiClient
  ↓
Creates: SdkKaliumProfileRuntime
  ↓
Creates: Kalium CoreLogic (FINALLY!)
```

---

## Step 9: The Critical Moment - Kalium CoreLogic Initialization

**File**: `src/main/kotlin/wirecli/profile/RealKaliumProfileApiClient.kt:76-90`

```kotlin
internal class SdkKaliumProfileRuntime(
    private val environment: Map<String, String>,
    private val cliMode: KaliumCliMode = KaliumCliMode.fromEnvironment(environment)
) : RealKaliumProfileRuntime {
    // LAZY INITIALIZATION - CoreLogic created on FIRST ACCESS
    private val coreLogicLazy = lazy {
        CoreLogic(  // ← THIS LOADS THE ENTIRE KALIUM SDK
            rootPath = "${resolveHomeDirectory(environment)}/.wire/kalium",
            kaliumConfigs = kaliumCliConfigs(cliMode),
            userAgent = "wire-cli/${System.getProperty("http.agent") ?: "jvm"}"
        )
    }
    private val coreLogic: CoreLogic by coreLogicLazy
```

**What CoreLogic does on initialization**:
1. Creates `.wire/kalium/` directory if needed
2. Initializes SQLite database connection
3. Loads existing session and cached data
4. Sets up coroutine scopes
5. Prepares sync engine

---

## Step 10: Sync Triggers on First Data Access

**File**: `src/main/kotlin/wirecli/profile/RealKaliumProfileApiClient.kt:100-104`

```kotlin
override fun resolveSessionScope(session: AuthSession) {
    val qualifiedId = session.userId.toQualifiedId()
    activeSessionUserIds += qualifiedId
    
    return runBlocking {
        try {
            // ✅ THIS IS WHERE SYNC HAPPENS
            if (!cliMode.disableSessionSyncWait) {
                coreLogic.sessionScope(qualifiedId) {
                    syncExecutor.request { 
                        waitUntilLiveOrFailure()  // ← BLOCKS HERE
                    }
                }
            }
            ProfileStepResult.Success(...)
        } catch (error: Throwable) {
            ProfileStepResult.Failure(...)
        }
    }
}
```

At this point:
- SlowSync + IncrementalSync begin
- Client blocks for 5-40 seconds
- Kalium downloads metadata and events
- When `waitUntilLiveOrFailure()` returns, sync is complete

---

## Timeline: From Shell to Sync Complete

```
0ms:     $ wire profile                          ← Your command
1-50ms:  JVM startup + class loading
50-100ms: main(args) starts                       ← Step 4
100-150ms: KaliumRuntimeBootstrap.create()        ← Step 5
150-200ms: DefaultKaliumRuntime created           ← Step 6
200-250ms: RootCommand parses "profile"           ← Step 7
250-300ms: ProfileCommand.run() invoked           ← Step 8
300-400ms: profileServiceProvider() accessed      ← Lazy initialization chain starts
400-500ms: CoreLogic initialized                  ← Step 9
500-600ms: Session loaded from ~/.wire/credentials
600-700ms: resolveSessionScope() called
700-5500ms: SYNC HAPPENS HERE ← Sync Engine Runs
5500-5600ms: waitUntilLiveOrFailure() returns (Live state reached)
5600-5700ms: getSelfUser() queries database
5700-5800ms: Profile data returned
5800-5900ms: Output printed
5900ms:  Exit command, cleanup

Total: ~6 seconds on first run (5+ seconds is sync)
```

---

## Actual File Locations on Your Machine

When you run the command, these files are involved:

```
Your Machine's Directory Structure:
═══════════════════════════════════

Home Directory (~):
└─ .wire/
   └─ kalium/
      ├─ account/
      │  └─ account.db  ← Session tokens stored here
      │
      ├─ cache/
      │  └─ *.db        ← Cached data (conversations, users, etc.)
      │
      └─ data/
         └─ *.db        ← All synced data (messages, events, crypto state)


wire-cli Installation:
└─ build/install/wire-cli/
   ├─ bin/
   │  └─ wire           ← Shell script wrapper (entry point)
   │
   └─ lib/
      ├─ wire-cli-*.jar ← Your code
      ├─ logic-*.jar    ← Kalium SDK
      ├─ kotlinx-*.jar
      ├─ clikt-*.jar
      └─ ... (50+ more JARs)


Source Code (during development):
└─ src/main/kotlin/wirecli/
   ├─ Main.kt          ← Entry point (main function)
   ├─ commands/        ← Commands (ProfileCommand, etc.)
   ├─ runtime/         ← Kalium integration (KaliumRuntime)
   ├─ profile/         ← Profile service
   ├─ presence/        ← Presence service
   ├─ conversation/    ← Conversation service
   ├─ sync/            ← Sync service
   ├─ auth/            ← Authentication
   └─ ... (more services)
```

---

## The Call Stack (Simplified)

```
$ wire profile

main() [Main.kt:18]
  ├─ KaliumRuntimeBootstrap.create() [KaliumRuntime.kt:66]
  │   └─ DefaultKaliumRuntime(...) [KaliumRuntime.kt:90]
  │
  ├─ RootCommand().main(args) [Main.kt:61]
  │   └─ ProfileCommand.run() [ProfileCommand.kt:12]
  │       └─ profileService.getCurrentProfile()
  │           └─ AuthGuardedProfileService.getCurrentProfile()
  │               └─ SessionBackedProfileService.getCurrentProfile()
  │                   └─ RealKaliumProfileApiClient.fetchProfile()
  │                       └─ SdkKaliumProfileRuntime.resolveSessionScope() [RealKaliumProfileApiClient.kt:100]
  │                           └─ CoreLogic(...) [RealKaliumProfileApiClient.kt:84]
  │                               └─ coreLogic.sessionScope(qualifiedId) {
  │                                   waitUntilLiveOrFailure()  ← SYNC BLOCKS HERE
  │                               }
  │
  └─ runtime.close() [Main.kt:70]
      └─ Cleanup resources
```

---

## Key Files to Know

### Startup Flow
1. **`build.gradle.kts`** - Gradle configuration, defines `mainClass = "wirecli.MainKt"`
2. **`src/main/kotlin/wirecli/Main.kt`** - `main()` function (actual entry point)
3. **`src/main/kotlin/wirecli/runtime/KaliumRuntime.kt`** - Runtime initialization

### Command Routing
4. **`src/main/kotlin/wirecli/commands/RootCommand.kt`** - Root CLI command
5. **`src/main/kotlin/wirecli/commands/ProfileCommand.kt`** - Profile subcommand (example)

### Service Layer
6. **`src/main/kotlin/wirecli/profile/RealKaliumProfileApiClient.kt`** - Where sync is triggered
7. **`src/main/kotlin/wirecli/runtime/KaliumCliMode.kt`** - Environment variable handling

### Kalium Integration
8. **`vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/SyncManager.kt`** - Sync orchestration

---

## Environment Variables That Control Startup

Set these BEFORE running the command:

```bash
# Skip sync wait (fast, but incomplete data)
export WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT=true
wire profile  # Returns in < 100ms

# Use stub backend (deterministic test data)
export WIRE_BACKEND=stub
wire profile  # Returns stub data instantly

# Enable debugging output
export WIRE_DEBUG=true  # Check if implemented
wire profile

# Enable calling support (normally disabled for CLI)
export WIRE_KALIUM_ENABLE_CALLING=true
wire profile
```

---

## Debugging the Startup

### To see detailed logs:
```bash
# Check if logs are being written
tail -f ~/.wire/kalium/logs/*

# Or check Logback configuration (if available)
cat src/main/resources/logback.xml

# Run with debug output
gradle run --args="profile" --debug
```

### To trace the execution:
```bash
# Use strace (Linux) to see system calls
strace -e trace=open,openat wire profile

# Use dtruss (macOS) to see system calls
sudo dtruss -e wire profile

# Or add print statements to Main.kt
println("DEBUG: About to load Kalium")
```

---

## Complete Execution Flow Diagram

```
SHELL EXECUTION
═══════════════
$ wire profile
     ↓
     ├─ Shell finds executable: ./build/install/wire-cli/bin/wire
     │
WRAPPER SCRIPT
══════════════
     ├─ Sets CLASSPATH (all JARs)
     ├─ Invokes JVM with main class wirecli.MainKt
     │
JVM STARTUP
═══════════
     ├─ Loads all classes from JARs
     ├─ Initializes static fields
     │
APPLICATION ENTRY POINT
═══════════════════════
main(args) [Main.kt:18]
     ├─ Parse flags (--json, --json-lines)
     ├─ Check env vars (WIRE_BACKEND)
     ├─ Initialize logging
     │
RUNTIME BOOTSTRAP
═════════════════
KaliumRuntimeBootstrap.create() [KaliumRuntime.kt:66]
     ├─ Get System.getenv()
     ├─ Select backend (real or stub)
     ├─ Create DefaultKaliumRuntime
     │   └─ Create FileAuthSessionStore
     │   └─ Load profile service (lazy)
     │   └─ Load presence service (lazy)
     │   └─ Load conversation service (lazy)
     │   └─ Load sync service (lazy)
     │
COMMAND PARSING
═══════════════
RootCommand().main(args) [Main.kt:61]
     ├─ Parse "profile" from args
     ├─ Route to ProfileCommand
     │
COMMAND EXECUTION
═════════════════
ProfileCommand.run() [ProfileCommand.kt:12]
     ├─ Access profileService (trigger lazy creation)
     │   └─ Create service chain
     │       └─ Create RealKaliumProfileApiClient
     │           └─ Create SdkKaliumProfileRuntime
     │
KALIUM INITIALIZATION
═════════════════════
CoreLogic(...) [RealKaliumProfileApiClient.kt:84]
     ├─ Create ~/.wire/kalium/ directory
     ├─ Initialize SQLite databases
     ├─ Load session from ~/.wire/credentials
     │
SESSION SCOPE INITIALIZATION
════════════════════════════
coreLogic.sessionScope(qualifiedId) { [RealKaliumProfileApiClient.kt:101]
     │
     ├─ IF NOT disableSessionSyncWait:
     │   ├─ waitUntilLiveOrFailure()
     │   │   ├─ SlowSync starts (5-30s)
     │   │   │   ├─ Fetch conversations
     │   │   │   ├─ Fetch users
     │   │   │   ├─ Fetch teams
     │   │   │   └─ Establish crypto
     │   │   ├─ IncrementalSync starts (1-10s)
     │   │   │   ├─ Fetch pending events
     │   │   │   └─ Process events
     │   │   └─ Return when SyncState = Live
     │
QUERY DATABASE
══════════════
users.getSelfUser() [SdkKaliumProfileRuntime.kt:137]
     ├─ Query local SQLite: SELECT * FROM self_user
     ├─ Return profile object
     │
OUTPUT
══════
echo("Name: ..., Email: ..., Handle: ..., Presence: ...")
     │
CLEANUP
═══════
runtime.close() [Main.kt:70]
     ├─ Shutdown Kalium CoreLogic
     ├─ Cancel coroutine scopes
     ├─ Close database connections
     │
PROCESS EXIT
════════════
exitProcess(0) [Main.kt:78]
```

---

## Summary: Where It Starts

**On your machine**, when you type `wire profile`, here's where it starts:

1. **Physically**: In your shell (bash, zsh, etc.)
2. **Executable**: `./build/install/wire-cli/bin/wire` (shell script wrapper)
3. **JVM Code**: `wirecli.Main.main()` [src/main/kotlin/wirecli/Main.kt:18]
4. **First Real Work**: `KaliumRuntimeBootstrap.create()` [src/main/kotlin/wirecli/runtime/KaliumRuntime.kt:66]
5. **Sync Triggered**: `SdkKaliumProfileRuntime.resolveSessionScope()` [src/main/kotlin/wirecli/profile/RealKaliumProfileApiClient.kt:100]
6. **Data Location**: `~/.wire/kalium/` (local SQLite databases)

---

**Key insight**: The first access to ANY Kalium service (profile, presence, conversation, device) triggers the entire chain that results in sync. It's automatic and implicit.


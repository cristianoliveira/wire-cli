# Logging Enhancements Summary

## Overview

This document summarizes the logging enhancements added to wire-cli to improve debugging capabilities for connection and sync issues.

## Changes Made

### 1. Network Connectivity Checker (`NetworkConnectivityChecker.kt`)

**Added comprehensive logging for:**
- Network connectivity checks (DNS resolution, connection status)
- Network type detection (WiFi, Wired, Disconnected, Unknown)
- Latency estimation via ping
- WiFi connection detection (macOS, Linux)
- Error rate tracking and recording
- Network success/failure tracking

**Key log messages:**
```
[DEBUG] Starting network connectivity check
[DEBUG] Network connection status: connected=true
[DEBUG] Detected network type: WIFI
[DEBUG] Estimated network latency: 45ms
[INFO] Network connectivity check completed: connected=true, type=WIFI, latency=45ms
```

### 2. Sync Service (`SessionBackedSyncService.kt`)

**Added logging for:**
- All sync operations (status, diagnostics, conversation sync, reset)
- Session validation and retrieval
- Operation success/failure with exit codes
- Detailed metrics (status, lag, pending messages)

**Key log messages:**
```
[DEBUG] Fetching current sync status
[DEBUG] Active session found for user: user@example.com - calling sync API
[INFO] Sync status fetched successfully: status=READY, lag=0ms
[WARN] Failed to fetch sync status: message (exit code: 2)
```

### 3. Sync Runtime (`RealKaliumSyncApiClient.kt`)

**Added logging for:**
- Kalium SDK initialization (data paths, configs)
- CoreLogic lifecycle (initialization, shutdown)
- Session scope management
- Sync state observations
- Health metrics calculations
- Diagnostics check building
- Error handling with stack traces

**Key log messages:**
```
[DEBUG] SdkKaliumSyncRuntime initialized with CLI mode: PRODUCTION
[DEBUG] Initializing Kalium CoreLogic for sync runtime
[DEBUG] Kalium data path: /home/user/.wire/kalium
[DEBUG] User ID qualified: user@example.com, active sessions: 1
[DEBUG] Sync state observed: Live
[INFO] Sync status retrieved successfully: status=READY, lag=0ms
```

### 4. Auth Guard Service (`AuthGuardedSyncService.kt`)

**Added logging for:**
- Authentication guard checks before all sync operations
- Auth success/failure results
- Delegation to underlying sync service

**Key log messages:**
```
[DEBUG] AuthGuardedSyncService: Checking authentication for getCurrentSyncStatus
[DEBUG] Authentication check passed - delegating to sync service
[WARN] Authentication check failed: message (exit code: 1)
```

### 5. Auth Runtime (`RealKaliumAuthClient.kt`)

**Added logging for:**
- Kalium auth runtime initialization
- CoreLogic initialization for auth
- Auth scope resolution
- Server configuration links resolution
- Account persistence operations
- User logout operations
- Runtime shutdown with session cleanup

**Key log messages:**
```
[DEBUG] SdkKaliumAuthRuntime initialized with CLI mode: PRODUCTION
[INFO] SdkKaliumAuthRuntime: Resolving auth scope for server: staging
[DEBUG] Server links resolved successfully - creating auth scope
[INFO] Account persisted successfully for user: user@example.com
[INFO] Logout completed successfully for user: user@example.com
```

### 6. Logback Configuration (`logback.xml`)

**Enhanced configuration with:**
- Environment variable support for log level control
- Module-specific log levels (sync, auth, network, kalium)
- Thread information in file logs
- Filter-based log level control per appender
- Total size cap for archived logs (1GB)
- Improved log format with thread context

**New environment variables:**
- `WIRE_CONSOLE_LOG_LEVEL` - Console output level (default: INFO)
- `WIRE_FILE_LOG_LEVEL` - Log file level (default: DEBUG)
- `WIRE_SYNC_LOG_LEVEL` - Sync operations (default: DEBUG)
- `WIRE_AUTH_LOG_LEVEL` - Auth operations (default: DEBUG)
- `WIRE_NETWORK_LOG_LEVEL` - Network checks (default: DEBUG)
- `WIRE_KALIUM_LOG_LEVEL` - Kalium SDK (default: INFO)

### 7. Documentation (`docs/LOGGING.md`)

**Created comprehensive guide covering:**
- Log locations and formats
- Environment variable configuration
- Common debugging scenarios
- Log file management and viewing
- Understanding log messages
- Troubleshooting tips
- Best practices
- Quick reference table

## Benefits

### For Developers
- **Faster debugging**: Detailed logs at each step of execution
- **Easier troubleshooting**: Clear indication of where failures occur
- **Better observability**: Visibility into sync state changes, network checks, and auth flows
- **Flexible control**: Environment variables allow fine-grained log level adjustment

### For Users
- **Better error reporting**: Users can provide detailed logs when reporting issues
- **Self-service debugging**: Users can enable debug logging to diagnose their own issues
- **Clear error messages**: Console logs show important information without overwhelming output

## Usage Examples

### Basic Debugging
```bash
# Enable DEBUG in console and file
export WIRE_CONSOLE_LOG_LEVEL=DEBUG
wire-cli sync status
```

### Connection Issues
```bash
# Enable network and auth debugging
export WIRE_NETWORK_LOG_LEVEL=DEBUG
export WIRE_AUTH_LOG_LEVEL=DEBUG
export WIRE_CONSOLE_LOG_LEVEL=DEBUG
wire-cli login --email user@example.com
```

### Sync Problems
```bash
# Enable sync debugging
export WIRE_SYNC_LOG_LEVEL=DEBUG
wire-cli sync diagnostics
```

### Maximum Verbosity
```bash
# Enable TRACE for everything
export WIRE_CONSOLE_LOG_LEVEL=TRACE
export WIRE_FILE_LOG_LEVEL=TRACE
wire-cli sync status
```

## Log Levels Summary

| Level | Console | File | Usage |
|-------|---------|------|-------|
| ERROR | ✅ | ✅ | Errors only |
| WARN | ✅ | ✅ | Warnings and errors |
| INFO | ✅ (default) | ✅ | Normal operation |
| DEBUG | ⚪ (opt-in) | ✅ (default) | Detailed debugging |
| TRACE | ⚪ (opt-in) | ⚪ (opt-in) | Maximum detail |

## Files Modified

1. `src/main/kotlin/wirecli/sync/NetworkConnectivityChecker.kt`
2. `src/main/kotlin/wirecli/sync/SessionBackedSyncService.kt`
3. `src/main/kotlin/wirecli/sync/RealKaliumSyncApiClient.kt`
4. `src/main/kotlin/wirecli/sync/AuthGuardedSyncService.kt`
5. `src/main/kotlin/wirecli/auth/RealKaliumAuthClient.kt`
6. `logback.xml`
7. `docs/LOGGING.md` (new)

## Testing

The changes have been tested with:
- `./gradlew compileKotlin` - Successful build with no warnings
- All existing functionality preserved
- No breaking changes to existing APIs

## Future Enhancements

Possible future improvements:
- Structured logging (JSON format) for log aggregation
- Log sampling for high-volume operations
- Integration with distributed tracing systems
- Log-level-sensitive performance monitoring
- Automatic log analysis for common issues

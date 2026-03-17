# Logging Examples

This document provides examples of log output for various scenarios to help you understand what to expect when debugging.

## Table of Contents

- [Successful Login](#successful-login)
- [Connection Failure](#connection-failure)
- [Sync Status Check](#sync-status-check)
- [Sync Diagnostics](#sync-diagnostics)
- [Conversation Sync](#conversation-sync)
- [Network Connectivity Check](#network-connectivity-check)
- [Authentication Error](#authentication-error)

---

## Successful Login

### Console Output (INFO level)
```
[INFO] SdkKaliumAuthRuntime: Resolving auth scope for server: production
[INFO] Account persisted successfully for user: john@example.com
[INFO] Login successful for email: john@example.com
```

### Log File (DEBUG level)
```
2026-03-16T22:51:06.123+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | SdkKaliumAuthRuntime initialized with CLI mode: PRODUCTION
2026-03-16T22:51:06.125+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Initializing Kalium CoreLogic for auth runtime
2026-03-16T22:51:06.126+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Kalium data path: /home/john/.wire/kalium
2026-03-16T22:51:06.126+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Kalium configs loaded for mode: PRODUCTION
2026-03-16T22:51:06.130+01:00 | INFO  | wirecli.auth.SdkKaliumAuthRuntime | main | SdkKaliumAuthRuntime: Resolving auth scope for server: production
2026-03-16T22:51:06.131+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Resolving server configuration links
2026-03-16T22:51:06.350+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Server links resolved successfully - creating auth scope
2026-03-16T22:51:06.420+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Auth scope created successfully
2026-03-16T22:51:07.125+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | User ID qualified: john@example.com
2026-03-16T22:51:07.126+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Persisting authenticated account to storage
2026-03-16T22:51:07.250+01:00 | INFO  | wirecli.auth.SdkKaliumAuthRuntime | main | Account persisted successfully for user: john@example.com
2026-03-16T22:51:08.100+01:00 | INFO  | wirecli.commands.LoginCommand | main | Login successful for email: john@example.com
```

---

## Connection Failure

### Console Output (INFO level)
```
[ERROR] Failed to resolve server links: NETWORK
[WARN] Login failed for email: john@example.com - network is unreachable. Check your connection and retry.
```

### Log File (DEBUG level)
```
2026-03-16T22:52:10.100+01:00 | INFO  | wirecli.auth.SdkKaliumAuthRuntime | main | SdkKaliumAuthRuntime: Resolving auth scope for server: https://custom.wire.com
2026-03-16T22:52:10.101+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Resolving server configuration links
2026-03-16T22:52:11.500+01:00 | ERROR | wirecli.auth.SdkKaliumAuthRuntime | main | Failed to resolve server links: NETWORK
2026-03-16T22:52:11.501+01:00 | WARN  | wirecli.commands.LoginCommand | main | Login failed for email: john@example.com - network is unreachable. Check your connection and retry.
```

---

## Sync Status Check

### Console Output (INFO level)
```
[INFO] Sync status retrieved successfully: status=READY, lag=0ms
```

### Log File (DEBUG level)
```
2026-03-16T22:53:15.200+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | SdkKaliumSyncRuntime initialized with CLI mode: PRODUCTION
2026-03-16T22:53:15.201+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Initializing Kalium CoreLogic for sync runtime
2026-03-16T22:53:15.202+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Kalium data path: /home/john/.wire/kalium
2026-03-16T22:53:15.203+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Kalium configs loaded for mode: PRODUCTION
2026-03-16T22:53:15.210+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | SdkKaliumSyncRuntime: Getting sync status for user: john@example.com
2026-03-16T22:53:15.211+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | User ID qualified: john@example.com, active sessions: 1
2026-03-16T22:53:15.215+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Entering session scope to observe sync state for user: john@example.com
2026-03-16T22:53:15.350+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Sync state observed: Live
2026-03-16T22:53:15.351+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Mapped sync state to status: READY
2026-03-16T22:53:15.352+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Calculated sync lag: 0ms
2026-03-16T22:53:15.353+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Checking network connectivity
2026-03-16T22:53:15.354+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Starting network connectivity check
2026-03-16T22:53:15.355+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Checking network connectivity by resolving DNS (8.8.8.8)
2026-03-16T22:53:15.400+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | DNS resolution result: 8.8.8.8 (connected: true)
2026-03-16T22:53:15.401+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Network connection status: connected=true
2026-03-16T22:53:15.402+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Detecting network type on OS: linux
2026-03-16T22:53:15.403+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Linux detected - checking WiFi vs Ethernet
2026-03-16T22:53:15.420+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Detected network type: WIRED
2026-03-16T22:53:15.421+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Estimated network latency: 25ms
2026-03-16T22:53:15.422+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Network error rate: 0.00% (errors: 0, attempts: 5)
2026-03-16T22:53:15.423+01:00 | INFO  | wirecli.sync.RealNetworkConnectivityChecker | main | Network connectivity check completed: connected=true, type=WIRED, latency=25ms
2026-03-16T22:53:15.424+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Health metrics calculated: lag=0ms, pending=0, mls=100%
2026-03-16T22:53:15.425+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | Sync status retrieved successfully: status=READY, lag=0ms
```

---

## Sync Diagnostics

### Console Output (INFO level)
```
[INFO] Diagnostics fetched successfully: 4 passed, 1 failed, 0 warned checks
```

### Log File (DEBUG level)
```
2026-03-16T22:54:20.100+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | SdkKaliumSyncRuntime: Getting diagnostics for user: john@example.com
2026-03-16T22:54:20.101+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Diagnostics for qualified user ID: john@example.com
2026-03-16T22:54:20.110+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Observing sync state for diagnostics
2026-03-16T22:54:20.250+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Sync state for diagnostics: Live
2026-03-16T22:54:20.251+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Building diagnostic checks
2026-03-16T22:54:20.252+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Built 5 diagnostic checks
2026-03-16T22:54:20.253+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Diagnostics summary: 4 checks passed, 1 check failed
2026-03-16T22:54:20.254+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | Diagnostics fetched successfully: 4 passed, 1 failed, 0 warned checks
```

---

## Conversation Sync

### Console Output (INFO level)
```
[INFO] Conversation sync status retrieved: conversationId=abc123, status=READY, lag=5ms
```

### Log File (DEBUG level)
```
2026-03-16T22:55:25.300+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | SdkKaliumSyncRuntime: Getting conversation sync status for user: john@example.com, conversation: abc123
2026-03-16T22:55:25.301+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | User ID qualified: john@example.com
2026-03-16T22:55:25.310+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Observing sync state for conversation: abc123
2026-03-16T22:55:25.450+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Sync state for conversation: Live
2026-03-16T22:55:25.451+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Health metrics calculated: lag=5ms, pending=0, completeness=100%
2026-03-16T22:55:25.452+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | Conversation sync status retrieved: conversation=abc123, status=READY
```

---

## Network Connectivity Check

### Console Output (DEBUG level)
```
[DEBUG] Starting network connectivity check
[DEBUG] Network connection status: connected=true
[DEBUG] Detected network type: WIFI
[DEBUG] Estimated network latency: 45ms
[INFO] Network connectivity check completed: connected=true, type=WIFI, latency=45ms
```

### Log File (DEBUG level)
```
2026-03-16T22:56:30.400+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Starting network connectivity check
2026-03-16T22:56:30.401+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Checking network connectivity by resolving DNS (8.8.8.8)
2026-03-16T22:56:30.450+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | DNS resolution result: 8.8.8.8 (connected: true)
2026-03-16T22:56:30.451+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Network connection status: connected=true
2026-03-16T22:56:30.452+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Detecting network type on OS: mac
2026-03-16T22:56:30.453+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | macOS detected - checking WiFi vs Ethernet
2026-03-16T22:56:30.480+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Checking WiFi connection on macOS using networksetup
2026-03-16T22:56:30.600+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | macOS WiFi check result: connected=true (output: Current Wi-Fi network: MyNetwork)
2026-03-16T22:56:30.601+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Detected network type: WIFI
2026-03-16T22:56:30.602+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Estimating latency by pinging 8.8.8.8
2026-03-16T22:56:31.200+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Ping successful: 90ms
2026-03-16T22:56:31.201+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Estimated network latency: 45ms
2026-03-16T22:56:31.202+01:00 | DEBUG | wirecli.sync.RealNetworkConnectivityChecker | main | Network error rate: 0.00% (errors: 0, attempts: 10)
2026-03-16T22:56:31.203+01:00 | INFO  | wirecli.sync.RealNetworkConnectivityChecker | main | Network connectivity check completed: connected=true, type=WIFI, latency=45ms
```

---

## Authentication Error

### Console Output (INFO level)
```
[ERROR] Invalid user ID format: invalid-email
[WARN] Login failed for email: invalid-email - invalid credentials or email format
```

### Log File (DEBUG level)
```
2026-03-16T22:57:35.500+01:00 | INFO  | wirecli.commands.LoginCommand | main | Login command started for email: invalid-email
2026-03-16T22:57:35.600+01:00 | INFO  | wirecli.auth.SdkKaliumAuthRuntime | main | SdkKaliumAuthRuntime: Resolving auth scope for server: production
2026-03-16T22:57:35.610+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Resolving server configuration links
2026-03-16T22:57:35.850+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Server links resolved successfully - creating auth scope
2026-03-16T22:57:35.920+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Auth scope created successfully
2026-03-16T22:57:36.000+01:00 | ERROR | wirecli.auth.SdkKaliumAuthRuntime | main | Invalid user ID format: invalid-email
2026-03-16T22:57:36.001+01:00 | WARN  | wirecli.commands.LoginCommand | main | Login failed for email: invalid-email - invalid credentials or email format
```

---

## Sync State Transition (Initializing → Live)

### Log File (DEBUG level)
```
2026-03-16T22:58:40.100+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | SdkKaliumSyncRuntime: Getting sync status for user: john@example.com
2026-03-16T22:58:40.101+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | User ID qualified: john@example.com, active sessions: 1
2026-03-16T22:58:40.110+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Entering session scope to observe sync state for user: john@example.com
2026-03-16T22:58:40.250+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Sync state observed: SlowSync
2026-03-16T22:58:40.251+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Mapped sync state to status: INITIALIZING
2026-03-16T22:58:40.252+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Calculated sync lag: 5000ms
2026-03-16T22:58:40.253+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Health metrics calculated: lag=5000ms, pending=100, mls=0%
2026-03-16T22:58:40.254+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | Sync status retrieved successfully: status=INITIALIZING, lag=5000ms

# ... later ...

2026-03-16T22:59:45.100+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | SdkKaliumSyncRuntime: Getting sync status for user: john@example.com
2026-03-16T22:59:45.250+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Sync state observed: Live
2026-03-16T22:59:45.251+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Mapped sync state to status: READY
2026-03-16T22:59:45.252+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Calculated sync lag: 0ms
2026-03-16T22:59:45.253+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Health metrics calculated: lag=0ms, pending=0, mls=100%
2026-03-16T22:59:45.254+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | Sync status retrieved successfully: status=READY, lag=0ms
```

---

## Error with Stack Trace

### Console Output (INFO level)
```
[ERROR] Failed to get sync status for user: john@example.com
```

### Log File (DEBUG level)
```
2026-03-16T22:59:50.100+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | SdkKaliumSyncRuntime: Getting sync status for user: john@example.com
2026-03-16T22:59:50.101+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | User ID qualified: john@example.com, active sessions: 1
2026-03-16T22:59:50.110+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Entering session scope to observe sync state for user: john@example.com
2026-03-16T22:59:50.250+01:00 | ERROR | wirecli.sync.SdkKaliumSyncRuntime | main | Failed to get sync status for user: john@example.com
2026-03-16T22:59:50.251+01:00 | ERROR | wirecli.sync.SdkKaliumSyncRuntime | main | java.net.SocketTimeoutException: timeout
    at java.net.SocketInputStream.socketRead0(Native Method)
    at java.net.SocketInputStream.socketRead(SocketInputStream.java:116)
    at java.net.SocketInputStream.read(SocketInputStream.java:171)
    at java.net.SocketInputStream.read(SocketInputStream.java:141)
    at java.net.BufferedInputStream.fill(BufferedInputStream.java:246)
    at java.net.BufferedInputStream.read1(BufferedInputStream.java:286)
    at java.net.BufferedInputStream.read(BufferedInputStream.java:345)
    at sun.net.www.http.HttpClient.parseHTTPHeader(HttpClient.java:735)
    at sun.net.www.http.HttpClient.parseHTTP(HttpClient.java:678)
    at sun.net.www.protocol.http.HttpURLConnection.getInputStream0(HttpURLConnection.java:1587)
    at sun.net.www.protocol.http.HttpURLConnection.getInputStream(HttpURLConnection.java:1492)
    at com.wire.kalium.network.api.NetworkClient.execute(NetworkClient.kt:45)
    at com.wire.kalium.logic.feature.sync.ObserveSyncStateUseCase.invoke(ObserveSyncStateUseCase.kt:23)
    at wirecli.sync.SdkKaliumSyncRuntime$getSyncStatus$2.invokeSuspend(RealKaliumSyncApiClient.kt:125)
    at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
```

---

## Shutdown Sequence

### Log File (DEBUG level)
```
2026-03-16T23:00:00.000+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | SdkKaliumSyncRuntime: Shutting down sync runtime
2026-03-16T23:00:00.001+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Cancelling 2 active session scopes
2026-03-16T23:00:00.002+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Cancelling session scope for user: john@example.com
2026-03-16T23:00:00.003+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Cancelling session scope for user: jane@example.com
2026-03-16T23:00:00.010+01:00 | DEBUG | wirecli.sync.SdkKaliumSyncRuntime | main | Cancelling global scope
2026-03-16T23:00:00.011+01:00 | INFO  | wirecli.sync.SdkKaliumSyncRuntime | main | Sync runtime shutdown complete

2026-03-16T23:00:00.100+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | SdkKaliumAuthRuntime: Shutting down auth runtime
2026-03-16T23:00:00.101+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Cancelling 1 active session scopes
2026-03-16T23:00:00.102+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Cancelling session scope for user: john@example.com
2026-03-16T23:00:00.110+01:00 | DEBUG | wirecli.auth.SdkKaliumAuthRuntime | main | Cancelling global scope
2026-03-16T23:00:00.111+01:00 | INFO  | wirecli.auth.SdkKaliumAuthRuntime | main | Auth runtime shutdown complete
```

---

## Tips for Reading These Logs

1. **Timestamps**: All logs use ISO 8601 format with timezone
2. **Log Levels**: DEBUG shows detailed flow, INFO shows important milestones
3. **Logger Names**: Indicate which module is logging (e.g., `wirecli.sync.*`)
4. **Threads**: Most operations run on `main` thread in CLI context
5. **Progression**: Follow the flow from initialization → operation → result
6. **Errors**: Include full stack traces for debugging
7. **Metrics**: Lag, pending messages, and error rates help identify performance issues

---

## Searching Logs

```bash
# Find all errors
grep "ERROR" ~/.cache/wire-cli/logs/wire-cli.log

# Find sync-related logs
grep "sync" ~/.cache/wire-cli/logs/wire-cli.log -i

# Find network connectivity checks
grep "network connectivity check" ~/.cache/wire-cli/logs/wire-cli.log

# Find authentication flows
grep "auth scope" ~/.cache/wire-cli/logs/wire-cli.log

# Context around an error (5 lines before and after)
grep -B 5 -A 5 "ERROR" ~/.cache/wire-cli/logs/wire-cli.log
```

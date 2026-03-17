# Logging Guide

This document explains how to use and configure logging in wire-cli for debugging connection and sync issues.

## Overview

wire-cli uses [SLF4J](https://www.slf4j.org/) with [Logback](http://logback.qos.ch/) for logging. Logs are output to both the console (stderr) and a rotating log file for detailed debugging.

## Log Locations

### Console Output
- **Destination**: `stderr` (to avoid interfering with command output)
- **Default Level**: `INFO`
- **Format**: `[LEVEL] message`

### Log Files
- **Location**: `~/.cache/wire-cli/logs/wire-cli.log`
- **Archives**: `~/.cache/wire-cli/logs/archive/wire-cli-YYYY-MM-DD.N.log.gz`
- **Default Level**: `INFO`
- **Format**: `timestamp | level | logger | thread | message`

## Configuring Logging

You can configure logging using just two options: **log level** and **log path**.

### Setting Log Level

The log level controls the verbosity of both console and file output. Available levels (from least to most verbose): `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`.

#### Using Command-Line Options

```bash
# Set log level explicitly
wire-cli --log-level DEBUG sync status

# Enable debug mode (shorthand for --log-level DEBUG)
wire-cli --verbose sync status
wire-cli -v sync status
```

#### Using Environment Variables

```bash
# Set log level globally
export WIRECLI_LOG_LEVEL=DEBUG

# Then run any command
wire-cli sync status
```

### Setting Log Path

Customize where logs are stored using the `--log-dir` option or `WIRECLI_LOG_DIR` environment variable.

#### Using Command-Line Options

```bash
# Custom log directory
wire-cli --log-dir /path/to/custom/logs sync status
```

#### Using Environment Variables

```bash
# Set log directory globally
export WIRECLI_LOG_DIR=/path/to/custom/logs

# Then run any command
wire-cli sync status
```

## Common Debugging Scenarios

### Debugging Connection Issues

```bash
# Enable debug logging
wire-cli --log-level DEBUG login --email user@example.com

# Or using environment variable
export WIRECLI_LOG_LEVEL=DEBUG
wire-cli login --email user@example.com
```

Look for:
- Network connectivity checks
- DNS resolution attempts
- Authentication flow progress
- Connection establishment details

### Debugging Sync Problems

```bash
# Enable debug logging
wire-cli --log-level DEBUG sync status
wire-cli --log-level DEBUG sync diagnostics

# Or with --verbose shorthand
wire-cli --verbose sync status
```

Look for:
- Sync state observations
- Lag calculations
- Pending message counts
- Network metrics during sync

### Maximum Debugging Output

```bash
# Enable TRACE level for maximum detail
export WIRECLI_LOG_LEVEL=TRACE
wire-cli sync diagnostics
```

### Minimal Logging (Production)

```bash
# Only show errors and warnings
export WIRECLI_LOG_LEVEL=ERROR

# Or use --log-level
wire-cli --log-level ERROR sync status
```

## Log File Management

### Viewing Current Logs

```bash
# View current log file
tail -f ~/.cache/wire-cli/logs/wire-cli.log

# View last 100 lines
tail -n 100 ~/.cache/wire-cli/logs/wire-cli.log

# Search for errors
grep "ERROR" ~/.cache/wire-cli/logs/wire-cli.log

# Search for sync-related messages
grep "sync" ~/.cache/wire-cli/logs/wire-cli.log -i

# Search for profile-related messages
grep "profile" ~/.cache/wire-cli/logs/wire-cli.log -i

# Search for presence-related messages
grep "presence" ~/.cache/wire-cli/logs/wire-cli.log -i
```

### Archived Logs

Logs are automatically rotated when:
- File size reaches 50MB
- Date changes (daily rotation)

Archived logs are compressed (`.gz`) and kept for 14 days with a maximum total size of 1GB.

```bash
# List archived logs
ls -lh ~/.cache/wire-cli/logs/archive/

# View a specific archived log
zcat ~/.cache/wire-cli/logs/archive/wire-cli-2026-03-15.1.log.gz

# Search across all archived logs
zcat ~/.cache/wire-cli/logs/archive/*.log.gz | grep "ERROR"
```

### Custom Log Location

```bash
# Set a custom log directory
export WIRECLI_LOG_DIR=~/my-logs
mkdir -p ~/my-logs

# Run commands - logs will go to the custom location
wire-cli --verbose sync status

# Check your custom log file
tail -f ~/my-logs/wire-cli.log
```

## Understanding Log Messages

### Profile Service Logs

```
[INFO] Profile command started
[DEBUG] SessionBackedProfileService: Retrieving current profile
[DEBUG] RealKaliumProfileApiClient: Fetching profile for user: user@example.com
[DEBUG] SdkKaliumProfileRuntime: Initializing Kalium CoreLogic for profile runtime
[DEBUG] SdkKaliumProfileRuntime: Kalium data path: /home/user/.wire/kalium
[DEBUG] SdkKaliumProfileRuntime: Kalium configs loaded for mode: CLI
[DEBUG] Profile session scope resolved successfully for user: user@example.com
[DEBUG] Fetching self user data for: user@example.com
[DEBUG] Self user data retrieved successfully: name=John Doe, handle=johndoe
[INFO] Successfully retrieved profile: name=John Doe, handle=johndoe
```

### Presence Service Logs

```
[INFO] Presence command started (get)
[DEBUG] SessionBackedPresenceService: Retrieving current presence
[DEBUG] RealKaliumPresenceApiClient: Fetching presence for user: user@example.com
[DEBUG] SdkKaliumPresenceRuntime: Initializing Kalium CoreLogic for presence runtime
[DEBUG] SdkKaliumPresenceRuntime: Kalium data path: /home/user/.wire/kalium
[DEBUG] Presence session scope resolved successfully for user: user@example.com
[DEBUG] Fetching self availability status for: user@example.com
[DEBUG] Self availability status retrieved successfully: AVAILABLE
[INFO] Presence retrieved successfully: online

[INFO] Presence set command started: status=busy
[INFO] Updating presence to: BUSY for user: user@example.com
[DEBUG] Setting self availability status to: BUSY for: user@example.com
[DEBUG] Self availability status updated successfully
[INFO] Presence updated successfully: busy
```

### Sync Service Logs

```
[INFO] SdkKaliumSyncRuntime: Getting sync status for user: user@example.com
[DEBUG] User ID qualified: user@example.com, active sessions: 1
[DEBUG] Sync state observed: Live
[INFO] Sync status retrieved successfully: status=READY, lag=0ms
```

### Authentication Logs

```
[INFO] SdkKaliumAuthRuntime: Resolving auth scope for server: staging
[DEBUG] Resolving server configuration links
[DEBUG] Server links resolved successfully - creating auth scope
[DEBUG] Auth scope created successfully
```

### Network Connectivity Logs

```
[DEBUG] Starting network connectivity check
[DEBUG] Network connection status: connected=true
[DEBUG] Detected network type: WIFI
[DEBUG] Estimated network latency: 45ms
[INFO] Network connectivity check completed: connected=true, type=WIFI, latency=45ms
```

### Error Logs

```
[ERROR] Failed to get sync status for user: user@example.com
[ERROR] java.net.SocketTimeoutException: timeout

[WARN] Failed to retrieve profile: Profile fetch failed: network is unreachable. Check your connection and retry.

[WARN] Failed to set presence: Presence update could not be completed. Retry later or check server settings.
```

## Troubleshooting Tips

### Issue: No logs appearing

1. Check log directory permissions:
   ```bash
   ls -la ~/.cache/wire-cli/logs/
   ```

2. Verify log level settings:
   ```bash
   env | grep WIRECLI_LOG_LEVEL
   ```

3. Check if log file is being written:
   ```bash
   ls -lh ~/.cache/wire-cli/logs/wire-cli.log
   ```

4. Try a custom log directory with write permissions:
   ```bash
   export WIRECLI_LOG_DIR=/tmp/wire-cli-logs
   mkdir -p /tmp/wire-cli-logs
   wire-cli --verbose sync status
   tail -f /tmp/wire-cli-logs/wire-cli.log
   ```

### Issue: Too many logs

1. Reduce log level to ERROR or WARN:
   ```bash
   export WIRECLI_LOG_LEVEL=ERROR
   ```

2. Or use --log-level option:
   ```bash
   wire-cli --log-level WARN sync status
   ```

### Issue: Logs not helpful for debugging

1. Enable TRACE level for maximum detail:
   ```bash
   export WIRECLI_LOG_LEVEL=TRACE
   ```

2. Check for warnings that might indicate underlying issues:
   ```bash
   grep "WARN" ~/.cache/wire-cli/logs/wire-cli.log
   ```

## Best Practices

1. **Use file logs for debugging**: Console logs are limited, while file logs capture full DEBUG/TRACE output.

2. **Adjust levels incrementally**: Start with DEBUG, only use TRACE when necessary as it's very verbose.

3. **Search logs effectively**: Use grep with context to understand issues:
   ```bash
   grep -B 5 -A 5 "ERROR" ~/.cache/wire-cli/logs/wire-cli.log
   ```

4. **Preserve logs for issues**: Before reporting a bug, save relevant log portions:
   ```bash
   cp ~/.cache/wire-cli/logs/wire-cli.log ~/wire-cli-issue-$(date +%Y%m%d-%H%M%S).log
   ```

5. **Clean up old logs**: If disk space is a concern, manually clean archives:
   ```bash
   rm ~/.cache/wire-cli/logs/archive/wire-cli-*.log.gz
   ```

6. **Use custom log directories for testing**: When debugging specific issues, use a dedicated log directory:
   ```bash
   export WIRECLI_LOG_DIR=~/debug-logs/$(date +%Y%m%d-%H%M%S)
   mkdir -p "$WIRECLI_LOG_DIR"
   wire-cli --verbose sync status
   ```

## Configuration Summary

| Option | Default | Description |
|--------|---------|-------------|
| `--log-level` | `INFO` | Set logging level (ERROR, WARN, INFO, DEBUG, TRACE) |
| `--log-dir` | `~/.cache/wire-cli/logs` | Custom log directory path |
| `--verbose`, `-v` | - | Shorthand for `--log-level DEBUG` |
| `WIRECLI_LOG_LEVEL` | `INFO` | Environment variable for log level |
| `WIRECLI_LOG_DIR` | `~/.cache/wire-cli/logs` | Environment variable for log directory |

## Migration from Old Configuration

If you were previously using module-specific log level variables (`WIRE_SYNC_LOG_LEVEL`, `WIRE_AUTH_LOG_LEVEL`, etc.), they are no longer supported. Instead, use a single log level:

```bash
# Old way (no longer supported)
export WIRE_SYNC_LOG_LEVEL=DEBUG
export WIRE_AUTH_LOG_LEVEL=DEBUG

# New way
export WIRECLI_LOG_LEVEL=DEBUG
```

The single `WIRECLI_LOG_LEVEL` now applies to all logging (console, file, and all modules).

## Additional Resources

- [Logback Documentation](http://logback.qos.ch/documentation.html)
- [SLF4J Manual](http://www.slf4j.org/manual.html)
- [Log Levels Best Practices](https://logging.apache.org/log4j/2.x/manual/customloglevels.html)

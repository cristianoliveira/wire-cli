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
- **Default Level**: `DEBUG`
- **Format**: `timestamp | level | logger | thread | message`

## Configuring Log Levels

You can configure log levels using environment variables for fine-grained control.

### Global Log Level Controls

```bash
# Console log level (default: INFO)
export WIRE_CONSOLE_LOG_LEVEL=DEBUG    # Show DEBUG messages in console
export WIRE_CONSOLE_LOG_LEVEL=ERROR    # Only show errors in console

# File log level (default: DEBUG)
export WIRE_FILE_LOG_LEVEL=TRACE       # Maximum detail in log file
export WIRE_FILE_LOG_LEVEL=INFO       # Less verbose log file

# Legacy fallback (affects both console and file)
export wire.log.level=DEBUG
```

### Module-Specific Log Levels

```bash
# Sync operations (default: DEBUG)
export WIRE_SYNC_LOG_LEVEL=DEBUG       # Detailed sync state changes
export WIRE_SYNC_LOG_LEVEL=TRACE       # Maximum sync debugging

# Authentication operations (default: DEBUG)
export WIRE_AUTH_LOG_LEVEL=DEBUG       # Detailed auth flow logging
export WIRE_AUTH_LOG_LEVEL=INFO        # Less verbose auth logging

# Network connectivity (default: DEBUG)
export WIRE_NETWORK_LOG_LEVEL=DEBUG    # Network check details
export WIRE_NETWORK_LOG_LEVEL=INFO     # Basic network status

# Kalium SDK integration (default: INFO)
export WIRE_KALIUM_LOG_LEVEL=DEBUG    # Verbose SDK internals
export WIRE_KALIUM_LOG_LEVEL=TRACE     # Maximum SDK debugging
```

## Common Debugging Scenarios

### Debugging Connection Issues

```bash
# Enable detailed network and auth logging
export WIRE_NETWORK_LOG_LEVEL=DEBUG
export WIRE_AUTH_LOG_LEVEL=DEBUG
export WIRE_CONSOLE_LOG_LEVEL=DEBUG

wire-cli login --email user@example.com
```

Look for:
- Network connectivity checks
- DNS resolution attempts
- Authentication flow progress
- Connection establishment details

### Debugging Sync Problems

```bash
# Enable detailed sync logging
export WIRE_SYNC_LOG_LEVEL=DEBUG
export WIRE_CONSOLE_LOG_LEVEL=DEBUG

wire-cli sync status
wire-cli sync diagnostics
```

Look for:
- Sync state observations
- Lag calculations
- Pending message counts
- Network metrics during sync

### Maximum Debugging Output

```bash
# Enable TRACE level for everything
export WIRE_CONSOLE_LOG_LEVEL=TRACE
export WIRE_FILE_LOG_LEVEL=TRACE
export WIRE_SYNC_LOG_LEVEL=TRACE
export WIRE_AUTH_LOG_LEVEL=TRACE
export WIRE_NETWORK_LOG_LEVEL=TRACE
export WIRE_KALIUM_LOG_LEVEL=TRACE

wire-cli sync diagnostics
```

### Minimal Logging (Production)

```bash
# Only show errors in console
export WIRE_CONSOLE_LOG_LEVEL=ERROR
# Keep DEBUG in log file for troubleshooting
export WIRE_FILE_LOG_LEVEL=DEBUG

wire-cli sync status
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

## Understanding Log Messages

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
```

## Troubleshooting Tips

### Issue: No logs appearing

1. Check log directory permissions:
   ```bash
   ls -la ~/.cache/wire-cli/logs/
   ```

2. Verify log level settings:
   ```bash
   env | grep WIRE_
   ```

3. Check if log file is being written:
   ```bash
   ls -lh ~/.cache/wire-cli/logs/wire-cli.log
   ```

### Issue: Too many logs

1. Reduce console log level:
   ```bash
   export WIRE_CONSOLE_LOG_LEVEL=ERROR
   ```

2. Reduce file log level:
   ```bash
   export WIRE_FILE_LOG_LEVEL=INFO
   ```

3. Disable specific modules:
   ```bash
   export WIRE_KALIUM_LOG_LEVEL=WARN
   ```

### Issue: Logs not helpful for debugging

1. Enable TRACE level for specific modules:
   ```bash
   export WIRE_SYNC_LOG_LEVEL=TRACE
   export WIRE_NETWORK_LOG_LEVEL=TRACE
   ```

2. Enable Kalium SDK logging:
   ```bash
   export WIRE_KALIUM_LOG_LEVEL=DEBUG
   ```

3. Check for warnings that might indicate underlying issues:
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

## Environment Variables Quick Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `WIRE_CONSOLE_LOG_LEVEL` | `INFO` | Console output level (ERROR, WARN, INFO, DEBUG, TRACE) |
| `WIRE_FILE_LOG_LEVEL` | `DEBUG` | Log file output level |
| `WIRE_SYNC_LOG_LEVEL` | `DEBUG` | Sync operations logging |
| `WIRE_AUTH_LOG_LEVEL` | `DEBUG` | Authentication operations logging |
| `WIRE_NETWORK_LOG_LEVEL` | `DEBUG` | Network connectivity checks logging |
| `WIRE_KALIUM_LOG_LEVEL` | `INFO` | Kalium SDK integration logging |
| `wire.log.level` | `INFO` | Legacy fallback (affects both) |

## Additional Resources

- [Logback Documentation](http://logback.qos.ch/documentation.html)
- [SLF4J Manual](http://www.slf4j.org/manual.html)
- [Log Levels Best Practices](https://logging.apache.org/log4j/2.x/manual/customloglevels.html)

# wire-cli

`wire-cli` is a Kotlin command-line client for the Wire ecosystem, focused on fast account and presence workflows for local development and real backend integration.

## Quickstart

Build and install locally:

```bash
gradle installDist
```

Run help:

```bash
./build/install/wire-cli/bin/wire-cli --help
```

Optional convenience alias used in examples below:

```bash
alias wire="./build/install/wire-cli/bin/wire-cli"
wire --help
```

## Core Commands

```bash
# Login (prompts for password securely)
wire login --email jane@example.com

# Logout
wire logout

# Profile (includes: Presence: ...)
wire profile

# Read presence
wire presence get

# Update presence
wire presence set online
wire presence set busy
wire presence set away
wire presence set offline
```

Presence values are normalized; unsupported or unavailable backend values are surfaced as `unknown`.

## Backend Modes

- Default mode: real Wire backend
- Deterministic stub/fake mode: set `WIRE_BACKEND=stub`

Example:

```bash
wire login --email jane@example.com
wire profile
wire presence get

WIRE_BACKEND=stub wire login --email jane@example.com
WIRE_BACKEND=stub wire profile
WIRE_BACKEND=stub wire presence get
```

## Kalium Lifecycle And CLI Mode Flags

- `wire-cli` always attempts deterministic shutdown on process exit by closing runtime scopes in `main`.
- Kalium currently does not expose a single `CoreLogic.close()` API; this CLI uses a local lifecycle boundary that cancels active session scopes and the global scope.

Optional CLI mode flags for real backend commands:

- `WIRE_KALIUM_ENABLE_CALLING=true` enables calling support (disabled by default for CLI safety).
- `WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT=true` skips `waitUntilLiveOrFailure()` warmup for profile/presence commands.
- `WIRE_KALIUM_DISABLE_MLS_MIGRATION_SCHEDULER=true` pushes MLS migration cadence to a long interval as a local scheduler-suppression workaround.

When these flags are unset, default behavior remains unchanged.

## Testing

Run the full verification suite (recommended):

```bash
gradle check
```

Run Bats integration tests only:

```bash
gradle batsTest
```

## Security Note

Avoid passing secrets directly on the command line when possible, because shell history and process lists can expose credentials.

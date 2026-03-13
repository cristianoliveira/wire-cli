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

- Default mode: local/dev stub backend (no extra setup required)
- Real backend mode: set `WIRE_BACKEND=real`

Example:

```bash
WIRE_BACKEND=real wire login --email jane@example.com
WIRE_BACKEND=real wire profile
WIRE_BACKEND=real wire presence get
```

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

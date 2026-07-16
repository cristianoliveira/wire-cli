# wire-cli

`wire-cli` is a Kotlin command-line client for the Wire ecosystem, focused on fast account and presence workflows for local development and real backend integration.

## Quickstart

Build and install locally:

```bash
nix develop  # Enter Nix dev shell (recommended)
gradle installDist
```

Run help:

```bash
./build/install/wire/bin/wire --help
```

Optional convenience alias used in examples below:

```bash
alias wire="./build/install/wire/bin/wire"
wire --help
```

## Building with Nix

This project supports reproducible builds with Nix flakes.

### Quick Start

```bash
# Build the project
nix build .

# Run the CLI directly
nix run .

# Development environment
nix develop
```

### Documentation

For detailed instructions on:
- Upgrading dependencies
- Troubleshooting build issues
- Regenerating verification-metadata.xml

See [docs/NIX_BUILD.md](docs/NIX_BUILD.md)

## Core Commands

```bash
# Login (prompts for password securely)
wire login --email jane@example.com

# Logout
wire logout

# Profile (includes: Presence: ...)
wire profile

# Change profile display name
wire profile name "Jane Doe"

# Read presence
wire presence get

# Update presence
wire presence set online
wire presence set busy
wire presence set away
wire presence set offline

# Keep message sync active and cache messages locally
wire daemon

# Read cached messages without waiting for network sync
wire message fetch --local <conversation-id>

# Restore a Wire client backup into the authenticated user's local cache
wire backup import wire-backup.wbu

# Export a backup as JSON Lines for analysis
wire backup export --format jsonl --destination ./analysis wire-backup.wbu
```

`wire daemon` runs in the foreground until interrupted. Use systemd, launchd, Docker, or another process supervisor to keep it running. Kalium stores synchronized state under `~/.wire/kalium`.

`wire backup import` requires an active login and restores backup conversations, messages, users, and reactions into Kalium's local cache. Imported messages can then be read with `wire message fetch --local <conversation-id>`. Wire backup is the default source format; use `--from` only to override source selection.

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
- `WIRECLI_MESSAGE_SEND_TIMEOUT_MS=<milliseconds>` configures message send timeout (default `60000`, max `300000`).

When these flags are unset, default behavior remains unchanged.

Example:

```bash
WIRECLI_MESSAGE_SEND_TIMEOUT_MS=120000 wire message send --conversation-id <conversation-id> --text "hello"
```

## Development

### Quality Checks

```bash
# Run all checks (format, lint, test)
make all

# Individual checks
make format-check  # ktlint
make lint          # detekt
make test          # unit + integration tests
```

### Pre-commit Hooks

Install pre-commit hooks to automatically run checks before pushing:

```bash
pre-commit install
pre-commit install --hook-type pre-push
```

### Continuous Integration

The project uses GitHub Actions for CI with Nix for reproducible builds. See [docs/CI.md](docs/CI.md) for details.

## Testing

Run the full verification suite (recommended):

```bash
gradle check
```

Run Bats integration tests only:

```bash
gradle batsTest
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to contribute to the project.

## Security Note

Avoid passing secrets directly on the command line when possible, because shell history and process lists can expose credentials.

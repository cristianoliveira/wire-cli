# wire

`wire` is a Kotlin command-line client for the Wire ecosystem, focused on fast account and presence workflows for local development and real backend integration.

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

# Read cached messages while daemon is active
wire message fetch <conversation-id>

# Bypass daemon-backed cache and fetch from Wire
wire message fetch --no-cache <conversation-id>

# Mark conversation as read through a message
wire message set <conversation-id> --read <message-id>

# Restore a Wire client backup into the authenticated user's local cache
wire backup import wire-backup.wbu

# Export a backup as JSON Lines for analysis
wire backup export --format jsonl --destination ./analysis wire-backup.wbu
```

### Multiple Accounts

`wire` stores multiple authenticated accounts locally and lets you switch the
active one without logging out. The active account is an explicit pointer (like
kubectl `current-context`); every command runs against it.

```bash
# Add accounts (each login stores and activates that account)
wire login --email jane@example.com   # personal
wire login --email jane@company.com   # work

# List stored accounts; the active one is marked with *
wire account list

# Show the currently active account
wire whoami

# Switch the active account (local only, no re-authentication)
wire account use jane@company.com

# Remove a single stored account (local only; use `wire logout` for server logout)
wire account remove jane@example.com
```

`wire logout` removes only the **active** account and clears the active pointer;
run `wire account use <user-id>` to select another. Accounts are stored in the
session file (`~/.config/wire/session` by default; override with
`WIRE_SESSION_FILE`).

`wire daemon` runs in the foreground until interrupted. Use systemd, launchd, Docker, or another process supervisor to keep it running. Kalium stores synchronized state under `~/.wire/kalium`.

`wire backup import` requires an active login and restores backup conversations, messages, users, and reactions into Kalium's local cache. Imported messages can then be read with `wire message fetch <conversation-id>`. Wire backup is the default source format; use `--from` only to override source selection.

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

- `wire` always attempts deterministic shutdown on process exit by closing runtime scopes in `main`.
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

## Access policy

Wire CLI reads optional configuration from `$XDG_CONFIG_HOME/wire/config.yaml`, or
`~/.config/wire/config.yaml` when `XDG_CONFIG_HOME` is unset. Set
`WIRECLI_CONFIG_FILE` to use another path.

Access control is disabled when the file or `access.enabled` is absent. When enabled,
every capability is denied unless listed under `allow`:

```yaml
access:
  enabled: true
  allow:
    - read
    - auth.login
```

`read` grants all read-only operations. A domain such as `message` grants all operations
in that domain. Narrow capabilities such as `message.read` and `message.send` grant only
that operation. Unknown and future capabilities remain denied.

See [`examples/config-read-only.yaml`](examples/config-read-only.yaml),
[`examples/config-message-reader.yaml`](examples/config-message-reader.yaml), and
[`examples/config-message-bot.yaml`](examples/config-message-bot.yaml).

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

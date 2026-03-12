# wire-cli

Minimal Kotlin CLI project using [Clikt](https://github.com/ajalt/clikt) and a Nix flake dev shell.

## Requirements

- Nix with flakes enabled

## Getting started

```bash
nix develop
gradle run
```

Run with a custom name:

```bash
gradle run --args='--name Kotlin'
```

Show CLI help:

```bash
gradle run --args='--help'
```

## Command usage

Login and persist a local session:

```bash
gradle run --args='login --email jane@example.com --password correct-horse'
```

Show current profile (requires an active session):

```bash
gradle run --args='profile'
```

Logout and clear local session:

```bash
gradle run --args='logout'
```

If `profile` or `logout` is run without a valid session, the CLI returns exit code `11` and prints re-login guidance.

## Login with real Wire account

Use the helper script to log into a real Wire server:

```bash
./scripts/login-real-wire.sh
```

**Required environment variables:**
- `WIRE_EMAIL` - Your Wire account email
- `WIRE_PASSWORD` - Your Wire account password

**Optional environment variables:**
- `WIRE_SERVER` - Custom Wire server URL (defaults to production)
- `WIRE_BIN` - Path to wire-cli binary (defaults to `./build/install/wire-cli/bin/wire-cli`)

Set credentials in `.env` or export them directly:

```bash
export WIRE_EMAIL="your@email.com"
export WIRE_PASSWORD="your-password"
export WIRE_BIN="/custom/path/to/wire-cli"
./scripts/login-real-wire.sh
```

## Bash integration tests (Bats)

Run bash-based integration tests:

```bash
gradle batsTest
```

Or run through the generic verification lifecycle:

```bash
gradle check
```

Test files live under `test/bats/` and are written in BDD style.

## Quality checks

Quick local guardrails:

```bash
make all
```

Individual commands:

```bash
make format
make format-check
make lint
make test
```

Optional git hooks via pre-commit:

```bash
pre-commit install --hook-type pre-commit --hook-type pre-push
pre-commit run --all-files
```

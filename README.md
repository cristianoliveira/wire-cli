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

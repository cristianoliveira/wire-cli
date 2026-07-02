# Agent Instructions

`wire-cli` is a Kotlin JVM command-line client for Wire. Keep this file stable and action-oriented for future agents.

## Project shape

- Entry point: `src/main/kotlin/wirecli/Main.kt`.
- Composition root: `src/main/kotlin/wirecli/runtime/KaliumRuntime.kt`.
- CLI surface: `src/main/kotlin/wirecli/commands/` using Clikt.
- Feature modules live under `src/main/kotlin/wirecli/{auth,profile,presence,device,conversation,message,sync}`.
- Main module guidance: `src/main/kotlin/wirecli/AGENTS.md`.
- Tests mirror feature modules under `src/test/kotlin/wirecli/`.
- Bats integration tests live under `test/bats/` and run against installed CLI.
- `vendor/kalium/` is vendored Wire SDK code. Do not change it unless the task explicitly targets Kalium.
- `.local/wiretui/` is a reference TUI implementation for understanding Wire server interaction. Do not add it as a production dependency.

## Architecture rules

- `Main.kt` only configures process concerns, builds runtime, registers commands, and shuts down.
- `runtime/KaliumRuntime.kt` owns dependency wiring and backend selection.
- Commands should parse/format CLI input/output and delegate to services. Keep business logic out of commands.
- Feature modules follow this pattern:
  - `*Contracts.kt`: domain models, result types, interfaces, exit codes.
  - `SessionBacked*Service`: loads active auth session and calls API client.
  - `AuthGuarded*Service`: converts missing/invalid auth into feature-level failures.
  - `RealKalium*ApiClient`: adapts Kalium SDK.
  - `Stub*ApiClient`: deterministic fake for `WIRE_BACKEND=stub` and tests.
- Prefer explicit dependency injection. Composition roots own wiring; feature code should receive configured clients.
- Keep dependency direction inward: commands -> services -> API clients/adapters. Avoid feature-to-command imports.
- There are no compatibility obligations before v1.0; prefer simple breaking cleanup over deprecated paths.

## Testing and verification

- Use TDD for code changes: write/adjust tests first, then implementation.
- Add unit tests near affected feature module under `src/test/kotlin/wirecli/`.
- Add or update Bats tests when CLI behavior, flags, output format, or exit codes change.
- Use deterministic stubs for tests. Do not hit real Wire backend from unit tests.
- Verification commands:
  - `make test-unit` for quick unit feedback.
  - `make test` for unit + Bats tests.
  - `make all` for format, lint, and tests.
- Gradle wrapper may be run directly with `./gradlew --no-daemon ...`; Makefile falls back to `nix develop` when Java is unavailable.

## CLI behavior rules

- Keep stdout script-friendly. Console logs are off by default and go to stderr when enabled.
- JSON modes (`--json`, `--json-lines`) must keep stdout machine-readable.
- Logging controls:
  - `--verbose` enables DEBUG console logs.
  - `--log-level <LEVEL>` sets console log level.
  - `WIRECLI_CONSOLE_LOG_LEVEL=<LEVEL>` sets console log level.
  - `WIRECLI_LOG_LEVEL=<LEVEL>` sets file/root logging level.
- Logs are written under `~/.cache/wire-cli/logs`.
- `WIRECLI_MESSAGE_SEND_TIMEOUT_MS` configures message send timeout; invalid or non-positive values must fall back safely.

## Development workflow

- Prefer `fd` over `find`, `rg` over `grep`, and AST/LSP tools before full file reads.
- Keep changes small and verified.
- Do not duplicate contracts or schemas. Import and extend the single source of truth.
- Name concepts by user-facing Wire domain language, not implementation details.
- Use early returns and clear result types for error paths.

## Session completion

When a work session changes code:

1. Create issues for known follow-up work when appropriate.
2. Run relevant quality gates.
3. Commit changes when asked to complete the work.
4. Pull with rebase, push, and verify branch is up to date with origin.
5. Hand off remaining context clearly.

Never say work is complete if local commits were not pushed when the task requires landing changes.

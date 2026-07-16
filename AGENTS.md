# Agent Instructions

`wire-cli` is a Kotlin/JVM command-line client for Wire, built on Clikt and the vendored Kalium SDK.

## Guidance map

Read the nearest guide before changing code:

- `src/main/kotlin/wirecli/AGENTS.md` — production architecture and feature ownership.
- `src/main/kotlin/wirecli/commands/AGENTS.md` — CLI parsing, output, and exit contracts.
- `src/main/kotlin/wirecli/runtime/AGENTS.md` — composition root and backend lifecycle.
- `src/test/kotlin/wirecli/AGENTS.md` — Kotlin unit-test conventions.
- `test/bats/AGENTS.md` — installed-CLI integration tests.
- `vendor/kalium/AGENTS.md` — vendored SDK guidance; do not change Kalium unless explicitly requested.

## Global rules

- Use TDD: add happy- and unhappy-path tests before behavior changes.
- Keep dependency direction `commands -> services -> API clients/adapters`; runtime owns concrete wiring.
- Keep stdout script-friendly. Diagnostics and optional logs belong on stderr.
- Preserve machine-readable JSON modes and stable exit behavior.
- Use deterministic stubs in tests; unit tests must not contact Wire services.
- Do not use `.local/wiretui/` as a production dependency; it is reference material only.
- Prefer small, explicit changes. Before v1.0, remove obsolete paths instead of adding compatibility layers.

## Verification

- `make test-unit` — Kotlin tests.
- `make test` — Kotlin and Bats tests.
- `make all` — formatting, lint, and all tests.

The Gradle wrapper may be used directly. The Makefile enters the Nix environment when Java is unavailable.

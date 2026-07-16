# Bats Integration Test Guidance

These tests exercise installed `wire-cli` through `WIRE_BIN`; they verify public CLI contract rather than Kotlin internals.

- Use helpers from `helpers/common.bash` instead of duplicating setup.
- Default to `WIRE_BACKEND=stub` and isolated temporary state.
- Never require network access, real credentials, or existing user configuration.
- Assert command syntax, stdout, stderr, exit status, and JSON validity where relevant.
- Keep stdout assertions strict enough to protect scripting contracts; logs must not pollute JSON output.
- Name story files by user-facing feature and keep setup/cleanup deterministic.
- Bound commands that can watch, daemonize, sync, or wait.

Run all integration tests with `make test`, or target a Bats path/filter through Gradle properties supported by `build.gradle.kts`.

# Command Guidance

This package is the Clikt adapter layer.

## Responsibilities

- Define command names, arguments, options, help, and subcommand grouping.
- Convert CLI input into feature contract values.
- Delegate to injected service interfaces.
- Render results to stdout/stderr and map failures to exit behavior.
- Keep JSON and JSON-lines output machine-readable.

## Boundaries

- Do not import Kalium SDK types.
- Do not construct concrete services or clients; `runtime/` owns wiring.
- Do not read auth session files directly.
- Do not duplicate validation or business rules already owned by a feature.
- Shared command-only rendering/parsing helpers belong in `CommandUtils.kt`; domain formatters stay with their feature.

Commands may coordinate CLI concerns such as timeout, streaming, cancellation, or logging, but feature outcomes should be represented by explicit result types.

## Tests

- Add command tests under `src/test/kotlin/wirecli/commands/` with fake service implementations.
- Assert arguments/options, service request values, stdout/stderr, JSON shape, and failures.
- Add or update `test/bats/` when installed CLI behavior, flags, output, or exit codes change.

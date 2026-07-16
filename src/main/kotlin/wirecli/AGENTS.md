# Production Code Guidance

This package contains the CLI entrypoint, command adapters, feature modules, and composition root.

## Dependency direction

- `Main.kt` configures process concerns, creates runtime, registers commands, and shuts down.
- `commands/` depends on feature service contracts, not Kalium.
- Feature services depend on local API-client contracts.
- Real adapters depend on Kalium; stubs remain deterministic.
- `runtime/` selects backends and wires concrete implementations.
- `config/` and `validation/` are shared support and should stay dependency-light.

Do not move dependency construction into commands or services. Inject configured clients rather than passing configuration per call.

## Feature shape

Most service-backed features use:

1. `*Contracts.kt` for domain models, result/failure types, exit codes, and interfaces.
2. `SessionBacked*Service` to resolve active session and invoke API client.
3. `AuthGuarded*Service` to translate unavailable auth into feature failures.
4. `RealKalium*ApiClient` or runtime as Kalium boundary.
5. `Stub*ApiClient` for deterministic stub backend and tests.

Use this shape when it solves same session/auth boundary; do not create layers mechanically.

## Local guidance

Every production subpackage has a local `AGENTS.md`. Read nearest guide before editing:

- Boundaries: `auth/`, `profile/`, `presence/`, `device/`, `conversation/`, `user/`, `connection/`, `message/`, `sync/`, `exporting/`, and `importing/`.
- Adapters and wiring: `commands/` and `runtime/`.
- Shared support: `config/` and `validation/`.

Cross-feature dependencies should remain narrow. `exporting` may reuse canonical `ImportSource`; profile may consume presence display contracts. Avoid broader feature cycles.

## Adding behavior

1. Start with tests and contract/result types.
2. Implement service behavior and both success/failure paths.
3. Extend deterministic stub behavior when backend behavior is visible.
4. Add Kalium adapter code only at infrastructure boundary.
5. Wire concrete implementation in `runtime/KaliumRuntime.kt`.
6. Add command and Bats coverage when CLI contract changes.

Keep business rules out of commands, Kalium types out of user-facing contracts, and file/session access inside owning module.

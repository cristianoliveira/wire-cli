# Runtime Guidance

`KaliumRuntime.kt` is the production composition root and backend lifecycle owner.

## Responsibilities

- Select real or stub backend from centralized configuration.
- Construct auth and feature API clients, services, and SDK runtimes.
- Expose configured service interfaces to `Main.kt` and commands.
- Defer expensive real-backend initialization until required.
- Shut down owned resources exactly once.

## Boundaries

- Keep wiring here; commands and feature services must not instantiate dependencies.
- Inject already configured clients. Do not spread environment parsing across features.
- Stub backend must remain deterministic and must not initialize Kalium.
- Real backend owns Kalium runtime creation and cleanup.
- When adding a feature, expose its service through `KaliumRuntime`, wire both supported backends, and test backend selection/lifecycle.

## Tests

Runtime tests live under `src/test/kotlin/wirecli/runtime/`. Cover real-vs-stub selection, invalid configuration fallback/error behavior, deferred initialization, and idempotent shutdown without contacting Wire services.

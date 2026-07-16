# Sync Guidance

Own sync status, diagnostics, force/reset operations, per-conversation checks, health metrics, recovery hints, and sync output.

- Keep raw Kalium inspection inside real adapter/runtime.
- Build diagnostic checks in `SyncCheckBuilder`; calculate metrics separately in `SyncMetricsCalculator`.
- Keep recovery advice in `SyncRecoveryHintBuilder` and rendering in `SyncOutputFormatter`.
- Network checks must be injectable, bounded, and deterministic in tests.
- Session-backed service resolves auth; auth guard maps unavailable auth.
- Avoid making output formatters responsible for health decisions.

Tests belong in `src/test/kotlin/wirecli/sync/`. Cover healthy/degraded/failure paths, metric boundaries, extended and per-conversation diagnostics, timeout/network errors, missing auth, and stable output.

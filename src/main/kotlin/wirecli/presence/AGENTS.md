# Presence Guidance

Own presence reads, updates, normalization, and presence-specific failures.

- Keep supported user-facing states in `PresenceContracts.kt`.
- Translate backend values at real adapter boundary; preserve unsupported values as explicit unknown-like states rather than dropping them.
- Session-backed service resolves auth; auth guard maps unavailable auth.
- Stub client behavior must be stable for command and service tests.
- Do not place profile update behavior here.

Tests belong in `src/test/kotlin/wirecli/presence/`. Cover normalization, unknown backend values, success, missing auth, and API failure paths.

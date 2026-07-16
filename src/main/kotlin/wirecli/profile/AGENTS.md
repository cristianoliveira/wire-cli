# Profile Guidance

Own current-user profile reads, updates, results, and failure mapping.

- Commands depend on `ProfileService`; service implementations own session resolution.
- Keep Kalium models inside real adapter and translate them to profile contracts.
- Presence may be consumed for profile display, but profile must not own presence mutation rules.
- Missing/invalid auth is translated by auth guard into profile-level failure.
- Keep formatting concerns out of service logic.

Tests belong in `src/test/kotlin/wirecli/profile/`. Cover authenticated success, missing session, API failures, and update validation with deterministic clients.

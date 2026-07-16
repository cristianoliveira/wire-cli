# Connection Guidance

Own connection request, block, and unblock lifecycle plus conflict/result mapping.

- Keep stable user-facing action results and exit codes in `ConnectionContracts.kt`.
- Distinguish conflicts from transport/auth failures explicitly.
- Session-backed service resolves active auth; auth guard maps unavailable auth.
- Translate Kalium IDs, states, and errors inside real adapter.
- User search and detail presentation belong in `user/`.

Tests belong in `src/test/kotlin/wirecli/connection/`. Cover each transition, already-in-state conflicts, missing auth, invalid users, API errors, and deterministic stub state changes.

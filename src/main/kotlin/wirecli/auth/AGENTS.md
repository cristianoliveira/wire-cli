# Authentication Guidance

Own login/logout, session persistence, auth parsing/redaction, and Kalium authentication lifecycle.

- Keep `AuthContracts.kt` as source of truth for auth interfaces, results, sessions, and exit codes.
- Session file access belongs in session-store implementations; other features use `SessionProvider` or auth services.
- Never expose passwords, tokens, cookies, or raw sensitive responses in output or logs.
- Keep response parsing separate from orchestration and SDK adaptation.
- Real Kalium lifecycle belongs at adapter/runtime boundary; stub behavior must be deterministic.

Tests belong in `src/test/kotlin/wirecli/auth/`. Cover malformed responses, unavailable/corrupt session state, redaction, persistence, login success, and login failure without real credentials.

# Authentication Guidance

Own login/logout, multi-account session persistence, auth parsing/redaction, and Kalium authentication lifecycle.

- Keep `AuthContracts.kt` as source of truth for auth interfaces, results, sessions, account inventory, accounts service, and exit codes.
- Session file access belongs in `AuthSessionStore` (`FileAuthSessionStore`); other features use `SessionProvider` (`readActiveSession`) or auth/accounts services.
- Accounts are multi-account: `AuthSessionStore` holds a list of accounts plus an explicit active pointer (`AccountInventory.activeUserId`, like kubectl `current-context`). Adding (`addAccount`) is additive and preserves other accounts; switching (`setActiveAccount`) and removal (`removeAccount`) are local-only and never contact Wire.
- `AccountsService` (`AccountsServiceImpl`) exposes local account management (list/use/remove/current) for commands; it does not perform network logout. Network logout is owned by `AuthSessionService`.
- Session file format is versioned (`wire-cli-session-store:2`). v1/legacy files are read and auto-migrated to v2 on first read; v2 is the only written format.
- Never expose passwords, tokens, cookies, or raw sensitive responses in output or logs.
- Keep response parsing separate from orchestration and SDK adaptation.
- Real Kalium lifecycle belongs at adapter/runtime boundary; stub behavior must be deterministic.

Tests belong in `src/test/kotlin/wirecli/auth/`. Cover malformed responses, unavailable/corrupt session state, redaction, persistence, v1→v2 migration, multi-account add/switch/remove, login success, and login failure without real credentials.

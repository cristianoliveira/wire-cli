# Authentication Guidance

Own login/logout, multi-account session persistence, labels, auth parsing/redaction, and Kalium authentication lifecycle.

- Keep `AuthContracts.kt` as source of truth for auth interfaces, results, sessions, account inventory, account service, and exit codes.
- Session file access belongs in `AuthSessionStore` (`FileAuthSessionStore`); other features use `SessionProvider` (`readActiveSession`) or the auth/account services.
- Accounts are multi-account: `AuthSessionStore` holds a list of `StoredAccount` (credential + optional label) plus an explicit active pointer (`AccountInventory.activeUserId`, like kubectl `current-context`). Adding (`addAccount`) is additive and preserves other accounts; switching (`setActiveAccount`) and removal (`removeAccount`) are local-only and never contact Wire.
- Labels are optional, unique, and set via `wire login --label`. `StoredAccount` carries the label; `AuthSession` stays the pure credential type passed to API clients (`StoredAccount.toAuthSession()` strips the label). `AccountService.useAccount`/`removeAccount` resolve a selector by label first, then userId.
- `AccountService` (`AccountServiceImpl`) exposes local account management (list/use/remove/current) for commands; it does not perform network logout. Network logout is owned by `AuthSessionService`.
- Session file format is versioned (`wire-cli-session-store:3`). v1/v2/legacy files are read and auto-migrated to v3 on first read; v3 is the only written format.
- Never expose passwords, tokens, cookies, or raw sensitive responses in output or logs.
- Keep response parsing separate from orchestration and SDK adaptation.
- Real Kalium lifecycle belongs at adapter/runtime boundary; stub behavior must be deterministic.

Tests belong in `src/test/kotlin/wirecli/auth/`. Cover malformed responses, unavailable/corrupt session state, redaction, persistence, v1/v2→v3 migration, label round-trip and uniqueness, multi-account add/switch/remove, login success, and login failure without real credentials.

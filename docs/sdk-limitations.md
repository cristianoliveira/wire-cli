# Kalium SDK Limitations for `wire-cli`

This document summarizes the current Kalium constraints and gaps that affect `wire-cli` feature rollout.

## Current Support Snapshot

- **Strong support now**: `sessions`, `convo`, `chat`, `contacts`.
- **Partial support**: `auth`, `search`, `client`, `backup`, `server`, `sync`.
- **Missing as first-class SDK surfaces**: `doctor`, generic `config`.

## Key SDK Limitations

1. **No stable CLI-focused public facade yet**
   - External API boundary is still weak/placeholder, so CLI integrations depend on `logic` internals more than ideal.

2. **Internal/delicate API dependency for some useful operations**
   - Some high-value flows (for example always-on sync and key-package refill paths) are not fully exposed as stable public operations.

3. **Fragmented diagnostics and recovery APIs**
   - No consolidated `doctor`-style scope.
   - Sync recovery is available as lower-level pieces, not one cohesive top-level use case.

4. **Server management is compositional, not first-class**
   - Building blocks exist for server config and auth versioning, but no dedicated high-level `server show/select/test` API set.

5. **Backup operability gaps**
   - Create/verify/restore exists, but list/status/id-based restore contracts are limited.

6. **Search contract limitations**
   - User search is strong.
   - Global/message search behavior (especially sorting and robust pagination semantics) needs stronger SDK-level contracts.

## Technical Constraints That Impact Delivery

- **API-version gates matter**: feature availability depends on backend/API version support, so command gating is required.
- **Composite-build coupling**: `wire-cli` tracks local Kalium composite build, which can introduce branch-drift behavior differences.
- **Stub-vs-real runtime gap**: optional stub mode can hide real-backend issues unless real-backend validation is mandatory.
- **Platform limits**: calling/media paths remain riskier on current CLI JVM setup.

## Practical Impact on wire-cli Planning

- Build first on already-strong Kalium surfaces (`sessions`, `contacts`, `client` baseline, `convo`/`chat` core).
- Add capability probing and explicit unsupported-feature messaging before shipping version-sensitive commands.
- Avoid defaulting production commands to internal/delicate APIs.

## Recommended Kalium Backlog (to unlock wire-cli roadmap)

### Near-term

- Define stable public CLI-facing API facade.
- Promote key-package refill and foreground sync lifecycle to stable public APIs.
- Add public server operations (`show/select/test`) and a consolidated `DoctorScope`.

### Mid-term

- Add unified sync recovery API.
- Add backup list/status and id-based restore model.
- Expose one-shot conversation-details-by-id and pending-contact-request read paths.

### Later

- Extend search with explicit sort + stronger pagination contracts.
- Add public config scope for supported runtime settings and paths.

# AXI plan: No-argument live state

## Problem

`wire` with no arguments prints static full help. It is deterministic, but does not tell an agent whether account/session and synchronization are ready or which next command is relevant.

## Proposed behavior

Print compact state under strict latency and token budgets, for example:

```text
wire: authenticated as <handle>
sync: ready
next: wire message list --json
```

Unauthenticated state should provide login guidance without treating discovery as failure. Never trigger network synchronization.

## Test-driven plan

1. Define byte and execution-time budgets.
2. Add deterministic tests for authenticated, unauthenticated, invalid-session, and unavailable-local-state paths.
3. Read only local session/cache state through injected service contract.
4. Add contextual next commands preserving known scope.
5. Keep `--help` as full static discovery.
6. Measure no-argument output and round trips before/after.

## Acceptance criteria

- No-argument invocation exits `0`.
- It performs no network requests or synchronization.
- Output fits defined byte/token budget.
- State and hints are deterministic and redacted.
- Failure to inspect optional state degrades to useful static guidance.

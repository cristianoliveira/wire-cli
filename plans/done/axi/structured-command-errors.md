# AXI plan: Structured command errors

## Problem

Structured commands emit JSON on success, but command, validation, and dependency failures are generally plain text on stderr. Agents must branch on exit status and parse unrelated human messages. Parser-level failures also bypass feature formatters, so adding structured errors one command at a time will drift.

## Scope

Define one shared error value and rendering boundary for commands that explicitly request structured output:

```json
{
  "error": {
    "code": "network_error",
    "message": "Could not mark the message as read.",
    "retryable": true,
    "next": "wire message set <conversation-id> --read <message-id> --json"
  }
}
```

- Preserve existing human-mode stderr behavior.
- In structured mode, emit one complete error document to stdout and reserve stderr for diagnostics/logs.
- Normalize process exits to `1` operational and `2` usage while preserving domain categories in `error.code`.
- Cover command-body validation, access denial, Clikt missing/unknown arguments, and dependency failures.
- Include one actionable, copyable recovery command only when correct; preserve original scope and structured-output flags.
- Buffer structured output so serialization failure cannot leave partial stdout.
- Redact secrets and hide raw SDK/dependency errors.

## Migration strategy

1. Introduce the shared error model and renderer without changing commands.
2. Route one representative leaf mutation (`message set --read`) through it.
3. Extend to neighboring message mutations (`send`, `react`, `delete`).
4. Move parser/access-policy handling to the same boundary.
5. Migrate other structured commands feature by feature.

## Test-driven plan

1. Add renderer tests for usage, auth, not-found, network, server, and unknown failures.
2. Add escaping/redaction tests and prove serialization is deterministic.
3. Add command tests that structured failures write only stdout; human failures remain stderr.
4. Add tests proving unknown flags and missing values fail before service-provider invocation.
5. Add tests for retryable versus non-retryable failures and copyable `next` hints.
6. Add Bats tests separating stdout/stderr and validating JSON with `jq`.
7. Add regression tests for global `--verbose`/logging behavior in structured mode.

## Acceptance criteria

- Every migrated structured command emits same error schema.
- Structured success and error share one output channel and format.
- Structured errors never include stack traces, tokens, passwords, SDK class names, or partial documents.
- Exit codes remain `0`, `1`, or `2`.
- Unknown/missing input names offending argument and includes valid local alternatives.
- Retry hints are correct, scoped, copyable, and absent when no safe recovery exists.
- Human-mode contracts remain compatible during migration.

# AXI plan: Message read result contract

## Problem

`wire message set <conversation-id> --read <message-id>` is deterministic and non-interactive, but success only prints `Message marked as read.` Agents cannot confirm coordinates, inspect resulting state, or distinguish an applied update from an idempotent no-op. The command also rejects `--json` while neighboring message mutations support it.

## Scope

- Keep current human output stable unless no-op can be reported truthfully.
- Add `--json` using canonical message field names already used by message search/watch:

```json
{
  "conversationId": "conv-001",
  "messageId": "msg-001",
  "state": "read",
  "outcome": "applied"
}
```

- Define `outcome` as `applied` or `already_read`.
- Extend CLI-owned result types through command, service, API client, runtime, and stub without exposing Kalium types.
- Determine no-op before mutation from public Kalium conversation state. The current update use case returns `Unit` and silently skips older/equal timestamps; never infer `applied` from `Unit` alone.
- Document concurrency semantics: outcome describes local state observed by this operation and must not claim causal ownership when it cannot be proven.
- Expand leaf help to 2 copyable examples, including structured output.

## Out of scope

- Marking messages unread.
- Wildcard message selection.
- Making JSON or TOON the default for all commands.
- Changing existing human output for other message mutations.

## Test-driven plan

1. Add contract tests for `APPLIED` and `ALREADY_READ` results.
2. Add command tests for human applied, human no-op, JSON applied, JSON no-op, stable field names, and clean stderr.
3. Add service/API tests proving outcome and coordinates survive every boundary.
4. Add runtime tests with controlled message timestamp and conversation last-read timestamp:
   - target newer than last-read -> applied;
   - target equal/older -> already read and update use case is not called;
   - missing message/conversation -> operational failure.
5. Add stub modes or deterministic fixtures for both outcomes.
6. Add Bats tests for repeated invocation, `--json`, valid JSON, exact exit codes, and help examples.
7. Measure human and JSON output bytes plus command round trips.

## Acceptance criteria

- Success identifies conversation, message, state, and truthful outcome in JSON.
- Repeating same request exits `0` and reports `already_read` without sending another update.
- Human stdout remains one deterministic line; structured stdout is valid JSON with zero stderr on success/no-op.
- Input validation happens before session or SDK access.
- Missing message and dependency failures remain exit `1`; malformed usage remains exit `2`.
- JSON uses shared serializers/builders, not string concatenation.
- Tests cover applied, no-op, usage error, auth failure, missing message, and transient failure.

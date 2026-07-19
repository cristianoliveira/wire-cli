# AXI plan: Structured list envelopes

## Problem

Several `--json` list commands return bare arrays. Agents cannot tell returned page size from total result count or know whether output was truncated.

## Scope

Inventory list commands supporting JSON, then introduce one shared envelope contract:

```json
{
  "items": [],
  "returned": 0,
  "total": 0,
  "truncated": false
}
```

`total` must only be emitted when cheaply and correctly known. If unknown, use an explicit nullable/omitted contract rather than treating page size as total.

## Test-driven plan

1. Add formatter tests for populated, empty, and truncated envelopes.
2. Add command tests proving JSON schema is stable.
3. Extend service views with total/truncation metadata where source can provide it.
4. Migrate list commands one feature at a time.
5. Add Bats schema checks using `jq`.
6. Document intentional JSON contract break before release.

## Acceptance criteria

- JSON lists use one envelope shape.
- `returned` equals item count.
- `total` never falsely represents page size.
- `truncated` is deterministic.
- Human and JSONL output behavior remains explicit.
- Success, empty, pagination/truncation, and dependency failure paths are covered.

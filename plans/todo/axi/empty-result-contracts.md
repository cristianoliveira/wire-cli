# AXI plan: Empty-result contracts

## Problem

JSONL list commands can emit zero bytes for empty results, while human output often says only `No ... found.` without query or filter context. Agents cannot distinguish valid empty output from missing output.

## Proposed behavior

- Human output states empty result with relevant query/filter scope.
- JSON envelope represents empty result with `items: []` and counts.
- JSONL uses one documented deterministic empty-result mechanism. Prefer a typed metadata record only if it does not break line consumers; otherwise document zero lines and provide summary metadata on stderr or through an explicit flag.

## Test-driven plan

1. Inventory empty output for all list/search commands.
2. Define JSONL compatibility constraints from existing examples/tests.
3. Add tests for empty human, JSON, and JSONL output.
4. Add shared contextual empty-message helpers where wording genuinely repeats.
5. Add Bats tests separating stdout and stderr.

## Acceptance criteria

- Every empty result is distinguishable from failure.
- Human messages preserve query/filter context.
- JSON empty output follows list envelope schema.
- JSONL empty behavior is documented and deterministic.
- Empty results exit `0`; dependency failures exit `1`.

# AXI plan: Field selection and truncation

## Problem

List commands can emit large content and fixed field sets. Agents pay for fields they do not need, while long message content has no preview/size/truncation contract.

## Scope

Start with representative message, conversation, user, and device outputs. Measure real samples before changing defaults.

Potential interface:

```bash
wire message list --fields messageId,conversationId,senderName --json
wire message list --full --json
```

Default previews should include original size and a deterministic `truncated` marker. `--full` must be explicit and preserve structured stdout.

## Test-driven plan

1. Capture representative output byte counts and agent decisions.
2. Define canonical field names from existing serializers; do not duplicate schema.
3. Add parser tests for valid, unknown, duplicate, and empty field selections.
4. Add formatter tests for boundary lengths and multiline content.
5. Validate fields before service access.
6. Add `--full` and `--fields` incrementally to high-volume commands.
7. Compare output bytes and required command round trips.

## Acceptance criteria

- Defaults expose only decision-relevant fields.
- Unknown fields exit `2` and list valid fields.
- Truncation includes preview, original size, and marker.
- `--full` reliably disables truncation.
- JSON and JSONL schemas remain deterministic.
- Measured bytes improve without increasing common round trips.

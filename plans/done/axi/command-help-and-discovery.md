# AXI plan: Command help and discovery

## Problem

Leaf help is concise but often omits accepted bounds, defaults, and copyable examples. Root description still understates CLI scope.

## Scope

- Update root description to cover account, conversations, messages, devices, sync, and backup.
- Add accepted values and numeric bounds to leaf options.
- Add 2–3 copyable examples to common leaf commands.
- Keep help local; do not duplicate full command tree on leaf pages.
- Ensure mutation examples use non-interactive confirmation flags.

## Test-driven plan

1. Add Bats assertions for root description and representative leaf examples.
2. Inventory undocumented defaults and accepted values.
3. Update help in feature-sized batches.
4. Verify invalid-input messages use same accepted values as help.
5. Measure help bytes to prevent unbounded growth.

## Acceptance criteria

- Root description matches current CLI scope.
- Each common leaf command documents arguments, defaults, accepted values, and examples.
- Examples are copyable after replacing placeholders.
- Help does not access runtime dependencies.
- Invalid-input guidance and help cannot drift silently.

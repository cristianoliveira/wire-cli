# AXI plan: TOON output boundary

## Problem

The CLI has feature-local JSON builders and no TOON dependency. Adding TOON directly inside individual commands would duplicate schemas, create inconsistent escaping, and violate one-source-of-truth. AXI prefers TOON as compact structured output, but adoption must be measured against representative Wire data and preserve existing JSON consumers.

## Scope

- Evaluate maintained, specification-compatible TOON libraries usable from Kotlin/JVM.
- Keep domain values format-independent.
- Introduce one structured output boundary that can render canonical domain values as TOON and compatibility JSON.
- Start with bounded shapes:
  - message mutation result;
  - message list envelope;
  - structured error.
- Define explicit format selection and compatibility policy before changing defaults.
- Buffer encoding before writing stdout.
- Do not shell out to a converter in production and do not hand-roll TOON.

## Decision gate

Before implementation, record:

1. library maintenance, license, JVM support, and specification version;
2. strict encode/decode behavior;
3. deterministic ordering and escaping;
4. empty collection, null, nested value, and uniform-array behavior;
5. JSON compatibility and migration cost;
6. measured bytes/tokens for representative Wire outputs.

If no suitable maintained Kotlin/JVM library exists, keep JSON as structured compatibility format and defer TOON rather than creating a custom encoder.

## Test-driven plan

1. Capture canonical JSON fixtures for mutation success/no-op/error and representative message lists.
2. Add semantic round-trip tests: domain -> TOON -> decoded value equals canonical domain value.
3. Add strict tests for quotes, delimiters, multiline content, Unicode, nulls, empty arrays, nested objects, and declared array lengths.
4. Add deterministic-output golden tests.
5. Add command tests proving stdout contains one complete document and stderr remains clean.
6. Measure TOON against minified JSON with same semantic fields; report bytes and estimated token counts without claiming universal savings.
7. Add Bats validation using a trusted decoder available in development/CI only.
8. Document `--format toon|json` and default migration policy.

## Acceptance criteria

- TOON is produced only through maintained library at shared output boundary.
- Domain contracts and field names are single source for TOON and JSON.
- Semantic round trips pass for all representative and edge-case fixtures.
- Serialization failures emit no partial stdout.
- Existing JSON mode remains available during documented compatibility window.
- Default-format decision is backed by measured output and consumer impact.
- CI pins validator/spec version and detects schema drift.

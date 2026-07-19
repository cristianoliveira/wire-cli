# AXI follow-up plans

Remaining work from AXI audit after commit `ad80f30`.

Recommended order:

1. [Structured list envelopes](structured-list-envelopes.md)
2. [Empty-result contracts](empty-result-contracts.md)
3. [Command help and discovery](command-help-and-discovery.md)
4. [No-argument live state](no-argument-live-state.md)
5. [Field selection and truncation](field-selection-and-truncation.md)

Global constraints:

- Keep stdout deterministic and machine-readable.
- Keep diagnostics and progress on stderr.
- Preserve process exit contract: `0` success/no-op, `1` operational failure, `2` usage error.
- Validate input before session, filesystem, subprocess, or network access.
- Write happy and unhappy tests first.
- Measure output bytes and command round trips before optimizing formats.

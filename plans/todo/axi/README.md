# AXI follow-up plans

No remaining work from message read-state AXI evaluation.

Global constraints:

- Keep human stdout deterministic and script-friendly.
- Keep diagnostics and progress on stderr.
- In explicit structured mode, emit success and errors in same format on stdout with clean stderr.
- Preserve process exit contract: `0` success/no-op, `1` operational failure, `2` usage error.
- Validate input before session, filesystem, subprocess, or network access.
- Write happy and unhappy tests first, including no-op and unknown-input paths.
- Keep domain values format-independent and field names canonical.
- Never hand-roll TOON or claim token savings without representative measurements.
- Preserve JSON compatibility until format migration is explicit and documented.

Completed AXI plans:

- [TOON output boundary](../../done/axi/toon-output-boundary.md)
- [Structured command errors](../../done/axi/structured-command-errors.md)
- [Message read result contract](../../done/axi/message-read-result-contract.md)
- [Structured list envelopes](../../done/axi/structured-list-envelopes.md)
- [Empty-result contracts](../../done/axi/empty-result-contracts.md)
- [Command help and discovery](../../done/axi/command-help-and-discovery.md)
- [No-argument live state](../../done/axi/no-argument-live-state.md)
- [Field selection and truncation](../../done/axi/field-selection-and-truncation.md)

# Import Guidance

Own import-source selection, validation, and restoration through Wire backup runtime.

- Keep supported source names and import results in `ImportContracts.kt` as source of truth.
- Require active session through injected session provider; do not access auth storage directly.
- `WireBackupImporter` handles format/file adaptation; SDK restoration remains behind runtime interface.
- Validate source and path before invoking SDK.
- Return explicit failures for unsupported source, unreadable/malformed backup, auth failure, and SDK failure.
- Coordinate shared backup format concepts with `exporting/` without creating duplicate schemas.

Tests belong in `src/test/kotlin/wirecli/importing/`. Use temporary fixtures and cover each source, validation failures, malformed backups, missing auth, and runtime failures.

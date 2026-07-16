# Export Guidance

Own local cache backup creation and export into supported external Wire backup representations.

- Keep export inputs/results and service/runtime interfaces in `ExportContracts.kt`.
- Distinguish local backup creation failures from external serialization/export failures.
- Require active session through injected session provider; do not read auth files directly.
- Filesystem and ZIP handling must use explicit paths, bounded resources, and deterministic cleanup.
- Reuse canonical import-source definitions where formats overlap; do not duplicate schemas.
- SDK backup creation stays in `SdkLocalCacheBackupRuntime`.

Tests belong in `src/test/kotlin/wirecli/exporting/`. Use temporary directories and fixtures; cover local/external sources, malformed data, filesystem failures, auth failure, and produced archive/JSON shape.

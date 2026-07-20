# Feature plan: Export local Kalium cache

## Problem

`wire backup export` currently requires an external `.wbu` input. Users reasonably expect omitting backup file to export conversations and messages already available in authenticated user's local Kalium cache.

## Proposed CLI

```bash
# Export current authenticated user's local cache
wire backup export --format jsonl --destination ./analysis

# Export an external backup without requiring login
wire backup export --format jsonl --destination ./analysis backup.wbu
```

Source selection is implicit:

- no `BACKUP` argument: local Kalium cache; active login required
- `BACKUP` argument: supplied Wire backup; login not required

Keep `--from` as optional future format override, not required in common path.

## Architecture

Do not access Kalium database tables directly or duplicate JSON mapping.

1. Add local-cache export runtime behind explicit interface.
2. Resolve active auth session.
3. Call Kalium `sessionScope(userId).multiPlatformBackup.create(...)` to create temporary unencrypted `.wbu`.
4. Pass temporary backup to existing `WireBackupJsonExporter`.
5. Delete temporary backup in `finally`, on success and failure.
6. Keep command responsible only for argument parsing and output.
7. Wire runtime/service in `KaliumRuntime` composition root.

Potential contracts:

```text
LocalCacheBackupRuntime.create(session): Result<Path>
ExportInput.ExternalBackup(path)
ExportInput.LocalCache
```

## Test-driven plan

1. Command test: omitted backup selects local cache.
2. Command test: provided backup selects external file.
3. Service test: local cache requires active session.
4. Service test: external backup does not require session.
5. Runtime adapter tests: map Kalium create success/failure.
6. Lifecycle test: temporary backup is deleted after successful export.
7. Unhappy-path test: temporary backup is deleted when JSON export fails.
8. Bats test: help documents optional backup and source behavior.
9. Isolated smoke test: copy auth/Kalium state into temporary `HOME`, export without backup argument, validate JSONL with `jq`.

## Acceptance criteria

- `wire backup export --format jsonl --destination DIR` exports local cached users, conversations, and messages.
- Local-cache export never triggers network synchronization.
- External-backup export remains backward compatible.
- JSON schema and mapping remain single-sourced in `WireBackupJsonExporter`.
- Temporary backups are never left behind.
- Missing session returns exit code 11 with login guidance.
- No plaintext messages, tokens, or backup passwords are logged.

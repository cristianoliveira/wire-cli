# Stories by Topic

This folder reorganizes MVP stories by topic while preserving story numbers, status markers, and acceptance criteria.
Presence core stories are done, and any non-blocking follow-ups are tracked in topic files as `[Planned]`.

## Status legend

- `[Done]`: implemented and verified for the MVP scope.
- `[Planned]`: not implemented yet; planned for upcoming work.
- `[Must]`: required for MVP or core behavior.
- `[Should]`: important but lower priority than Must.

## Topics

- [Authentication](./authentication.md)
- [Profile](./profile.md)
- [Presence](./presence.md)
- [Device Management](./device-management.md)
- [Sync Health](./sync-health.md)

## Acceptance Test Lanes

- Stub deterministic lane (explicit override):
  - `./gradlew installDist`
  - `WIRE_BACKEND=stub ./gradlew batsTest --tests 'test/bats/10_auth_profile_stories.bats'`
  - Uses `WIRE_STUB_MODE` test toggles for deterministic output and exit-code assertions.
- Real-auth smoke lane (credential-gated, default backend):
  - `WIRE_REAL_EMAIL='<email>' WIRE_REAL_PASSWORD='<password>' bats test/bats/10_auth_profile_stories.bats`
  - The live smoke scenario is skipped unless `WIRE_REAL_EMAIL` and `WIRE_REAL_PASSWORD` are set.

## Real backend smoke commands

- `printf '%s\n' "$WIRE_REAL_PASSWORD" | ./build/install/wire-cli/bin/wire-cli login --email "$WIRE_REAL_EMAIL" --password-stdin`
- `./build/install/wire-cli/bin/wire-cli profile`
- `./build/install/wire-cli/bin/wire-cli logout`
- For deterministic fake responses, set `export WIRE_BACKEND=stub`.
- Optional custom backend: include `--server '<staging|production|invite-link-or-config-url>'` on `login`.

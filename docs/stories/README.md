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

## Real backend smoke commands

- `WIRE_BACKEND=real WIRE_REAL_EMAIL='<email>' WIRE_REAL_PASSWORD='<password>' ./build/install/wire-cli/bin/wire-cli login --email "$WIRE_REAL_EMAIL" --password "$WIRE_REAL_PASSWORD"`
- `WIRE_BACKEND=real ./build/install/wire-cli/bin/wire-cli profile`
- `WIRE_BACKEND=real ./build/install/wire-cli/bin/wire-cli logout`
- Optional custom backend: include `--server '<staging|production|invite-link-or-config-url>'` on `login`.

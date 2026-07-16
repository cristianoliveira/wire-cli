# Device Guidance

Own device listing, details, verification, deletion, domain results, and device failure mapping.

- Define user-facing device contracts independently of Kalium types.
- Centralize SDK/error translation in real adapter and `DeviceFailureMapper`.
- Session-backed service performs authenticated operations; auth guard maps missing session.
- Keep destructive-operation confirmation in command layer, not service contracts.
- Stub behavior must support deterministic inventory and mutation tests.

Tests belong in `src/test/kotlin/wirecli/device/`. Cover success, absent devices, invalid identifiers, auth failure, adapter error mapping, and destructive operations.

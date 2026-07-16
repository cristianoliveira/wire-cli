# User Guidance

Own user search/discovery, user detail views, query validation, and user output formatting.

- Use Wire user-domain names in contracts; hide Kalium identifiers/models behind adapter.
- Keep schema-versioned JSON output stable and centralized in user formatter/contracts.
- Connection state may be represented using connection contracts, but connection mutations belong in `connection/`.
- Session-backed service resolves auth; auth guard maps unavailable auth.
- Validate search input before SDK calls.

Tests belong in `src/test/kotlin/wirecli/user/`. Cover queries, JSON shape/version, connection labels, missing auth, no results, and adapter failures.

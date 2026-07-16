# Configuration Guidance

Own access policy, command access, CLI mode, paths, environment interpretation, feature flags, and configurable timeouts.

- Centralize environment parsing here; feature code receives typed configuration.
- Invalid or non-positive numeric/duration values must use documented safe fallback or explicit error.
- Keep access-policy decisions separate from command rendering and feature business logic.
- Avoid process-global mutable configuration.
- New settings need one source of truth, deterministic parsing tests, and clear precedence.

Tests belong in `src/test/kotlin/wirecli/config/`. Cover defaults, valid overrides, malformed values, precedence, policy allow/deny, and missing files without reading user environment implicitly.

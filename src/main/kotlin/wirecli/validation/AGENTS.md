# Validation Guidance

Own validation rules genuinely shared by multiple features.

- Keep feature-specific validation with owning feature.
- Add a shared rule only after at least two consumers need same behavior.
- Return explicit validation outcomes/messages; do not print or exit here.
- Keep validators pure and independent of Kalium, filesystem, environment, and command framework.
- Preserve one canonical implementation instead of copying regexes or limits.

Tests belong in `src/test/kotlin/wirecli/validation/`. Cover accepted values, boundaries, malformed values, empty input, and error messages deterministically.

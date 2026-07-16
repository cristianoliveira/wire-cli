# Kotlin Test Guidance

Tests mirror production packages under `src/main/kotlin/wirecli/`.

- Follow TDD: first make desired success and failure behavior fail, then implement.
- Test contracts and observable behavior, not private implementation details.
- Prefer small fake/stub interfaces over mocking Kalium internals.
- Never contact real Wire services or rely on user home/session state.
- Use temporary directories for filesystem behavior and clean them deterministically.
- For coroutine/flow behavior, bound waits and avoid timing-dependent sleeps.
- Keep fixtures local unless several test classes genuinely share same domain fixture.
- Command tests assert parsed requests, output streams, JSON shape, and exit behavior.
- Adapter tests translate controlled SDK results/errors into domain results; do not retest Kalium itself.

Run focused Gradle tests during iteration, then `make test-unit`. CLI contract changes also require Bats coverage.

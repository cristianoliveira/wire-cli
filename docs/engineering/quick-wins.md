# Guardrails Quick Wins

This is the first enforcement slice for repository guardrails.

## What was added
- Canonical local quality gate entrypoint: `make all`
- Kotlin formatter/lint baseline: `ktlint`
- Complexity guardrail baseline (warn-first): `detekt` with thresholds in `detekt.yml`
- Shared git hook config: `.pre-commit-config.yaml`

## Command path

### Local development
- Auto-format: `make format`
- Formatting check: `make format-check`
- Static lint/complexity checks: `make lint`
- Tests (unit + bats integration): `make test`
- Aggregate quality gate: `make all`

### Optional hook setup
- Install hook runner: `pipx install pre-commit` (or `pip install pre-commit`)
- Install hooks: `pre-commit install --hook-type pre-commit --hook-type pre-push`
- Run manually on all files: `pre-commit run --all-files`

## Initial enforcement mode
- `detekt` is configured with `ignoreFailures = true` for warn-first adoption.
- This keeps delivery unblocked while surfacing complexity and maintainability hotspots.
- After baseline stabilization, switch to fail-on-violation mode.

## Current thresholds (starting point)
- `CyclomaticComplexMethod`: 15
- `LongMethod`: 60
- `LargeClass`: 500
- `TooManyFunctions`: 15
- `NestedBlockDepth`: 4

# Quality Gate Contract

## Overview

This document defines the quality gate contract for the wire-cli project, ensuring parity between local development checks and CI pipeline validation.

## Makefile Quality Gates

### Primary Targets

```make
# Auto-format Kotlin code
format: ktlintFormat

# Verify code formatting without changing files
format-check: ktlintCheck

# Run static analysis (detekt)
lint: detekt

# Run unit tests only (quick check)
test-unit: test

# Run all tests (unit + integration)
test: test batsTest

# Full quality gate - runs format-check, lint, and test
all: format-check lint test

# Generate ktlint baseline for new projects
ktlint-baseline: ktlintGenerateBaseline
```

### Execution Requirements

- **format-check** must pass before committing
- **lint** must pass before merging
- **test-unit** must pass before pushing (pre-push hook)
- **all** must pass before creating pull requests

## CI Workflow Specification

### Workflow: ci.yml (Full Pipeline)

**Trigger Events:**
- Push to main branch
- Pull requests to main or develop branches

**Jobs:**
1. **format-check**: Verify code formatting
   - Runs: `nix develop --command bash -c "./gradlew --no-daemon ktlintCheck"`
   - Failure: Blocks merge if formatting issues exist

2. **lint**: Run static analysis
   - Runs: `nix develop --command bash -c "./gradlew --no-daemon detekt"`
   - Failure: Blocks merge if lint violations exist

3. **test-unit**: Run unit tests
   - Runs: `nix develop --command bash -c "./gradlew --no-daemon test"`
   - Artifacts: Uploads test results to GitHub

4. **test-integration**: Run integration tests
   - Runs: `nix develop --command bash -c "./gradlew --no-daemon batsTest"`
   - Artifacts: Uploads test results to GitHub

5. **build**: Build verification
   - Runs: `nix build`
   - Validation: Tests built binary with `./result/bin/wire-cli --help`

### Workflow: ci-fast.yml (Quick Feedback)

**Trigger Events:**
- Push to main branch
- Pull requests to main or develop branches

**Jobs:**
1. **ci**: Run all quality checks
   - Runs: `nix develop --command bash -c "make all"`
   - Includes: format-check, lint, test, build verification
   - Artifacts: Uploads test results

## CI Parity Requirements

### Local ↔ CI Equivalence

| Check | Local Command | CI Job | Parity Requirement |
|-------|---------------|--------|-------------------|
| Format | `make format-check` | format-check | Identical ktlint version and rules |
| Lint | `make lint` | lint | Identical detekt version and rules |
| Unit Test | `make test-unit` | test-unit | Same test suite and environment |
| Integration Test | `make test` | test-integration | Same test suite and environment |
| Build | `nix build` | build | Same Nix environment and inputs |

### Environment Consistency

- **Nix**: Same nixpkgs channel and configuration
- **Java**: Same Java version and Gradle version
- **Cachix**: Same binary cache for reproducible builds
- **Test Data**: Same test fixtures and dependencies

## Quality Thresholds

### Test Requirements
- **Unit Test Success Rate**: 100% (no failures)
- **Integration Test Success Rate**: 100% (no failures)
- **Test Coverage**: Minimum 80% (configurable per project)
- **Build Success**: 100% (binary must be executable)

### Performance Targets
- **CI Runtime**: < 10 minutes for main branch pushes
- **Local Quality Gate**: < 2 minutes for quick checks
- **Full Test Suite**: < 5 minutes locally

## Failure Handling

### Local Failure Recovery
1. Run failing check locally: `make <check>`
2. Fix issues and re-run
3. Commit fixes: `git add . && git commit -m "fix: <issue>"`
4. Push changes

### CI Failure Recovery
1. Reproduce locally: `make all`
2. Fix issues
3. Push changes
4. Monitor CI run
5. If persistent issues: Update guardrails and notify team

## Validation Procedures

### Weekly Validation
- Run `make all` locally
- Verify CI passes for recent commits
- Check test coverage trends
- Review CI performance metrics

### Per-Release Validation
- Run full test suite
- Verify build artifacts
- Update version in bd issues
- Document breaking changes

## Success Criteria

- Local `make all` passes before every push
- CI passes for 100% of recent commits
- Test results are consistently uploaded as artifacts
- No merge conflicts due to formatting/linting issues
- New contributors can set up environment and pass all checks

---

*This quality gate contract is binding for all contributors and must be updated when CI/CD workflows change.*
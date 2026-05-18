# Engineering Guardrails for wire-cli

## Overview

This document defines the engineering guardrails for the wire-cli project, establishing a predictable workflow, quality gates, and release discipline.

## Project Context

- **Project Type**: Kotlin CLI tool (wire-cli)
- **Build System**: Gradle with Nix for reproducible builds
- **CI Platform**: GitHub Actions
- **Code Quality Tools**: ktlint, detekt, Gradle tests

## Core Guardrails

### 1. Code Formatting
- **Intent**: Ensure consistent code style across the codebase
- **Operator command(s)**: `make format` or `make format-check`
- **Enforcement**: 
  - Pre-commit hook: `make format-check`
  - CI job: `ci.yml` format-check job
- **Failure signal**: ktlint errors reported in console output
- **Recovery**: Run `make format` to auto-format code, then commit

### 2. Code Linting
- **Intent**: Maintain code quality and catch potential issues early
- **Operator command(s)**: `make lint`
- **Enforcement**: 
  - Pre-commit hook: `make lint`
  - CI job: `ci.yml` lint job
- **Failure signal**: detekt violations reported in console output
- **Recovery**: Fix detekt violations and re-run `make lint`

### 3. Quality Gate (All Checks)
- **Intent**: Ensure code passes all quality standards before merging
- **Operator command(s)**: `make all`
- **Enforcement**: 
  - Pre-push hook: `make test-unit`
  - CI job: `ci-fast.yml` runs `make all`
- **Failure signal**: Non-zero exit code from any check
- **Recovery**: Address failed checks and re-run `make all`

### 4. Testing Strategy
- **Intent**: Maintain test coverage and ensure functionality works
- **Operator command(s)**: 
  - Unit tests: `make test-unit`
  - All tests: `make test`
- **Enforcement**: 
  - Pre-push hook: `make test-unit`
  - CI jobs: separate unit and integration test jobs
- **Failure signal**: Test failures reported in test results
- **Recovery**: Fix failing tests and re-run `make test`

### 5. Build Verification
- **Intent**: Ensure the project builds successfully and produces a working binary
- **Operator command(s)**: `nix build`
- **Enforcement**: CI job in both workflows
- **Failure signal**: Build failure or binary not executable
- **Recovery**: Fix build issues and re-run `nix build`

## CI/CD Workflow

### Trigger Events
- **Push to main/develop**: Full CI pipeline runs
- **Pull Request**: Full CI pipeline runs
- **Pre-commit**: format-check and lint run
- **Pre-push**: test-unit runs

### CI Jobs
1. **format-check**: Verify code formatting
2. **lint**: Run detekt static analysis
3. **test-unit**: Run unit tests
4. **test-integration**: Run integration tests
5. **build**: Verify build and binary functionality

### Fast CI
- Runs `make all` for quick feedback
- Includes build verification
- Uploads test results as artifacts

## Local Development Workflow

### Daily Development
```bash
git pull --rebase  # Keep branch up to date
# Make changes
make format  # Auto-format code
make lint    # Check code quality
make test-unit  # Quick local test
git add .
git commit -m "feat: descriptive message"
git push
```

### Before Pushing
```bash
make all  # Run full quality gate
git push
```

### Session End (Landing)
```bash
git status
git add <files>
git commit -m "<message>"
git pull --rebase && git push
git status  # Verify up to date with origin
```

## Quality Gate Contract

### Makefile Targets
```make
format: ktlintFormat  # Auto-format code
format-check: ktlintCheck  # Verify formatting
lint: detekt  # Run static analysis
test: test batsTest  # Run all tests
test-unit: test  # Run unit tests only
all: format-check lint test  # Full quality gate
ktlint-baseline: ktlintGenerateBaseline  # Generate formatting baseline
```

### CI Parity
- Local `make all` must pass before pushing
- CI runs identical checks in isolated environment
- Test results are uploaded as artifacts for inspection

## Anti-Patterns

### ❌ DO NOT
- Commit unformatted code (will fail pre-commit)
- Skip running `make all` before pushing
- Merge PRs with failing CI jobs
- Modify CI workflows without updating guardrails

### ✅ DO
- Run `make format` to auto-format before committing
- Use `make all` for local quality gate verification
- Keep CI and local checks in sync

## Recovery Procedures

### Failed Format Check
```bash
make format  # Auto-format
git add .
git commit -m "fix: format code"
```

### Failed Lint
```bash
# Review detekt output and fix issues
make lint  # Verify fixes
git add .
git commit -m "fix: lint issues"
```

### Failed Tests
```bash
make test  # Run all tests locally
# Fix failing tests
make test  # Verify fixes
git add .
git commit -m "fix: test failures"
```

### CI Failure
1. Reproduce locally: `make all`
2. Fix issues
3. Push changes
4. Monitor CI run

## Success Criteria

- New contributors can follow this document and set up the environment
- Local checks and CI checks produce identical results
- "Done" is objectively testable (commands + enforcement)
- Guardrails are versioned and reviewed like code
- CI runs in under 10 minutes for main branch pushes

## Review Schedule

- **Monthly**: Review CI performance and guardrail effectiveness
- **Per-release**: Update guardrails for new features/dependencies
- **As-needed**: When CI failures indicate workflow issues

---

*This guardrails document is maintained alongside the codebase and should be reviewed during pull requests that modify the development workflow.*
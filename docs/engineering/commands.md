# Command Cheat Sheet

## Development Workflow

### Daily Development
```bash
# Keep branch up to date
git pull --rebase

# Make changes and format
make format

# Check code quality
make lint

# Run quick local tests
make test-unit

# Commit changes
git add .
git commit -m "feat: descriptive message"
git push
```

### Before Pushing
```bash
# Run full quality gate
make all

# Push changes
git push
```

### Session End (Landing)
```bash
# Verify working directory state
git status

# Add files
git add <files>

# Commit with message
git commit -m "<message>"

# Push with rebase
git pull --rebase && git push

# Verify
git status  # Should show "up to date with origin"
```

## Quality Gates

### Format
```bash
# Auto-format code
make format

# Check formatting (pre-commit)
make format-check
```

### Lint
```bash
# Run static analysis
make lint
```

### Testing
```bash
# Run unit tests only (quick)
make test-unit

# Run all tests (unit + integration)
make test

# Run full quality gate
make all
```

### Build
```bash
# Build with Nix
nix build

# Test built binary
./result/bin/wire-cli --help
```

## CI/CD Commands

### Local CI Parity
```bash
# Run full CI equivalent
make all
```

### CI Workflow Triggers
```bash
# View current CI status
gh run list

# Re-run failed jobs
gh run rerun <run-id>
```

## Troubleshooting

### Failed Format Check
```bash
make format  # Auto-format
git add .
git commit -m "fix: format code"
```

### Failed Lint
```bash
make lint  # Review and fix issues
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

### CI Performance Issues
```bash
# Check CI cache usage
gh run view <run-id>

# Clear local Nix cache if needed
nix-collect-garbage -d
```

---

*Keep this cheat sheet handy for quick reference during development.*
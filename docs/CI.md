# Continuous Integration (CI)

This document describes the CI setup for the wire-cli project.

## Overview

The project uses GitHub Actions for CI with Nix for reproducible builds. All quality gates are defined in the `Makefile` and `.pre-commit-config.yaml`.

## Workflows

### Main CI Workflow (`.github/workflows/ci.yml`)

Runs parallel jobs for:
- **Format Check** - ktlint formatting verification
- **Lint** - detekt static analysis
- **Unit Tests** - Gradle test suite
- **Integration Tests** - Bats integration tests
- **Build** - Nix build verification

### Fast CI Workflow (`.github/workflows/ci-fast.yml`)

Runs all checks in a single job for faster feedback on smaller projects.

## Quality Gates

| Gate | Command | Purpose |
|------|---------|---------|
| format-check | `./gradlew ktlintCheck` | Verify Kotlin code formatting |
| lint | `./gradlew detekt` | Static code analysis |
| test (unit) | `./gradlew test` | Run unit tests |
| test (integration) | `./gradlew batsTest` | Run Bats integration tests |
| build | `nix build .#cli` | Verify project builds |

## Local Development

### Pre-commit Hooks

The project uses pre-commit hooks to run quality checks before committing:

```bash
# Install pre-commit (if not already installed)
brew install pre-commit  # macOS
# or: pip install pre-commit

# Install hooks
pre-commit install
pre-commit install --hook-type pre-push
```

### Running Checks Manually

```bash
# Enter Nix dev shell
nix develop

# Run all checks
make all

# Or run individually
make format-check
make lint
make test

# Or use Gradle directly
./gradlew check
```

## Cachix Setup

Cachix caches Nix builds to speed up CI and local development.

### For CI

1. Create a cache at [cachix.org](https://cachix.org/)
2. Add `CACHIX_AUTH_TOKEN` to GitHub repository secrets
3. Update the cache name in `.github/workflows/ci.yml`:
   ```yaml
   - name: Setup Cachix
     uses: cachix/cachix-action@v14
     with:
       name: your-cache-name  # Change this
       authToken: '${{ secrets.CACHIX_AUTH_TOKEN }}'
   ```

### For Local Development

```bash
# Install cachix
nix-env -iA cachix -f https://cachix.org/api/v1/install

# Use the cache
cachix use your-cache-name

# Push local builds to the cache
cachix push your-cache-name result
```

## Troubleshooting

### CI Failures

1. **Submodule issues**: Ensure git submodules are initialized
   ```bash
   git submodule update --init --recursive
   ```

2. **Flake lock issues**: Update the flake lock
   ```bash
   nix flake update
   ```

3. **Nix store issues**: Clear problematic derivations
   ```bash
   nix-store --gc
   nix-collect-garbage -d
   ```

### Pre-commit Hook Issues

```bash
# Skip hooks temporarily
git commit --no-verify -m "WIP"

# Reinstall hooks
pre-commit uninstall && pre-commit install
```

## Adding New Checks

To add a new quality check:

1. Update `Makefile` with the new command
2. Update `.pre-commit-config.yaml` if needed
3. Add a new job to `.github/workflows/ci.yml`:
   ```yaml
   new-check:
     name: New Check
     runs-on: ubuntu-latest
     steps:
       - uses: actions/checkout@v4
         with:
           submodules: recursive
       - uses: cachix/install-nix-action@v24
         with:
           nix_path: nixpkgs=channel:nixos-unstable
           extra_nix_config: |
             experimental-features = nix-command flakes
       - uses: cachix/cachix-action@v14
         with:
           name: wire-cli
           authToken: '${{ secrets.CACHIX_AUTH_TOKEN }}'
           skipPush: true
       - run: nix develop --command bash -c "make new-check"
   ```

## CI Best Practices

1. **Keep jobs independent**: Each job should be able to run in isolation
2. **Cache effectively**: Use Cachix for Nix store, GitHub Actions cache for other dependencies
3. **Fail fast**: Run quick checks (format, lint) before slow ones (tests)
4. **Upload artifacts**: Save test results and build artifacts for debugging
5. **Use matrix strategy**: For testing across multiple environments (if needed)

## Related Documentation

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Nix Manual](https://nixos.org/manual/nix/stable/)
- [Cachix Documentation](https://docs.cachix.org/)
- [Pre-commit Documentation](https://pre-commit.com/)

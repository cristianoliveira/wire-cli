# GitHub Actions CI Workflows

This directory contains the GitHub Actions CI configuration for the wire-cli project.

## Workflow: CI

The `ci.yml` workflow runs on every push to `main` and on all pull requests targeting `main`.

### Jobs

The workflow consists of 5 parallel jobs:

1. **format-check** - Runs ktlint to verify code formatting
2. **lint** - Runs detekt for static analysis
3. **test-unit** - Executes unit tests via Gradle
4. **test-integration** - Executes Bats integration tests
5. **build** - Verifies the project builds successfully with Nix

### Nix Integration

All jobs use Nix for reproducible builds:

- Installs Nix using the [cachix/install-nix-action](https://github.com/cachix/install-nix-action)
- Uses the Nix dev shell defined in `flake.nix`
- Caches Nix store using [cachix/cachix-action](https://github.com/cachix/cachix-action)

### Cachix Setup

To enable Nix store caching with Cachix:

1. Create a Cachix cache at https://cachix.org/
2. Add the `CACHIX_AUTH_TOKEN` secret to your GitHub repository:
   - Go to repository Settings → Secrets and variables → Actions
   - Click "New repository secret"
   - Name: `CACHIX_AUTH_TOKEN`
   - Value: Your Cachix authentication token
3. Update the `name` field in the `Setup Cachix` step from `wire-cli` to your cache name

### Quality Gates

The CI enforces the following quality gates (matching the Makefile):

- **Format**: `./gradlew ktlintCheck`
- **Lint**: `./gradlew detekt`
- **Unit Tests**: `./gradlew test`
- **Integration Tests**: `./gradlew batsTest`
- **Build**: `nix build .#cli`

### Test Results

Test results are uploaded as artifacts when tests fail or succeed:

- `unit-test-results` - Unit test reports
- `integration-test-results` - Integration test reports

### Local Testing

To test the workflow locally before pushing:

```bash
# Enter Nix dev shell
nix develop

# Run all checks locally
make format-check lint test

# Or run Gradle tasks directly
./gradlew check
```

### Troubleshooting

If CI jobs fail:

1. Check the job logs for specific error messages
2. Ensure git submodules are properly initialized: `git submodule update --init --recursive`
3. Verify the Nix flake is up to date: `nix flake update`
4. Test locally with the same commands used in CI

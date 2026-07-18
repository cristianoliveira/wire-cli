# GitHub Actions CI Workflows

This directory contains the GitHub Actions CI configuration for the wire project.

## Workflows

CI is split into two pipelines by speed so quick feedback (linters and unit
tests) is not blocked by the slower integration suite. Both pipelines run on
every push to `main` and on PRs targeting `main`/`develop`, and they share one
Gradle cache (keyed on gradle inputs) so the Kalium cold build is paid once
across runs instead of every run.

### `ci.yml` - Quick checks

Fast lane. Two parallel jobs:

1. **lint** - `ktlintCheck` and `detekt` (under the Nix dev shell)
2. **unit-tests** - `./gradlew test`

### `ci-integration.yml` - Integration tests

Slow lane. Builds the distributable and exercises it through Bats:

1. **integration-tests** - `./gradlew installDist`, smoke `wire --help`, then
   Bats integration tests under the Nix dev shell

Splitting the suite by speed lane cut wall-clock from ~30 min (single
sequential job) toward `max(quick lane, integration)`.

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
3. Update the `name` field in the `Setup Cachix` step from `wire` to your cache name

### Quality Gates

The CI enforces the following quality gates (matching the Makefile):

- **Format**: `./gradlew ktlintCheck`
- **Lint**: `./gradlew detekt`
- **Unit Tests**: `./gradlew test`
- **Integration Tests**: `bats --print-output-on-failure test/bats` (via the Nix dev shell)
- **Build**: `./gradlew installDist`

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

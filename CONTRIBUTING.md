# Contributing to wire

Thank you for your interest in contributing to wire!

## Development Setup

### Prerequisites

- [Nix](https://nixos.org/download.html) with flakes enabled
- Git

### Getting Started

```bash
# Clone the repository
git clone https://github.com/your-org/wire-cli.git
cd wire-cli

# Initialize git submodules
git submodule update --init --recursive

# Enter the Nix development shell
nix develop

# Run the project
./gradlew run --args='--help'
```

## Making Changes

### Code Style

The project uses:
- **ktlint** for Kotlin code formatting
- **detekt** for static analysis

Before submitting a PR, run:

```bash
make format  # Auto-format code
make format-check  # Check formatting
make lint  # Run static analysis
```

### Testing

```bash
# Run all tests
make test

# Run unit tests only
./gradlew test

# Run integration tests only
./gradlew batsTest

# Run a specific integration test
./gradlew batsTest -PbatsFilter="test_name"
```

### Pre-commit Hooks

Install pre-commit hooks to automatically run checks:

```bash
pre-commit install
pre-commit install --hook-type pre-push
```

## Submitting Changes

1. Create a branch for your feature/fix
2. Make your changes following the code style guidelines
3. Add tests for new functionality
4. Run all quality checks: `make all`
5. Commit and push your changes
6. Open a pull request

### Pull Request Checklist

- [ ] Code follows the project's style guidelines
- [ ] All tests pass (`make test`)
- [ ] Code is properly formatted (`make format-check`)
- [ ] Linting passes (`make lint`)
- [ ] Documentation is updated (if needed)
- [ ] Commit messages follow conventional commits

## CI/CD

All pull requests are automatically checked by GitHub Actions. See [docs/CI.md](docs/CI.md) for details on the CI pipeline.

## Project Structure

```
wire-cli/
├── src/               # Source code
├── test/              # Integration tests
├── docs/              # Documentation
├── vendor/kalium/     # Wire SDK (git submodule)
├── build.gradle.kts   # Gradle build configuration
├── flake.nix          # Nix flake configuration
└── Makefile           # Common commands
```

## Getting Help

- Check existing [GitHub Issues](https://github.com/your-org/wire-cli/issues)
- Read the [documentation](docs/)
- Ask questions in discussions

## Code of Conduct

Be respectful, inclusive, and collaborative. We expect all contributors to adhere to these standards.

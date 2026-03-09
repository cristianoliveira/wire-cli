# Kotlin CLI Architecture

## Purpose
This document defines the recommended architecture for a production-grade Kotlin CLI, based on current GitHub ecosystem practices (2026) and tailored for this repository.

## Architectural Principles
- Keep CLI code thin: parse input, invoke use cases, render output, map errors to exit codes.
- Keep business logic framework-agnostic and testable.
- Isolate side effects (filesystem, HTTP, process execution, persistence) behind adapters.
- Treat CLI UX (`--help`, error text, exit codes) as a stable contract.
- Optimize for maintainability and release reliability over early convenience.

## Recommended Stack
- **Language**: Kotlin (JVM)
- **CLI framework**: Clikt (default for Kotlin-first teams)
- **Build**: Gradle Kotlin DSL
- **Packaging**: shadow/fat JAR as baseline executable artifact
- **Optional distribution**: native binaries (separate lane), Homebrew/Scoop/SDKMAN as needed

## Standard Project Structure

```text
repo/
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  build-logic/                       # optional, recommended as project grows

  cli-app/                           # command definitions and presentation wiring
    src/main/kotlin/.../cli/
    src/main/resources/
    src/test/kotlin/
    src/test/resources/

  application/                       # use-cases and orchestration
    src/main/kotlin/.../application/
    src/test/kotlin/

  domain/                            # pure business entities/rules
    src/main/kotlin/.../domain/
    src/test/kotlin/

  infrastructure/                    # adapters: fs/http/process/persistence
    src/main/kotlin/.../infrastructure/
    src/test/kotlin/

  plugins/                           # optional ServiceLoader extensions
    src/main/kotlin/
    src/main/resources/META-INF/services/
```

## Layer Responsibilities
- **cli-app**
  - Owns command/subcommand tree, option parsing, help text, output formatting.
  - Converts framework exceptions to user-facing errors and exit codes.
  - Does not contain business rules.

- **application**
  - Owns use-case orchestration and input validation flow.
  - Returns typed outcomes (`Success`, `ValidationError`, `DomainError`).

- **domain**
  - Owns core entities, invariants, and business rules.
  - Has no dependency on CLI framework or infrastructure.

- **infrastructure**
  - Owns implementations for side effects and integrations.
  - Can be swapped/mocked in tests.

## Command Design Rules
- One class per command/subcommand when possible.
- Map options/arguments to typed use-case input models.
- Keep output rendering separate from execution logic.
- Define explicit, documented exit codes per command.

## Configuration Strategy
- Use layered configuration with clear precedence:
  1. CLI flags
  2. Environment variables
  3. Config file
  4. Defaults
- Validate configuration at startup and fail fast with actionable errors.

## Build and Release Best Practices
- Centralize versions in `gradle/libs.versions.toml`.
- Introduce `build-logic` convention plugins when module count grows.
- Publish snapshot builds from `main` and immutable releases from SemVer tags.
- Automate changelog generation and package-manager publishing.
- Keep native builds optional unless startup latency is a hard requirement.

## Wire SDK Integration Design
- For this repository's Wire-specific command architecture and SDK usage patterns, see:
  - `docs/architecture/wire-sdk-cli-design.md`
  - `docs/architecture/real-auth-system-design.md`
- These docs are validated against local reference implementations under:
  - `.local/kalium` (official SDK/sample auth flow)
  - `.local/wiretui` (practical app-level auth/session handling)

## Source Basis
This architecture is synthesized from observed patterns in active Kotlin CLI repositories and framework guidance, including detekt, ktlint, kotlinx-kover, jbang, Clikt docs, picocli docs, Kotlin docs, and Gradle docs.

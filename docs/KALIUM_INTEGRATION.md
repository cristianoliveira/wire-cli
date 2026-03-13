# Kalium Integration Setup

This document explains how to work with Kalium integration in the wire-cli project.

## Overview

wire-cli uses the same local-source composite build pattern as wiretui.

- Kalium is consumed from source via `includeBuild(...)`
- Default Kalium location is `.local/kalium`
- You can override location with `-Pkalium.dir=/path/to/kalium` or `KALIUM_DIR`
- No Maven-hosted Kalium artifact is required

## Usage

### Build and test

```bash
nix develop -c gradle check
```

### Run with real SDK backend

```bash
nix develop -c gradle run --args="login --email <email> --password <password>"
```

Real backend is the runtime default. Use `WIRE_BACKEND=stub` only when you need deterministic fake behavior.

### Override Kalium path

```bash
KALIUM_DIR=/absolute/path/to/kalium nix develop -c gradle check
# or
nix develop -c gradle -Pkalium.dir=/absolute/path/to/kalium check
```

## Implementation Details

### settings.gradle.kts

- Resolves Kalium location from `kalium.dir` property or `KALIUM_DIR` env var
- Requires the directory to exist
- Uses composite build dependency substitution:

```kotlin
includeBuild(kaliumDirFile) {
    dependencySubstitution {
        substitute(module("com.wire:logic")).using(project(":logic"))
    }
}
```

### build.gradle.kts

- Depends on `com.wire:logic`, resolved by the local composite build
- Runtime backend selection defaults to `real`; set `WIRE_BACKEND=stub` for deterministic fake mode

## Architecture Notes

- No source exclusion or reflection fallback is used for Kalium wiring
- Real mode uses concrete SDK runtimes (`SdkKaliumAuthRuntime`, `SdkKaliumProfileRuntime`)
- Stub mode remains available for deterministic local tests

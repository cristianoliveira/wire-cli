# Setup

## Requirements

From Kalium repository docs:

- JDK 21
- Git
- Gradle via project wrapper
- Native libraries for JVM tests/CLI: `libsodium`, `cryptobox-c`, `cryptobox4j`, AVS where needed
- macOS Apple Silicon for iOS simulator/native builds

## Artifacts

Kalium release automation publishes two main outputs:

| Artifact | Intended use |
| --- | --- |
| `logic-android-aar` | Android-only integration |
| `logic-kmp` | Kotlin Multiplatform integration for Android, JVM, iOS metadata/framework outputs |

## Compile-time flags

Consumer builds must set relevant Gradle properties.

```bash
-PUSE_UNIFIED_CORE_CRYPTO=true
-Pkalium.providerCacheScope=GLOBAL
```

### `USE_UNIFIED_CORE_CRYPTO`

Controls crypto dependency mode.

- `false`: platform-specific crypto artifacts.
- `true`: unified `core-crypto-kmp` artifact. Required for KMP bundle and iOS/JS flows.

### `kalium.providerCacheScope`

Required; Kalium has no default.

Allowed values:

- `GLOBAL`: provider instances share process-global cache maps.
- `LOCAL`: each provider instance owns a local cache map.

Current consumers:

- `UserStorageProvider`
- `UserAuthenticatedNetworkProvider`

## JVM native libraries

When running JVM tests or CLI tasks that need native libraries, pass:

```bash
-Djava.library.path=./native/libs
```

Build native dependencies on macOS:

```bash
make
```

## Example Gradle invocations

Android AAR:

```bash
./gradlew :logic:bundleAndroidMainAar \
  -PUSE_UNIFIED_CORE_CRYPTO=false \
  -Pkalium.providerCacheScope=GLOBAL
```

KMP bundle:

```bash
./gradlew :logic:bundleAndroidMainAar :logic:jvmJar :logic:allMetadataJar :logic:sourcesJar :logic:assembleKaliumLogicReleaseXCFramework \
  -PUSE_UNIFIED_CORE_CRYPTO=true \
  -Pkalium.providerCacheScope=GLOBAL
```

JVM CLI:

```bash
./gradlew :sample:cli:assemble
java -Djava.library.path=./native/libs -jar sample/cli/build/libs/cli.jar login --email <email> --password <password> listen-group
```

## Supported platforms

- Android
- JVM
- iOS / Apple targets, with unified CoreCrypto
- JavaScript, limited support

## Recommended app integration package boundary

In a real app, wrap Kalium behind your own small adapter/service layer:

```text
app UI/view models
  -> app messaging/session service
  -> Kalium CoreLogic/UserSessionScope
```

Benefits:

- isolates SDK upgrade impact
- centralizes error/result mapping
- keeps UI free from SDK construction details
- makes tests deterministic with fake app service interfaces

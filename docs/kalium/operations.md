# Operational notes

## Logging

Kalium uses internal logging infrastructure. Keep app stdout clean for CLIs and scripting. Avoid logging secrets, tokens, passphrases, message plaintext, or cryptographic material.

## Native libraries on JVM

JVM tests/CLI require native libraries for crypto/calling flows.

```bash
-Djava.library.path=./native/libs
```

Build dependencies on macOS:

```bash
make
```

## iOS

Use unified CoreCrypto:

```bash
-PUSE_UNIFIED_CORE_CRYPTO=true
```

Typical iOS simulator task:

```bash
./gradlew iosSimulatorArm64Test -PUSE_UNIFIED_CORE_CRYPTO=true -Pkalium.providerCacheScope=GLOBAL
```

See upstream `docs/IOS_BUILD.md` for target-specific details.

## Android

Use Android `CoreLogic` constructor with application context. Do not pass Activity context.

```kotlin
CoreLogic(
    userAgent = "MyApp/1.0",
    appContext = context.applicationContext,
    rootPath = context.filesDir.absolutePath,
    kaliumConfigs = KaliumConfigs()
)
```

## Testing strategy in apps

Recommended boundary:

```text
ViewModel -> App service interface -> Kalium adapter -> UserSessionScope
```

Unit test your app service/view models with fakes. Use Kalium integration tests sparingly because they need storage, coroutines, network fakes, and native libs for JVM.

## Error handling

Kalium data/domain layers often use `Either<Failure, Success>` internally, while `:logic` exposes concrete result types for app consumers. Check each use case return type and map it into app-specific UI/domain errors.

## Sync health

If UI shows stale data:

1. Verify session scope exists for selected account.
2. Verify client is registered.
3. Verify sync was requested and reached live state.
4. Verify network state observer says online.
5. Inspect event processing/logs.

## Common pitfalls

- Forgetting `-Djava.library.path=./native/libs` on JVM.
- Forgetting `-PUSE_UNIFIED_CORE_CRYPTO=true` for iOS/KMP flows.
- Forgetting `-Pkalium.providerCacheScope=GLOBAL|LOCAL`.
- Creating multiple independent `CoreLogic` instances for one app process/storage root.
- Expecting complete message/conversation data before sync.
- Calling user-session APIs before client registration.
- Logging sensitive auth/backup/crypto data.

## Upgrade guidance

Before upgrading Kalium:

1. Review public scope API changes in `:logic`.
2. Re-run app adapter tests.
3. Re-run one real login/sync/send-message smoke test.
4. Re-run backup/calling flows if app uses them.
5. Confirm Gradle flags and native library versions.

## Smoke test checklist

- App starts and creates `CoreLogic`.
- Existing sessions are observed.
- Login succeeds.
- User session scope is created.
- Client is registered/fetched.
- Sync reaches live state.
- Conversation list appears.
- Text message sends.
- Incoming message appears after sync/websocket event.
- Logout clears active account state.

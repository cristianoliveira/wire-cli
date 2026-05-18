# Architecture

Kalium is split into layers. Application code should normally depend on `:logic` and interact with `CoreLogic` plus scopes/use cases.

## Module layers

```text
core/*      shared primitives, data models, crypto wrappers, logging, utilities
data/*      network, persistence, protobuf, DTO mappers
 domain/*   feature/domain modules such as backup, cells, messaging, work
logic       public SDK entrypoint and use-case orchestration
sample/*    CLI and examples
```

Dependency direction is intentionally layered:

```text
core -> data -> domain -> logic
```

## Runtime scopes

### `CoreLogic`

Platform-specific SDK root. It owns global database/preferences, providers, schedulers, network state, and the global call manager.

Common API:

```kotlin
val coreLogic = CoreLogic(...)
val global = coreLogic.getGlobalScope()
val session = coreLogic.getSessionScope(userId)
```

Platform constructors differ:

- JVM: `logic/src/jvmMain/kotlin/com/wire/kalium/logic/CoreLogic.kt`
- Android: `logic/src/androidMain/kotlin/com/wire/kalium/logic/CoreLogic.kt`
- Apple/iOS: `logic/src/appleMain/kotlin/com/wire/kalium/logic/CoreLogic.kt`

### `GlobalKaliumScope`

App-wide/account-wide scope. Use before and between user sessions.

Responsibilities:

- server configuration and API version updates
- validation helpers
- session/account queries
- persistent websocket settings
- notification token persistence
- creating authentication scope by server config
- observing valid accounts

### `AuthenticationScope`

Unauthenticated server-bound scope. Use for login, SSO, registration, domain lookup, and second-factor verification.

### `UserSessionScope`

Logged-in user scope. This is the main application surface once a session exists.

It exposes feature scopes:

```kotlin
session.messages
session.conversations
session.calls
session.users
session.client
session.connections
session.search
session.team
session.backup
session.multiPlatformBackup
session.channels
session.debug
```

It also exposes session operations:

```kotlin
session.syncExecutor
session.logout
session.observeLegalHoldForSelfUser
session.observeFileSharingStatus
session.persistPersistentWebSocketConnectionStatus
```

## Use-case style

Most APIs are small use-case objects exposed as scope properties. Invoke them directly:

```kotlin
session.messages.sendTextMessage(
    conversationId = conversationId,
    text = "Hello"
)
```

Some use cases are `suspend`, some return `Flow`, and some return result sealed classes. Keep API calls inside coroutine-aware application services/view models.

## Storage and encryption

Kalium persists global and user-scoped data. Important constructor/config concepts:

- `rootPath`: application-controlled root storage path.
- `KaliumConfigs.shouldEncryptData()`: controls encrypted persistence where supported.
- `useInMemoryStorage` on JVM: useful for tests and tools.
- `kalium.providerCacheScope`: compile-time provider cache policy, required by consumer builds.

## Networking

Network containers are created internally from server config, user agent, proxy credentials, certificate pinning config, and optional mocked requests.

Application code generally should not call network/data modules directly. Use `:logic` scopes instead.

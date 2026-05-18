# Bootstrapping

## JVM

```kotlin
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.featureFlags.KaliumConfigs

val coreLogic = CoreLogic(
    rootPath = "/var/lib/my-app/kalium",
    kaliumConfigs = KaliumConfigs(),
    userAgent = "MyApp/1.0",
    useInMemoryStorage = false
)
```

For tests/tools, `useInMemoryStorage = true` avoids file-backed global DB storage.

## Android

```kotlin
import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.featureFlags.KaliumConfigs

val coreLogic = CoreLogic(
    userAgent = "MyApp/1.0",
    appContext = context.applicationContext,
    rootPath = context.filesDir.absolutePath,
    kaliumConfigs = KaliumConfigs()
)
```

Use application context, not Activity context.

## Access global scope

```kotlin
val global = coreLogic.getGlobalScope()
```

Use `global` for app/account/session-wide operations:

```kotlin
val sessions = global.getSessions()
val validAccountsFlow = global.observeValidAccounts()
```

## Access user session scope

Once you have a `UserId` from a persisted or newly authenticated session:

```kotlin
val session = coreLogic.getSessionScope(userId)
```

Then use feature scopes:

```kotlin
val messages = session.messages
val conversations = session.conversations
val users = session.users
```

## Recommended lifecycle

### App start

```text
construct CoreLogic
observe valid sessions
for selected account: create UserSessionScope
start sync
observe data flows
```

### Login

```text
fetch/choose server config
create AuthenticationScope
login/register/SSO
persist authenticated account
create UserSessionScope
register/fetch client if needed
start sync
```

### Logout

```kotlin
session.logout()
```

Logout also coordinates client data clearing, push token deregistration, ending calls, and session scope cleanup.

## Threading

Kalium uses coroutines. Treat most calls as suspend or flow-based operations.

Recommended app pattern:

```kotlin
class MessagingService(
    private val session: UserSessionScope,
    private val scope: CoroutineScope
) {
    suspend fun sendText(conversationId: ConversationId, text: String) {
        session.messages.sendTextMessage(conversationId, text)
    }
}
```

## Storage path guidance

Use stable, app-owned storage:

- Android: `context.filesDir.absolutePath`
- JVM desktop/server: app data directory, not temp directory
- Tests: temp directory or in-memory storage

Avoid sharing one storage root across unrelated app environments.

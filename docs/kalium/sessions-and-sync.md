# Sessions and sync

## Session APIs

Global session APIs:

```kotlin
global.getSessions
global.getAllSessions
global.doesValidSessionExist
global.observeValidAccounts
global.observeAllValidSessionsFlow
global.deleteSession
global.session
```

`SessionScope` exposes:

```kotlin
sessionScope.allSessions
sessionScope.allSessionsIncludingInvalid
sessionScope.allSessionsFlow
sessionScope.currentSession
sessionScope.currentSessionFlow
sessionScope.updateCurrentSession
```

## Creating a user session scope

```kotlin
val session = coreLogic.getSessionScope(userId)
```

`UserSessionScope` is per logged-in user. Reuse it for the lifetime of the app account session.

## Client/device prerequisite

A user session generally needs a registered client/device before full messaging/sync works.

Relevant APIs:

```kotlin
session.client.needsToRegisterClient
session.client.getOrRegister
session.client.observeCurrentClientId
session.client.fetchSelfClients
session.client.deleteClient
```

## Starting sync

`UserSessionScope` exposes:

```kotlin
session.syncExecutor
```

Typical flow:

```kotlin
session.syncExecutor.request {
    waitUntilLiveOrFailure()
}
```

Sync internals include:

- slow sync
- incremental sync
- event gathering
- event processing
- recovery handlers
- MLS recovery
- conversation recovery
- local event processing

## What sync does

Slow sync fetches baseline state:

- self user
- user properties
- feature configs
- conversations
- connections
- team info
- contacts
- legal hold state
- MLS group joins/recovery
- pending one-to-one resolutions
- Nomad messages during slow sync when configured

Incremental sync processes backend events after baseline state is ready:

- new messages
- conversation/member events
- user/team/feature events
- typing indicators
- MLS welcomes/resets
- call events

## Observing data

Most app data should be observed through feature scopes after sync starts:

```kotlin
session.conversations.observeConversationListDetails
session.messages.observeMessageById
session.users.observeSelfUser
session.calls.observeOngoingCalls
```

## Persistent websocket status

Global:

```kotlin
global.observePersistentWebSocketConnectionStatus
global.setAllPersistentWebSocketEnabled
```

Per user:

```kotlin
session.persistPersistentWebSocketConnectionStatus
session.getPersistentWebSocketStatus
```

## Logout

```kotlin
session.logout()
```

Logout coordinates:

- remote/client logout repository work
- session repository update
- client data clearing
- push token deregistration
- user data clearing
- call termination
- session scope cleanup

## Practical integration pattern

```kotlin
class AccountRuntime(
    val userId: UserId,
    val session: UserSessionScope,
    private val appScope: CoroutineScope
) {
    fun start() {
        appScope.launch {
            session.syncExecutor.request { waitUntilLiveOrFailure() }
        }
    }

    suspend fun logout() {
        session.logout()
    }
}
```

## Reliability notes

- Start sync before expecting complete conversation/message lists.
- Treat sync failures as recoverable; surface retry state in UI.
- Keep session scope alive while account is active.
- End calls before or during logout; Kalium's logout use case already integrates call cleanup.
- Avoid creating many session scopes for same user; use `CoreLogic.getSessionScope(userId)` and reuse.

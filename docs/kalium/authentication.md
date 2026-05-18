# Authentication

Authentication starts from a server configuration and uses `AuthenticationScope`.

Source anchors:

- `GlobalKaliumScope.authenticationScopeForConfigId`
- `CoreLogicCommon.versionedAuthenticationScope`
- `AuthenticationScope.login`
- `AuthenticationScope.registerScope`
- `AuthenticationScope.ssoLoginScope`
- `AuthenticationScope.domainLookup`

## Global validation helpers

`GlobalKaliumScope` exposes local validation use cases:

```kotlin
global.validateEmailUseCase
global.validatePasswordUseCase
global.validateUserHandleUseCase
global.validateSSOCodeUseCase
```

Use these before remote calls for fast user feedback.

## Server config

Use global APIs to fetch/resolve server configuration:

```kotlin
val config = global.fetchServerConfigFromDeepLink(/* deep link or config source */)
```

For previously saved config IDs:

```kotlin
val authScope = global.authenticationScopeForConfigId(/* config id */)
```

For version-aware auth from server links:

```kotlin
val authScope = coreLogic.versionedAuthenticationScope(serverLinks)
```

Exact result types vary by use case. Handle success/failure explicitly.

## Email/password login

Conceptual flow:

```kotlin
val auth = /* AuthenticationScope for selected server */

val loginResult = auth.login(
    email = email,
    password = password
)

when (loginResult) {
    is LoginResult.Success -> {
        global.addAuthenticatedAccount(/* session/account from result */)
        val session = coreLogic.getSessionScope(loginResult.userId)
    }
    is LoginResult.Failure -> {
        // show mapped error
    }
}
```

The exact result model should be checked from `LoginUseCase` in the current Kalium version.

## SSO login

Use `AuthenticationScope.ssoLoginScope`:

```kotlin
val sso = auth.ssoLoginScope
```

Public SSO capabilities include:

- initiate SSO login
- finalize SSO login
- get SSO login session
- fetch SSO settings
- validate SSO code
- SSO metadata/settings

## Domain lookup

Use when the user's domain determines auth flow or backend config:

```kotlin
val lookup = auth.domainLookup
```

Domain lookup can help decide email/password vs SSO and server routing.

## Registration

Use:

```kotlin
val register = auth.registerScope
```

Registration APIs live under `RegisterScope` and use the same server config/proxy context.

## Second factor verification

Use:

```kotlin
auth.requestSecondFactorVerificationCode
```

Common use:

```text
login requires second factor
request code
user enters code
retry/finalize login with verification code
```

## After authentication

After successful auth:

1. Persist authenticated account/session through global account APIs.
2. Create user session scope:

```kotlin
val session = coreLogic.getSessionScope(userId)
```

3. Register/fetch local client if needed:

```kotlin
session.client.needsToRegisterClient
session.client.getOrRegister
```

4. Start sync:

```kotlin
session.syncExecutor.request { waitUntilLiveOrFailure() }
```

## Practical advice

- Keep login orchestration in one application service.
- Map Kalium auth errors into app-facing UI states.
- Do not expose raw SDK result models all the way to UI unless your app intentionally couples to Kalium.
- Persist selected account/user ID in your app state only after Kalium session persistence succeeds.

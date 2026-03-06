# Wire SDK CLI Design

## Purpose
Define the concrete code design for implementing `login`, `logout`, and `profile` with the Wire SDK in this repository, while keeping command code small and testable.

## Design Overview

Use a thin command layer and move SDK orchestration into services.

```text
src/main/kotlin/com/example/wirecli/
  Main.kt
  commands/
    LoginCommand.kt
    LogoutCommand.kt
    ProfileCommand.kt
  auth/
    AuthSessionService.kt
    SessionResolver.kt
    AuthErrorMapper.kt
    ExitCodes.kt
  profile/
    ProfileService.kt
    ProfileFormatter.kt
  runtime/
    KaliumRuntime.kt
```

## Layer Responsibilities

- `commands/*`
  - Parse CLI args/options.
  - Call service methods.
  - Print output and return exit codes.
  - No direct SDK flow choreography.

- `runtime/KaliumRuntime`
  - Create and own `CoreLogic`.
  - Provide stable access to global/session scopes.
  - Own data directory and runtime bootstrap choices.

- `auth/*`
  - Implement login/session/logout orchestration.
  - Centralize auth-state checks for protected commands.
  - Map SDK failures to actionable messages and stable exit codes.

- `profile/*`
  - Fetch current user profile through authenticated session.
  - Format output deterministically (`name`, `email` first).

## Recommended Login Sequence

The login service should run this sequence in order:

1. Resolve server config and authentication scope.
2. Perform SDK login with email/password.
3. Persist authenticated account/session.
4. Resolve user session scope.
5. Register or get current client/device.
6. Keep sync alive for a healthy session lifecycle.

Do not treat login as complete until step 3 succeeds. Authentication without persisted account state will not satisfy session persistence requirements.
For one-shot commands, keep sync only as long as needed for session health/bootstrap, then shut down cleanly.

## Session Model

- Keep a resolver that can:
  - choose the active valid session for normal command execution,
  - inspect all sessions for diagnostics and recovery hints.
- On each CLI process start, protected commands must resolve persisted sessions from storage before deciding auth state.
- Protected commands (`profile`) should fail fast when there is no valid session.
- Recovery guidance should always tell the user how to re-authenticate.

## Logout Model

- MVP behavior: logout current active session and remove local persisted active auth state for that session so protected commands require re-authentication.
- After logout, protected commands must return unauthorized and non-zero exit.
- Optional hard-logout mode can be added later.

## Error and Exit Code Contract

Centralize this in `AuthErrorMapper` + `ExitCodes`.

Suggested baseline:

```kotlin
object ExitCodes {
    const val OK = 0
    const val AUTH_FAILED = 10
    const val UNAUTHORIZED = 11
    const val NETWORK_ERROR = 12
    const val SERVER_ERROR = 13
    const val VALIDATION_ERROR = 14
    const val UNKNOWN_ERROR = 1
}
```

Rules:
- Invalid credentials -> concise actionable message, `AUTH_FAILED`.
- Missing/expired/invalid session on protected command -> re-login guidance, `UNAUTHORIZED`.
- Upstream network/server failures -> short cause + next action, non-zero.

## Service Interfaces (Example)

```kotlin
package com.example.wirecli.auth

data class LoginInput(
    val email: String,
    val password: String,
    val server: String?
)

sealed interface AuthResult {
    data class Success(val userId: String) : AuthResult
    data class Failure(val message: String, val exitCode: Int) : AuthResult
}

interface AuthSessionService {
    suspend fun login(input: LoginInput): AuthResult
    suspend fun logout(): AuthResult
    suspend fun requireActiveSession(): AuthResult
}
```

```kotlin
package com.example.wirecli.profile

data class ProfileView(
    val name: String?,
    val email: String?,
    val handle: String?
)

sealed interface ProfileResult {
    data class Success(val profile: ProfileView) : ProfileResult
    data class Failure(val message: String, val exitCode: Int) : ProfileResult
}

interface ProfileService {
    suspend fun getCurrentProfile(): ProfileResult
}
```

## Command Flow (Example)

```kotlin
class ProfileCommand(
    private val profileService: ProfileService,
    private val formatter: ProfileFormatter
) {
    suspend fun run(): Int {
        return when (val result = profileService.getCurrentProfile()) {
            is ProfileResult.Success -> {
                println(formatter.human(result.profile))
                ExitCodes.OK
            }
            is ProfileResult.Failure -> {
                System.err.println(result.message)
                result.exitCode
            }
        }
    }
}
```

## Output Contract for `profile`

Human-readable output should be stable and safe for missing fields:

```text
Name: Jane Doe
Email: jane@example.com
Handle: -
```

Formatting rules:
- Always print `Name` and `Email` labels.
- If optional values are absent, print `-`.
- Keep output deterministic for reliable tests.

## Test Strategy

- Unit tests per command:
  - success paths,
  - auth failures,
  - unauthorized behavior,
  - exit code checks.
- Service tests:
  - login sequence correctness,
  - session persistence expectations,
  - logout invalidation.
- Integration smoke test:
  - login -> profile -> logout -> profile denied.

## Implementation Notes

- Keep all SDK-specific translation inside services, not commands.
- Prefer typed results over exceptions crossing command boundaries.
- Keep command classes small so story acceptance criteria map one-to-one to tests.

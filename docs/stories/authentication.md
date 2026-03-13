# Authentication Stories

## Implemented Stories

### 1) Auth: login command `[Done] [Must]`
As a user, I want to log in with my email and password so I can access protected account data.

Acceptance criteria:
- Given I am not authenticated, when I run the login command with valid credentials, then I am authenticated.
- Given authentication succeeds, when the command completes, then I see a success confirmation.
- Given credentials are invalid, when I run login, then authentication is rejected and no authenticated session is created.

### 2) Auth: session persistence `[Done] [Must]`
As a user, I want my session to persist between CLI runs so I do not need to log in every time.

Acceptance criteria:
- Given I have logged in successfully, when I run another command in a new CLI process, then the session is reused.
- Given a persisted session exists, when a protected command is executed, then login is not required again.

### 3) Auth: logout command `[Done] [Must]`
As a user, I want to log out so my local session is cleared.

Acceptance criteria:
- Given I am authenticated, when I run logout, then the local session is removed.
- Given I have logged out, when I run a protected command, then I am prompted to authenticate.

### 4) Auth: auth failure handling `[Done] [Must]`
As a user, I want clear authentication failure messages so I know how to recover.

Acceptance criteria:
- Given authentication fails, when the command returns, then I see a concise, actionable error message.
- Given authentication fails, when the command exits, then it returns a non-zero status code.
- Given auth data is missing or expired, when a protected command runs, then it asks me to log in again.

## Current CLI Contract (Auth)

### `wire login`

- Required option: `--email <email>`
- Password input options:
  - Preferred: interactive prompt (default when no password flags are provided)
  - Preferred for automation: `--password-stdin`
  - Supported with warning: `--password <secret>` (deprecated due to shell/process exposure risk)
- Optional server override: `--server <staging|production|invite-link-or-config-url>`
- Validation guardrail: using `--password` with `--password-stdin` returns exit code `14`.

### `wire logout`

- On success prints `Logged out.` and clears the local active session marker.
- If no valid session exists, returns unauthorized guidance and exit code `11`.

## Test lane notes

- Stub lane (deterministic): default backend, no `WIRE_BACKEND` env var required.
- Real-auth lane (live): `WIRE_BACKEND=real` with `WIRE_REAL_EMAIL`/`WIRE_REAL_PASSWORD`.
- Real-auth smoke should use `--password-stdin` to avoid credential exposure in process args.

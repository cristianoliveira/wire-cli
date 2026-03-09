---
title: MVP
date: 06-03-2026
---

# MVP - Stories

This is the list of stories required to implement the wire-cli MVP.
The wire-cli MVP is a command-line tool that lets users interact with the Wire ecosystem.

## Authentication

### 1) Auth: login command `[Must]`
As a user, I want to log in with my email and password so I can access protected account data.

Acceptance criteria:
- Given I am not authenticated, when I run the login command with valid credentials, then I am authenticated.
- Given authentication succeeds, when the command completes, then I see a success confirmation.
- Given credentials are invalid, when I run login, then authentication is rejected and no authenticated session is created.

### 2) Auth: session persistence `[Must]`
As a user, I want my session to persist between CLI runs so I do not need to log in every time.

Acceptance criteria:
- Given I have logged in successfully, when I run another command in a new CLI process, then the session is reused.
- Given a persisted session exists, when a protected command is executed, then login is not required again.

### 3) Auth: logout command `[Must]`
As a user, I want to log out so my local session is cleared.

Acceptance criteria:
- Given I am authenticated, when I run logout, then the local session is removed.
- Given I have logged out, when I run a protected command, then I am prompted to authenticate.

### 4) Auth: auth failure handling `[Must]`
As a user, I want clear authentication failure messages so I know how to recover.

Acceptance criteria:
- Given authentication fails, when the command returns, then I see a concise, actionable error message.
- Given authentication fails, when the command exits, then it returns a non-zero status code.
- Given auth data is missing or expired, when a protected command runs, then it asks me to log in again.

## Profile

### 5) Profile: fetch profile `[Must]`
As an authenticated user, I want the CLI to retrieve my profile so I can inspect my account details.

Acceptance criteria:
- Given I am authenticated, when I request profile data, then the CLI fetches the current user's profile.
- Given the profile fetch fails due to network or server error, when the command returns, then I see a clear error message.

### 6) Profile: display profile `[Must]`
As an authenticated user, I want profile data displayed clearly so I can read key fields quickly.

Acceptance criteria:
- Given profile data is fetched successfully, when it is displayed, then name and email are shown in a readable format.
- Given optional fields are missing, when profile output is rendered, then output remains valid and understandable.

### 7) Profile: unauthorized state `[Must]`
As a user, I want profile access to require authentication so unauthorized requests are handled safely.

Acceptance criteria:
- Given I am not authenticated, when I run the profile command, then access is denied and I am prompted to log in.
- Given my session is invalid or expired, when I run the profile command, then I receive an unauthorized response and recovery guidance.

## Real backend smoke commands

- `WIRE_BACKEND=real WIRE_REAL_EMAIL='<email>' WIRE_REAL_PASSWORD='<password>' ./build/install/wire-cli/bin/wire-cli login --email "$WIRE_REAL_EMAIL" --password "$WIRE_REAL_PASSWORD"`
- `WIRE_BACKEND=real ./build/install/wire-cli/bin/wire-cli profile`
- `WIRE_BACKEND=real ./build/install/wire-cli/bin/wire-cli logout`
- Optional custom backend: include `--server '<staging|production|invite-link-or-config-url>'` on `login`.

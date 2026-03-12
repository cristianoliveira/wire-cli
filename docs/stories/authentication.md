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

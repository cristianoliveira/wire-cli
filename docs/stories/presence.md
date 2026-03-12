# Presence Stories

## Planned Stories (Next)

## Presence model

- Supported states: `online`, `busy`, `away`, `offline`, `unknown`.
- Backend values that are missing, undefined, or not recognized map to `unknown`.

### 8) Presence: fetch current user status `[Planned] [Must]`
As an authenticated user, I want the CLI to fetch my current presence status so I can see whether I am online, busy, away, offline, or unknown.

Acceptance criteria:
- Given I am authenticated, when I request presence, then the CLI fetches the current user's status from the backend.
- Given the backend returns an undefined, null, or unsupported status value, when the CLI parses presence, then it maps the value to `unknown`.
- Given I am not authenticated or my session is invalid, when I request presence, then access is denied and I am prompted to log in.
- Given the backend is unavailable or returns an error, when I request presence, then I see a clear failure message and the command exits non-zero.

### 9) Presence: display in profile output `[Planned] [Must]`
As an authenticated user, I want my presence shown in `wire profile` output so profile and status are visible together.

Acceptance criteria:
- Given profile and presence are available, when I run `wire profile`, then output includes a `presence` field with one of `online|busy|away|offline|unknown`.
- Given presence cannot be fetched due to backend failure, when I run `wire profile`, then profile output remains readable and presence is shown as `unknown`.
- Given I am unauthorized, when I run `wire profile`, then the command reports unauthorized and does not expose protected profile or presence data.

### 10) Presence: normalize backend status values `[Planned] [Must]`
As a developer, I want a single normalization rule for backend presence values so CLI output is predictable across environments.

Acceptance criteria:
- Given backend value `online`, when normalized, then output is `online`.
- Given backend value `busy`, when normalized, then output is `busy`.
- Given backend values `away` or `not_available`, when normalized, then output is `away`.
- Given backend value `offline`, when normalized, then output is `offline`.
- Given backend value is missing, undefined, null, empty, or unknown, when normalized, then output is `unknown`.

### 11) Presence: graceful failure and auth handling `[Planned] [Should]`
As a user, I want presence-related errors to be handled clearly so I understand whether to retry, log in, or continue with partial output.

Acceptance criteria:
- Given presence fetch returns unauthorized, when command output is rendered, then I see an auth recovery message (log in again) and a non-zero exit on presence-only commands.
- Given presence fetch fails due to timeout/network/server error, when command output is rendered, then I see a concise retry-oriented message and presence defaults to `unknown` where partial output is allowed.
- Given both profile and presence fail in `wire profile`, when the command completes, then I see a single clear failure response and non-zero exit.

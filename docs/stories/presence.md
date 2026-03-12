# Presence Stories

## Implemented Stories

## Presence model

- `wire presence set <status>` accepts only: `online`, `busy`, `away`, `offline`.
- `wire presence get` can return: `online`, `busy`, `away`, `offline`, `unknown`.
- `wire presence` remains a compatibility alias for `wire presence get`.
- Backend values that are missing, undefined, empty, or not recognized map to `unknown`.

### 8) Presence: `wire presence get` retrieves current status `[Implemented] [Must]`
As an authenticated user, I want `wire presence get` so I can check my current presence status.

Acceptance criteria:
- Given I am authenticated, when I run `wire presence get`, then the CLI fetches and prints one status value.
- Given the backend returns undefined, null, empty, or unsupported status, when I run `wire presence get`, then the CLI prints `unknown`.
- Given I am not authenticated or my session is invalid, when I run `wire presence get`, then access is denied with a login prompt and non-zero exit.
- Given a timeout, network failure, or server error, when I run `wire presence get`, then I see a clear failure message and a non-zero exit.

### 9) Presence: `wire presence set <status>` updates current status `[Implemented] [Must]`
As an authenticated user, I want `wire presence set <status>` so I can change my current presence.

Acceptance criteria:
- Given I am authenticated and provide `online|busy|away|offline`, when I run `wire presence set <status>`, then the backend is updated and the CLI confirms the applied status.
- Given I provide any other status value, when I run `wire presence set <status>`, then the CLI rejects input with usage guidance and non-zero exit.
- Given I am not authenticated or my session is invalid, when I run `wire presence set <status>`, then access is denied with a login prompt and non-zero exit.
- Given a timeout, network failure, or server error, when I run `wire presence set <status>`, then I see a clear failure message and a non-zero exit.

### 10) Presence: display in `wire profile` output `[Implemented] [Must]`
As an authenticated user, I want presence shown in `wire profile` output so profile and status are visible together.

Acceptance criteria:
- Given profile and presence are available, when I run `wire profile`, then output includes `presence` with one of `online|busy|away|offline|unknown`.
- Given presence cannot be fetched due to backend failure, when I run `wire profile`, then profile output remains readable and presence is shown as `unknown`.
- Given I am unauthorized, when I run `wire profile`, then the command reports unauthorized and does not expose protected profile or presence data.

### 11) Presence: normalize backend status values `[Implemented] [Must]`
As a developer, I want one normalization rule for presence values so `wire presence get` and `wire profile` are predictable.

Acceptance criteria:
- Given backend value `online`, when normalized, then output is `online`.
- Given backend value `busy`, when normalized, then output is `busy`.
- Given backend values `away` or `not_available`, when normalized, then output is `away`.
- Given backend value `offline`, when normalized, then output is `offline`.
- Given backend value is missing, undefined, null, empty, or unknown, when normalized, then output is `unknown`.

### 12) Presence: graceful failure and auth handling across commands `[Implemented] [Should]`
As a user, I want presence command errors handled consistently so I know whether to retry, log in, or continue.

Acceptance criteria:
- Given `wire presence get` or `wire presence set <status>` returns unauthorized, when output is rendered, then I see an auth recovery message and the command exits non-zero.
- Given `wire presence get` or `wire presence set <status>` fails due to timeout/network/server error, when output is rendered, then I see a concise retry-oriented message and the command exits non-zero.
- Given `wire profile` cannot fetch presence due to timeout/network/server error, when output is rendered, then profile remains readable and presence is `unknown`.

# Profile Stories

## Implemented Stories

### 5) Profile: fetch profile `[Done] [Must]`
As an authenticated user, I want the CLI to retrieve my profile so I can inspect my account details.

Acceptance criteria:
- Given I am authenticated, when I request profile data, then the CLI fetches the current user's profile.
- Given the profile fetch fails due to network or server error, when the command returns, then I see a clear error message.

### 6) Profile: display profile `[Done] [Must]`
As an authenticated user, I want profile data displayed clearly so I can read key fields quickly.

Acceptance criteria:
- Given profile data is fetched successfully, when it is displayed, then name and email are shown in a readable format.
- Given optional fields are missing, when profile output is rendered, then output remains valid and understandable.

### 7) Profile: unauthorized state `[Done] [Must]`
As a user, I want profile access to require authentication so unauthorized requests are handled safely.

Acceptance criteria:
- Given I am not authenticated, when I run the profile command, then access is denied and I am prompted to log in.
- Given my session is invalid or expired, when I run the profile command, then I receive an unauthorized response and recovery guidance.

## Current CLI Contract (Profile)

- Success output is deterministic and line-oriented:
  - `Name: <value|->`
  - `Email: <value|->`
  - `Handle: <value|->`
  - `Presence: <online|busy|away|offline|unknown>`
- Missing optional values render as `-`.
- Unauthorized/missing sessions return exit code `11` with re-auth guidance.
- Network and server failures map to exit codes `12` and `13` with actionable retry messages.

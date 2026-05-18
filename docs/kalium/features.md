# Feature overview

All features below are exposed from `UserSessionScope` unless marked global/auth.

## Global/account features

Scope: `GlobalKaliumScope`

- Validate email/password/handle/SSO code.
- Fetch server config from deep link/custom backend.
- Observe login context.
- Persist authenticated account.
- List/observe valid sessions.
- Delete sessions.
- Save notification token.
- Observe/update persistent websocket setting.
- Observe app update requirements.
- Observe new clients across accounts.
- Observe app lock editability.

## Authentication features

Scope: `AuthenticationScope`

- Login.
- Register account.
- SSO login.
- Domain lookup.
- Login flow discovery for domain.
- Request second-factor verification code.
- Check if update is required.
- Check if Nomad profiles are enabled.

## Messaging

Scope: `session.messages`

- Send text, edit text.
- Send multipart/composite messages.
- Retry failed messages.
- Fetch/observe message by ID.
- Send assets/files.
- Fetch asset messages and image asset messages.
- Recent messages.
- Delete messages.
- Toggle/observe reactions.
- Observe receipts.
- Send knock/ping.
- Send location.
- Notifications.
- Reset session.
- Button/action messages.
- Search messages in conversation or globally.
- Draft save/get/remove.
- In-call reactions.
- Audio message helpers and loudness updates.
- Observe asset upload/status.

## Conversations

Scope: `session.conversations`

- List/observe conversations.
- One-to-one conversation lookup/create.
- Observe details, members, mention candidates.
- Create regular groups.
- Create channels.
- Add/remove members.
- Add services/apps.
- Update muted/archived/read state.
- Update access roles/member roles.
- Leave/promote admin and leave.
- Rename, clear, locally delete conversations.
- Join via invite code.
- Guest room link generate/revoke/observe.
- Message timer and receipt mode.
- Unread counts.
- Typing send/observe/clear.
- Legal hold notification flags.
- Conversation folders/favorites.

## Calls

Scope: `session.calls`

- Observe established/incoming/outgoing/ongoing calls.
- Start, answer, end, reject calls.
- Mute/unmute.
- Update video state.
- Video preview, camera flip, UI rotation.
- Loudspeaker controls.
- Request video streams.
- Eligibility and conference calling feature observation.
- Recently ended call metadata.
- In-call reactions.
- Background selection.
- Call quality data and interval.
- Call moderation actions.
- Stale open call cleanup.

## Users

Scope: `session.users`

- Get/observe self user.
- Observe self user with team.
- User info/details.
- Upload avatar and fetch public avatar assets.
- Persist self email.
- Set user handle.
- Contacts and known users.
- Update availability, display name, accent color, email.
- Account deletion.
- Read receipt and typing indicator preferences.
- Read-only/password-required state.
- Asset size limit.
- E2EI enrollment and certificates.
- Wire Cells/profile QR feature flags.
- Foreground actions.
- Server links.

## Clients/devices

Scope: `session.client`

- Fetch self and other users' clients.
- Observe client details/current client ID.
- Delete client.
- Determine if client registration is needed.
- Register or get current client.
- MLS key package count/refill integration.
- Proteus and remote client fingerprints.
- Update client verification status.
- Restart slow sync for recovery.

## Connections

Scope: `session.connections`

- Send connection request.
- Accept, cancel, ignore connection request.
- Block/unblock user.

## Search

Scope: `session.search`

- Search users.
- Search by handle.
- Federated search parsing.
- Federation search availability.

## Backup

Scopes: `session.backup`, `session.multiPlatformBackup`

- Create backup.
- Verify backup.
- Restore backup.
- Create unencrypted/obfuscated copy.
- Backup and upload crypto state.
- Restore crypto state.
- Multi-platform backup import/export helpers.

## Teams/apps/services/channels

Scopes:

- `session.team`
- `session.app`
- `session.service`
- `session.channels`

Capabilities:

- Sync self team info.
- Check team membership.
- Get/search apps and services.
- Observe app/service membership.
- Observe whether apps are allowed.
- Observe/update channel creation/add permissions.

## Debug/internal support

Scope: `session.debug`

Debug scope exposes repair, inspection, and maintenance use cases. Use only for developer tools or controlled support flows, not ordinary user journeys.

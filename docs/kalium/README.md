# Kalium SDK integration guide

Kalium is Wire's Kotlin Multiplatform client SDK. It provides the application-facing logic for authentication, account/session management, messaging, conversations, calling, sync, backup, users, teams, clients/devices, services/apps, search, and cryptographic protocol flows.

This guide is based on the public API exposed by the `:logic` module in `vendor/kalium`.

## Contents

- [Architecture](./architecture.md)
- [Setup](./setup.md)
- [Bootstrapping](./bootstrapping.md)
- [Authentication](./authentication.md)
- [Sessions and sync](./sessions-and-sync.md)
- [Features](./features.md)
- [Messaging examples](./messaging.md)
- [Conversations examples](./conversations.md)
- [Calling](./calling.md)
- [Backup](./backup.md)
- [Operational notes](./operations.md)
- [API surface map](./api-surface.md)

## Mental model

Use Kalium through three layers:

```text
CoreLogic
  -> GlobalKaliumScope       // app/account/session-wide APIs
  -> AuthenticationScope     // unauthenticated login/register/SSO APIs
  -> UserSessionScope        // APIs for one logged-in user
```

Typical app flow:

```text
create CoreLogic
  -> choose/fetch server config
  -> authenticate user
  -> persist authenticated account/session
  -> create UserSessionScope(userId)
  -> register/fetch client if needed
  -> start sync
  -> use feature scopes: messages, conversations, calls, users, backup, ...
```

## Main capabilities

- Authentication: login, SSO, registration, second-factor verification, domain lookup.
- Sessions: persisted accounts, current session, multi-account observation, logout.
- Sync: slow sync, incremental sync, websocket/event processing, recovery.
- Messaging: text, edits, multipart, assets, reactions, receipts, location, knocks, drafts, search.
- Conversations: group/channel creation, one-to-one resolution, members, roles, read state, guest links, folders.
- Calling: call lifecycle, audio/video controls, call metadata, quality, moderation, reactions.
- Users: self user, contacts, profile updates, avatar, E2EI certificates, availability, preferences.
- Clients/devices: registration, deletion, fingerprints, MLS key packages, verification.
- Backup: backup create/verify/restore, multi-platform backup import/export, crypto-state backup.
- Teams/apps/services/channels: team status, service/app discovery, channel permissions.

## Source anchors

- `logic/src/commonMain/kotlin/com/wire/kalium/logic/CoreLogicCommon.kt`
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/GlobalKaliumScope.kt`
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/UserSessionScope.kt`
- `logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/auth/AuthenticationScope.kt`
- `sample/cli` and `sample/samples` for runnable/reference usage

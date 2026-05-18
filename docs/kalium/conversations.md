# Conversations

Conversation APIs live under:

```kotlin
val conversations = session.conversations
```

Source anchor:

- `logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/conversation/ConversationScope.kt`

## Observe conversation list

```kotlin
val conversationsFlow = session.conversations.observeConversationListDetails
val conversationsWithEventsFlow = session.conversations.observeConversationListDetailsWithEvents
```

Use these for main conversation list UI.

## Get conversation details

```kotlin
val details = session.conversations.observeConversationDetails(/* conversation id */)
```

## One-to-one conversation

```kotlin
session.conversations.getOneToOneConversation(/* user id */)
session.conversations.getOrCreateOneToOneConversationUseCase(/* user id */)
session.conversations.isOneToOneConversationCreatedUseCase(/* user id */)
```

Use `getOrCreateOneToOneConversationUseCase` before sending a direct message if no local one-to-one exists.

## Create group

```kotlin
session.conversations.createRegularGroup(/* group name, members, protocol params */)
```

After creation, rely on sync/observation to update UI state.

## Create channel

```kotlin
session.conversations.createChannel(/* params */)
```

Check channel permission first:

```kotlin
session.channels.observeChannelsCreationPermissionUseCase
```

## Members

```kotlin
session.conversations.observeConversationMembers(/* conversation id */)
session.conversations.getMembersToMention(/* conversation id */)
session.conversations.addMemberToConversationUseCase(/* params */)
session.conversations.removeMemberFromConversation(/* params */)
session.conversations.updateConversationMemberRole(/* params */)
```

## Access and roles

```kotlin
session.conversations.updateConversationAccess(/* params */)
session.conversations.changeAccessForAppsInConversation(/* params */)
```

## Read/archive/mute state

```kotlin
session.conversations.updateConversationMutedStatus(/* params */)
session.conversations.updateConversationArchivedStatus(/* params */)
session.conversations.updateConversationReadDateUseCase(/* params */)
session.conversations.markConversationAsReadLocally(/* params */)
```

## Leave/delete/clear

```kotlin
session.conversations.leaveConversation(/* conversation id */)
session.conversations.promoteAdminAndLeaveConversation(/* params */)
session.conversations.checkConversationLeaveConditions(/* conversation id */)
session.conversations.clearConversationContent(/* conversation id */)
session.conversations.deleteTeamConversation(/* conversation id */)
session.conversations.deleteConversationLocallyUseCase(/* conversation id */)
```

## Guest links/invite codes

```kotlin
session.conversations.generateGuestRoomLink(/* conversation id */)
session.conversations.revokeGuestRoomLink(/* conversation id */)
session.conversations.observeGuestRoomLink(/* conversation id */)
session.conversations.joinConversationViaCode(/* code */)
session.conversations.checkIConversationInviteCode(/* code */)
```

## Typing indicators

```kotlin
session.conversations.sendTypingEvent(/* conversation id, typing state */)
session.conversations.observeUsersTyping(/* conversation id */)
session.conversations.clearUsersTypingEvents(/* conversation id */)
```

## Folders and favorites

```kotlin
session.conversations.getFavoriteFolder
session.conversations.addConversationToFavorites(/* conversation id */)
session.conversations.removeConversationFromFavorites(/* conversation id */)
session.conversations.observeUserFolders
session.conversations.moveConversationToFolder(/* params */)
session.conversations.removeConversationFromFolder(/* params */)
session.conversations.createConversationFolder(/* params */)
session.conversations.observeConversationsFromFolder(/* folder id */)
```

## Legal hold and verification notification flags

```kotlin
session.conversations.setUserInformedAboutVerificationBeforeMessagingUseCase
session.conversations.observeInformAboutVerificationBeforeMessagingFlagUseCase
session.conversations.setNotifiedAboutConversationUnderLegalHold
session.conversations.observeConversationUnderLegalHoldNotified
```

## Practical notes

- Conversation mutations usually require network and local persistence; handle async states.
- UI should observe conversation list/details instead of assuming mutation return updates all state.
- Protocol details are available through `getConversationProtocolInfo`.
- For message sending, ensure conversation exists and user can interact using `observeConversationInteractionAvailabilityUseCase`.

# Messaging

Messaging APIs live under:

```kotlin
val messages = session.messages
```

Source anchor:

- `logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/message/MessageScope.kt`

## Send text

```kotlin
suspend fun sendBasicText(
    session: UserSessionScope,
    conversationId: ConversationId
) {
    session.messages.sendTextMessage(
        conversationId = conversationId,
        text = "Hello"
    )
}
```

This mirrors the repository sample in `sample/samples/src/jvmMain/kotlin/samples/logic/MessageUseCases.kt`.

## Send text with mentions

```kotlin
suspend fun sendMention(
    session: UserSessionScope,
    conversationId: ConversationId,
    mentionedUserId: UserId,
    selfUserId: UserId
) {
    val text = "Hello, @John"
    val mention = MessageMention(
        start = 7,          // index where mention begins
        length = 5,         // includes @
        userId = mentionedUserId,
        isSelfMention = mentionedUserId == selfUserId
    )

    session.messages.sendTextMessage(
        conversationId = conversationId,
        text = text,
        mentions = listOf(mention)
    )
}
```

Keep mention offsets aligned with the actual message string.

## Edit text

```kotlin
suspend fun editMessage(
    session: UserSessionScope,
    conversationId: ConversationId,
    originalMessageId: String
) {
    session.messages.sendEditTextMessage(
        conversationId = conversationId,
        originalMessageId = originalMessageId,
        text = "Updated text"
    )
}
```

## Retry failed message

```kotlin
session.messages.retryFailedMessage(/* message id / params from use case */)
```

Use when local state marks an outgoing message as failed.

## Observe or fetch message

```kotlin
val message = session.messages.getMessageById(/* ids */)
val flow = session.messages.observeMessageById(/* ids */)
```

Use observe APIs for UI state.

## Delete message

```kotlin
session.messages.deleteMessage(/* conversation/message params */)
```

Deletion behavior depends on message type, sender, and backend rules.

## Reactions and receipts

```kotlin
session.messages.toggleReaction(/* params */)
session.messages.observeMessageReactions(/* message id */)
session.messages.observeMessageReceipts(/* message id */)
```

## Assets/files

```kotlin
session.messages.sendAssetMessage(/* file metadata/content params */)
session.messages.getAssetMessage(/* message/asset params */)
session.messages.observeAssetUploadState(/* asset/message params */)
session.messages.updateAssetMessageTransferStatus(/* params */)
```

Asset upload is scheduled through Kalium work scheduling. Present progress using observe APIs.

## Search messages

```kotlin
session.messages.searchMessagesInConversation(/* query */)
session.messages.searchMessagesGlobally(/* query */)
session.messages.getSearchedConversationMessagePosition(/* params */)
```

## Drafts

```kotlin
session.messages.saveMessageDraftUseCase(/* draft */)
session.messages.getMessageDraftUseCase(/* conversation id */)
session.messages.removeMessageDraftUseCase(/* conversation id */)
```

## Ephemeral/self-deleting messages

Relevant APIs:

```kotlin
session.messages.enqueueMessageSelfDeletion
session.messages.deleteEphemeralMessageEndDate
```

Kalium also schedules deletion from event handlers when ephemeral messages are received.

## UI integration pattern

```kotlin
class ConversationViewModel(
    private val session: UserSessionScope,
    private val conversationId: ConversationId
) : ViewModel() {
    fun send(text: String) = viewModelScope.launch {
        session.messages.sendTextMessage(conversationId, text)
    }
}
```

## Practical notes

- Start sync before displaying conversation timelines.
- Use `Flow` observation for UI, direct fetch for one-off commands.
- Keep message sends in application service/view model coroutine scope.
- Map send failures into retry UI using `retryFailedMessage`.
- For mentions, compute offsets after final text normalization.

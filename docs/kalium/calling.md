# Calling

Calling APIs live under:

```kotlin
val calls = session.calls
```

Source anchor:

- `logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/call/CallsScope.kt`

## Observe calls

```kotlin
session.calls.establishedCall
session.calls.getIncomingCalls
session.calls.observeOutgoingCall
session.calls.observeOngoingCalls
session.calls.allCallsWithSortedParticipants
session.calls.observeLastActiveCallWithSortedParticipants
```

Use these to drive call banners, incoming call screens, and active call UI.

## Start/answer/end/reject

```kotlin
session.calls.isEligibleToStartCall(/* conversation id */)
session.calls.startCall(/* conversation id, media params */)
session.calls.answerCall(/* call params */)
session.calls.endCall(/* call params */)
session.calls.rejectCall(/* call params */)
```

## Audio controls

```kotlin
session.calls.muteCall(/* call id */)
session.calls.unMuteCall(/* call id */)
session.calls.turnLoudSpeakerOn()
session.calls.turnLoudSpeakerOff()
session.calls.observeSpeaker()
```

## Video controls

```kotlin
session.calls.updateVideoState(/* params */)
session.calls.setVideoSendState(/* params */)
session.calls.setVideoPreview(/* params */)
session.calls.flipToFrontCamera()
session.calls.flipToBackCamera()
session.calls.setUIRotation(/* rotation */)
session.calls.requestVideoStreams(/* params */)
```

## Feature availability

```kotlin
session.calls.observeConferenceCallingEnabled
session.calls.observeEndCallDueToDegradationDialog
```

## Call feedback and metadata

```kotlin
session.calls.observeAskCallFeedbackUseCase
session.calls.updateNextTimeCallFeedback
session.calls.observeRecentlyEndedCallMetadata
```

## In-call features

```kotlin
session.calls.observeInCallReactions
session.messages.sendInCallReactionUseCase
session.calls.observeCallModerationActions
```

## Quality data

```kotlin
session.calls.observeCallQualityData
session.calls.setCallQualityInterval(/* interval */)
```

## Background and preview/test controls

```kotlin
session.calls.setBackground(/* background */)
session.calls.setTestVideoType(/* test video */)
session.calls.setTestPreviewActive(/* active */)
session.calls.setTestRemoteVideoStates(/* states */)
```

Test controls should be reserved for QA/dev tooling.

## Practical integration notes

- Calling depends on the global call manager created by `CoreLogic`.
- Keep call UI subscribed to call flows; call state can change because of remote events.
- Logout integrates call cleanup through `session.logout`.
- Sync and websocket/event processing are important for accurate call state.
- On Android, call/audio integration uses platform context and AVS/audio dependencies.

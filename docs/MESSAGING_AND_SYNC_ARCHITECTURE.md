# Wire-CLI: Messaging and Synchronization Architecture

**Research Date**: March 15, 2026  
**Status**: Complete Research Synthesis  
**Document Version**: 1.0  

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Why Data Synchronization is Critical](#why-data-synchronization-is-critical)
3. [Message Sending Architecture](#message-sending-architecture)
4. [Message Receiving Architecture](#message-receiving-architecture)
5. [Synchronization Mechanisms](#synchronization-mechanisms)
6. [Wire-CLI Integration Layer](#wire-cli-integration-layer)
7. [Complete Data Flow Diagrams](#complete-data-flow-diagrams)
8. [Key Design Decisions](#key-design-decisions)
9. [Failure Modes and Recovery](#failure-modes-and-recovery)
10. [Implementation Guidelines](#implementation-guidelines)

---

## Executive Summary

Wire-CLI is a Kotlin command-line interface for the Wire messaging platform that integrates with **Kalium**, a comprehensive SDK for Wire client functionality. The architecture separates three key concerns:

1. **Message Sending** - Encrypting and transmitting messages via Proteus or MLS protocols
2. **Message Receiving** - Fetching encrypted messages from the server and decrypting them
3. **Data Synchronization** - Maintaining client state consistency with the Wire backend

### Key Insight

The system cannot simply "request messages" when needed. Instead, it must maintain **synchronized metadata state** (conversations, users, teams, cryptographic keys) as the foundation, then use **incremental sync** to deliver new messages. This two-phase approach handles:

- **Event window limitations**: Servers don't keep message history forever
- **Offline detection**: Clients must recognize when they've been offline too long to catch up
- **Cryptographic state**: Session keys must be established before decryption
- **Atomic consistency**: Metadata and messages must update together

---

## Why Data Synchronization is Critical

### Problem 1: Event Window Limitation

Wire backend keeps events (messages and state changes) in a **rolling window**, typically 30 days. When a client connects:

- If offline < 30 days: **Catch-up sync** (fetch missed events)
- If offline > 30 days: **Full sync** (rebuild entire state)
- Client must **detect** when it's been offline too long

**Naive Approach Fails**: Simply asking "give me all messages from last checkpoint" fails when that checkpoint is outside the event window. The client doesn't know how far back it missed.

### Problem 2: Missing Conversation Metadata

Messages exist in conversations, which have:

- Participant lists
- Encryption protocol (Proteus vs MLS)
- Group context and epochs (for MLS)
- Archive status, message timer, access levels
- Team membership

**Cannot decrypt messages without this context**. If you only fetch messages:

```
Server: Here are 500 messages
Client: But from which conversations? What's the encryption protocol?
Client: What are the cryptographic keys and epochs?
```

### Problem 3: Cryptographic State Not Established

For **MLS (Message Layer Security)**, the client must:

1. Join the group
2. Learn the group's current epoch
3. Download the current group tree
4. Establish cryptographic context

Without this, decryption is impossible. For **Proteus**, sessions must be established with each participant.

### Problem 4: Stateful Side Effects Race Conditions

When messages arrive:

- Delivery confirmations are sent
- Self-deletion timers start
- Typing indicators reset
- Read receipts update user state

If metadata state is incomplete, these side effects can:
- Acknowledge the wrong message
- Fail to start self-deletion timers
- Create inconsistent client state

### Solution: Two-Phase Synchronization

**Phase 1 - SlowSync (Full State Reconstruction)**
```
Fetch and establish:
1. Self user profile
2. All conversations (1-on-1, groups, teams)
3. User profiles for all conversation members
4. Team information and roles
5. Cryptographic keys and sessions
6. MLS group contexts and current epochs
```

**Phase 2 - IncrementalSync (Live Event Delivery)**
```
After Phase 1, fetch events in a tight loop:
- New messages (with cryptographic context already loaded)
- User presence changes
- Conversation metadata updates
- Server handshake confirmations
```

This ensures:
- ✅ Metadata is complete before processing messages
- ✅ Cryptographic state is prepared
- ✅ Side effects execute correctly
- ✅ Client can detect offline-too-long condition
- ✅ No silent data loss

---

## Message Sending Architecture

### High-Level Flow

```
User Input
    ↓
SendTextMessageUseCase.invoke(conversationId, text)
    ↓
Resolve conversation metadata (members, encryption protocol)
    ↓
MessageSender.attemptToSend()
    ├─→ Proteus Branch: encrypt per-client with Signal protocol
    └─→ MLS Branch: encrypt once with MLS group key
    ↓
Add to local database as PENDING
    ↓
Attempt transmission to /conversations/{convId}/otr/messages or /mls/messages
    ├─→ Success: Mark as SENT ✓
    └─→ Failure: Queue for retry via PendingMessagesSenderWorker
    ↓
Server confirms: Mark as READ ✓✓
```

### Message Envelope Creation - Proteus Path

**Proteus Protocol**: Signal protocol-based, requires per-client encryption.

For a group with 10 members:

```kotlin
// 1. Resolve all members
val recipients: List<QualifiedID> = conversation.members
    .filter { it.id != currentUserId }

// 2. Establish/retrieve session with each member
recipients.forEach { member ->
    sessionStore.getSessionWithUser(member)  // May establish if new
}

// 3. Encrypt message for each member
val envelopes = recipients.map { member ->
    val plaintext = messageBytes  // ~50 bytes for "Hello"
    
    // Create encryption context per-client per-device
    val ciphertext = proteusSession.encrypt(
        plaintext = plaintext,
        clientId = member.clientId
    )
    
    ClientMessageEntry(
        clientId = member.clientId,
        ciphertext = base64(ciphertext)  // ~256 bytes overhead
    )
}

// 4. For large payloads (>200KB), use external message pattern
val (payload, externalData) = if (plaintext.size > 200_000) {
    val aesKey = generateRandomBytes(32)  // AES-256
    val encrypted = AES_256_CBC.encrypt(plaintext, aesKey)
    val encryptedKey = proteusSession.encrypt(aesKey)
    
    Pair(
        payload = protobuf.encode(MessageContent.External(encryptedKey)),
        externalData = encrypted  // Sent separately to /conversations/{id}/otr/blob
    )
} else {
    val payload = protobuf.encode(MessageContent.Text(plaintext))
    Pair(payload = payload, externalData = null)
}

// 5. Create final message envelope
val envelope = OtrMessage(
    sender = currentUserId.clientId,
    recipients = envelopes,  // Map of QualifiedID -> ClientMessageEntry
    blob = externalData
)

// 6. Send to server
httpClient.post("/conversations/${convId}/otr/messages") {
    body = json.encode(envelope)
}
```

**Key Points**:
- ✅ Per-client encryption adds ~256 bytes per recipient
- ✅ External message pattern for large payloads (AES-256-CBC)
- ✅ Session establishment on first message (automatic)
- ✅ Batched encryption optimization for multiple recipients

### Message Envelope Creation - MLS Path

**MLS (Message Layer Security)**: Modern protocol with tree-based cryptography and forward secrecy.

```kotlin
// 1. Resolve MLS group (or create if new)
val mlsGroupId = conversation.mlsGroupId
    ?: createMLSGroup(conversation.members)

// 2. Ensure client is joined to group
val mlsGroup = mlsGroupStore.getOrJoin(mlsGroupId)

// 3. Get current group context
val currentEpoch = mlsGroup.epoch  // e.g., epoch 42
val groupTree = mlsGroup.tree      // Merkle tree of members

// 4. Protect message with MLS
val plaintext = messageBytes  // ~50 bytes for "Hello"
val msgProtect = coreCrypto.protectMessage(
    groupId = mlsGroupId,
    plaintext = plaintext
)

// 5. Create wire envelope
val ciphertext = msgProtect.mls_ciphertext
val commit = msgProtect.commit  // null unless proposal pending

val envelope = MLSMessage(
    id = generateMessageId(),
    groupId = mlsGroupId,
    epoch = currentEpoch,
    ciphertext = base64(ciphertext),
    commit = commit
)

// 6. Send to server
httpClient.post("/mls/messages") {
    body = json.encode(envelope)
}

// 7. If commit was sent, wait for welcome messages from server
// (new members joining the group during this send)
```

**Key Points**:
- ✅ Single encryption for entire group (no per-client overhead)
- ✅ Forward secrecy: Epoch advances with each commit
- ✅ Group management: Adds/removes tracked automatically
- ✅ Scales better than Proteus for large groups (100+ members)

### Message Queuing and Retry Mechanisms

**Three-Level Retry Strategy**:

#### Level 1: Inline Retry (During Send)

```kotlin
// In MessageSenderImpl.attemptToSend()
fun attemptToSend(message: Message, retry: Int = 0): SendResult {
    return try {
        val envelope = when {
            message.isProteus -> createProteusEnvelope(message)
            message.isMLS -> createMLSEnvelope(message)
        }
        
        httpClient.post(envelope)
        SendResult.Success()
        
    } catch (e: EncryptionException) {
        if (retry < MAX_INLINE_RETRIES && isRetryableError(e)) {
            // Recursive retry: typically for session establishment failures
            Thread.sleep(100 * (1 shl retry))  // Exponential backoff
            attemptToSend(message, retry + 1)
        } else {
            SendResult.Failure(retryable = true)  // Will be scheduled for background retry
        }
    } catch (e: NetworkException) {
        SendResult.Failure(retryable = true)
    }
}
```

#### Level 2: Scheduled Retry (Background Worker)

```kotlin
// PendingMessagesSenderWorker - runs periodically (every 30 seconds)
class PendingMessagesSenderWorker : CoroutineWorker() {
    override suspend fun doWork(): Result {
        val session = getActiveSession()
        val pendingMessages = messageRepository.getAllPending(session.userId)
        
        pendingMessages.forEach { msg ->
            val result = messageSender.attemptToSend(msg)
            
            when (result) {
                is SendResult.Success -> {
                    messageRepository.update(msg.id, status = SENT)
                }
                is SendResult.Failure -> {
                    msg.retryCount++
                    
                    // Exponential backoff: don't retry too frequently
                    if (msg.retryCount > MAX_RETRIES) {
                        messageRepository.update(msg.id, status = FAILED)
                    } else {
                        val backoffMs = 1000 * (2 pow msg.retryCount)
                        scheduleRetry(msg, delayMs = backoffMs)
                    }
                }
            }
        }
        
        return Result.success()
    }
}
```

#### Level 3: Manual Retry (User Action)

```kotlin
// RetryFailedMessageUseCase - invoked by user via CLI
class RetryFailedMessageUseCase(
    private val messageSender: MessageSender
) {
    suspend fun invoke(messageId: String): Result {
        val message = messageRepository.getById(messageId)
        
        if (message.status != FAILED) {
            return Result.Failure("Only failed messages can be retried")
        }
        
        // Reset retry count
        message.retryCount = 0
        message.status = PENDING
        
        // Attempt immediate send
        val result = messageSender.attemptToSend(message)
        
        if (result is SendResult.Success) {
            messageRepository.update(message.id, status = SENT)
            return Result.Success()
        } else {
            // Back to failed, user can try again later
            return Result.Failure(result.errorMessage)
        }
    }
}
```

### Handling Client Changes During Send

When you send a message and the recipient **creates a new device** (or **deletes a device**), Proteus requires session re-establishment:

```kotlin
// In handleProteusError()
fun handleProteusError(error: ProteusException, message: Message): SendResult {
    return when (error) {
        is SessionNotFound -> {
            // Recipient created a new device, or sessions were cleared
            sessionStore.deleteSessionWithUser(message.recipient)
            
            // Re-establish on next send (inline retry)
            SendResult.Failure(retryable = true, reason = "Session not found")
        }
        is UnknownMessageFormat -> {
            // Old client doesn't understand our encryption
            // Try with older Proteus version (fallback)
            attemptToSend(message, useProtocol = PROTEUS_V2)
        }
        else -> SendResult.Failure(retryable = false)
    }
}
```

---

## Message Receiving Architecture

### Event Receiving Pipeline

```
Server Event Stream
    ↓
EventGatherer (WebSocket or REST polling)
    ↓
EventProcessor (routes to type-specific handlers)
    ├─→ NewMessageEventHandler (unpacking)
    │   ├─→ ProteusMessageUnpacker (Proteus decryption)
    │   │   ├─ Establish session if needed
    │   │   ├─ Decrypt with Signal protocol
    │   │   └─ Handle external message if present
    │   └─→ MLSMessageUnpacker (MLS decryption)
    │       ├─ Process MLS Welcome messages
    │       ├─ Update group context/epoch
    │       └─ Batch decrypt multiple messages
    ├─→ ConversationEventHandler (adds/removes members)
    └─→ ... (other event types)
    ↓
ApplicationMessageHandler (route to content handlers)
    ├─→ TextContentHandler → persist to MessageTextContent table
    ├─→ AssetContentHandler → download attachment
    └─→ ReactionContentHandler → persist to ReactionRepository
    ↓
Database Transaction (atomic: decrypt + store + mark processed)
    ↓
Side Effects Queue
    ├─ Send delivery confirmation
    ├─ Start self-deletion timer
    └─ Update read receipts
```

### Proteus Message Unpacking

**Single message per event**: Decrypt, extract payload, handle external content.

```kotlin
class ProteusMessageUnpacker(
    private val sessionStore: SessionStore,
    private val externalContentRepository: ExternalContentRepository
) {
    suspend fun unpack(event: NewMessageEvent): MessageUnpackResult {
        return try {
            // 1. Extract encrypted data from event
            val ciphertext = base64Decode(event.data)
            
            // 2. Establish/retrieve session with sender
            val session = sessionStore.getSessionWithUser(event.from)
                ?: sessionStore.establishSession(event.from)  // Returns null if sender unknown
            
            if (session == null) {
                return MessageUnpackResult.Decryption_Error(
                    convId = event.convId,
                    reason = "Unknown user: ${event.from}"
                )
            }
            
            // 3. Decrypt with Proteus (Signal protocol)
            val plaintext: ByteArray = try {
                proteusEngine.decrypt(session, ciphertext)
            } catch (e: Exception) {
                // Decryption failed - could be many reasons
                return MessageUnpackResult.Decryption_Error(
                    convId = event.convId,
                    reason = "Proteus decryption failed: ${e.message}"
                )
            }
            
            // 4. Protobuf decode to MessageContent
            val content = MessageContent.deserialize(plaintext)
            
            // 5. Handle external message pattern if present
            val finalContent = if (content is MessageContent.External) {
                // Decrypt external blob separately
                val externalBlob = externalContentRepository.fetch(content.externalKey)
                
                val externalPlaintext = AES_256_CBC.decrypt(
                    ciphertext = externalBlob.data,
                    key = proteusEngine.decrypt(session, content.externalKey)
                )
                
                MessageContent.deserialize(externalPlaintext)
            } else {
                content
            }
            
            // 6. Return success with decrypted content
            return MessageUnpackResult.Success(
                sender = event.from,
                clientId = event.clientId,
                content = finalContent,
                timestamp = event.timestamp
            )
            
        } catch (e: Exception) {
            MessageUnpackResult.Decryption_Error(
                convId = event.convId,
                reason = "Failed to unpack Proteus message: ${e.message}"
            )
        }
    }
}
```

### MLS Message Unpacking

**Batch processing**: Handle Welcome messages, update group context, decrypt messages.

```kotlin
class MLSMessageUnpacker(
    private val mlsGroupStore: MLSGroupStore,
    private val coreCrypto: CoreCrypto
) {
    suspend fun unpack(events: List<MLSMessageEvent>): List<MessageUnpackResult> {
        val results = mutableListOf<MessageUnpackResult>()
        
        // 1. Process Welcome messages first (member joins)
        events.filterIsInstance<MLSWelcomeEvent>().forEach { welcome ->
            try {
                val groupId = welcome.groupId
                val joinedGroup = coreCrypto.processWelcome(
                    ciphertext = base64Decode(welcome.ciphertext)
                )
                
                mlsGroupStore.addGroup(joinedGroup)
                results.add(MessageUnpackResult.Success(
                    sender = joinedGroup.creator,
                    content = MessageContent.System.GroupJoined(),
                    timestamp = welcome.timestamp
                ))
            } catch (e: Exception) {
                results.add(MessageUnpackResult.Decryption_Error(
                    reason = "MLS Welcome processing failed: ${e.message}"
                ))
            }
        }
        
        // 2. Process regular MLS messages
        events.filterIsInstance<MLSAppMessageEvent>().forEach { msgEvent ->
            try {
                val groupId = msgEvent.groupId
                val group = mlsGroupStore.getGroup(groupId)
                    ?: return@forEach results.add(MessageUnpackResult.Decryption_Error(
                        reason = "Unknown MLS group: $groupId"
                    ))
                
                // 3. Unprotect message (decrypt)
                val plaintext = coreCrypto.unprotectMessage(
                    groupId = groupId,
                    ciphertext = base64Decode(msgEvent.ciphertext),
                    externalAAD = msgEvent.aad.toByteArray()
                )
                
                // 4. Extract sender from group tree
                val sender = group.getSenderFromCommitIndex(msgEvent.senderIndex)
                
                // 5. Protobuf decode to content
                val content = MessageContent.deserialize(plaintext)
                
                // 6. Check for proposal/commit
                if (msgEvent.commit != null) {
                    // Group was updated, epoch advanced
                    group.commitProposals(msgEvent.commit)
                    group.epoch++
                }
                
                results.add(MessageUnpackResult.Success(
                    sender = sender,
                    content = content,
                    timestamp = msgEvent.timestamp,
                    subconvId = msgEvent.subconvId
                ))
                
            } catch (e: MLSGroupOutOfSync) {
                // Out-of-epoch message - request group reset
                results.add(MessageUnpackResult.MLS_OutOfSync(
                    groupId = msgEvent.groupId,
                    currentEpoch = group.epoch,
                    messageEpoch = msgEvent.epoch
                ))
            } catch (e: Exception) {
                results.add(MessageUnpackResult.Decryption_Error(
                    reason = "MLS message unprotection failed: ${e.message}"
                ))
            }
        }
        
        return results
    }
}
```

### Message Persistence in Database

After successful decryption:

```kotlin
// Atomic transaction: decrypt + persist + mark event processed
databaseTransaction {
    // 1. Persist message
    val messageId = UUID.randomUUID()
    messagesTable.insert(Message(
        id = messageId,
        conversationId = event.convId,
        sender = unpackResult.sender,
        body = unpackResult.content.text ?: "",
        timestamp = unpackResult.timestamp,
        status = MessageStatus.RECEIVED,
        visibilityStatus = MessageVisibilityStatus.VISIBLE,
        senderName = senderUser.name,
        senderImage = senderUser.image
    ))
    
    // 2. Persist content details (text, mentions, links)
    when (unpackResult.content) {
        is MessageContent.Text -> {
            messageTextContentTable.insert(MessageTextContent(
                messageId = messageId,
                content = unpackResult.content.text,
                mentions = unpackResult.content.mentions.map { mention ->
                    MessageMention(
                        messageId = messageId,
                        userId = mention.userId,
                        start = mention.start,
                        length = mention.length
                    )
                }
            ))
        }
        is MessageContent.Text_With_LinkPreview -> {
            // Similar, with additional link metadata
        }
        is MessageContent.Asset -> {
            // Queue asset download
            assetDownloadQueue.enqueue(unpackResult.content.assetId)
        }
    }
    
    // 3. Mark event as processed
    eventsTable.update(
        eventId = event.id,
        processed = true,
        processedAt = Instant.now()
    )
    
    // Commit: all or nothing
}
```

---

## Synchronization Mechanisms

### The Two-Phase Sync Model

#### Phase 1: SlowSync (Full State Reconstruction)

Runs on login or when client detects it's been offline > 30 days.

```
Step 1: Migrate database schema
Step 2: Fetch self user profile
Step 3: Fetch and cache feature flags
Step 4: Fetch all conversations (1-on-1, groups, teams)
Step 5: Fetch all active connections (pending 1-on-1 requests)
Step 6: Fetch team information and roles
Step 7: Fetch all user profiles (contacts, team members)
Step 8: Establish MLS group contexts (join groups, download trees)
Step 9: Resolve encryption protocols (Proteus → MLS migration)
Step 10: Establish Proteus sessions with active participants
Step 11: Warmup metadata (user avatars, conversation icons)
Step 12: Save event checkpoint
```

After SlowSync, client has **complete metadata**: conversations, members, keys, epochs. Now it can process messages.

```kotlin
class SlowSyncManager(
    private val selfUserRepository: SelfUserRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsGroupRepository: MLSGroupRepository,
    private val sessionStore: SessionStore,
    // ... other repos
) {
    suspend fun performSlowSync(userId: UserId): SlowSyncResult {
        return try {
            // 1. Migrate database
            databaseMigrator.migrateToLatestSchema()
            
            // 2. Fetch self user
            val selfUser = userApi.getSelf()
            selfUserRepository.upsert(selfUser)
            
            // 3. Fetch feature flags
            val flags = featureApi.getFlags()
            flagsRepository.upsert(flags)
            
            // 4. Fetch conversations
            val conversations = conversationApi.listConversations()
            conversationRepository.upsert(conversations)
            
            // 5. Fetch connections
            val connections = connectionApi.listConnections()
            connectionRepository.upsert(connections)
            
            // 6. Fetch team info
            if (selfUser.team != null) {
                val team = teamApi.getTeam(selfUser.team)
                teamRepository.upsert(team)
            }
            
            // 7. Fetch all users
            val users = userApi.listAllUsers()
            userRepository.upsert(users)
            
            // 8. MLS group setup
            conversations.filter { it.protocol == Protocol.MLS }.forEach { conv ->
                val groupId = conv.mlsGroupId
                val group = mlsApi.getGroupInfo(groupId)
                val tree = mlsApi.getGroupTree(groupId)
                
                val joinedGroup = coreCrypto.processGroupInfo(group, tree)
                mlsGroupRepository.upsert(joinedGroup)
            }
            
            // 9. Establish Proteus sessions
            conversations.filter { it.protocol == Protocol.PROTEUS }.forEach { conv ->
                conv.members.forEach { member ->
                    val preKeys = preKeyApi.getPreKeys(member.userId)
                    sessionStore.establishSession(member.userId, preKeys)
                }
            }
            
            // 10. Save checkpoint
            val latestEventId = eventApi.getLatestEventId()
            checkpointRepository.save(latestEventId)
            
            SlowSyncResult.Success()
            
        } catch (e: ServerError.EventWindowExceeded) {
            // Server says: events before this checkpoint are lost
            // Solution: restart SlowSync (will refetch all state)
            SlowSyncResult.Failure(reason = "Event window exceeded, restarting")
        } catch (e: Exception) {
            SlowSyncResult.Failure(reason = "SlowSync failed: ${e.message}")
        }
    }
}
```

#### Phase 2: IncrementalSync (Live Event Delivery)

After SlowSync completes, continuously fetch new events.

```
State: GatheringPendingEvents
├─ Resume from last checkpoint
└─ Fetch events in chunks until no more pending
    ↓
    For each chunk:
    1. Process events through EventProcessor
    2. Update local state (messages, members, metadata)
    3. Advance checkpoint
    
State: Live (when no pending events remain)
├─ Switch to WebSocket for real-time delivery
└─ Process events with ~100-200ms latency
    ↓
    Each incoming event:
    1. Process immediately through EventProcessor
    2. Update local state
    3. Queue side effects (delivery confirmations, etc.)
    
If network disconnects:
├─ Revert to REST polling
├─ Resume from checkpoint
└─ Re-enter GatheringPendingEvents until caught up
```

```kotlin
class IncrementalSyncManager(
    private val eventApi: EventApi,
    private val eventProcessor: EventProcessor,
    private val checkpointRepository: CheckpointRepository,
    private val webSocketManager: WebSocketManager
) {
    suspend fun startIncrementalSync(userId: UserId) {
        var checkpoint = checkpointRepository.getLastCheckpoint()
        var isCaughtUp = false
        
        while (true) {
            if (!isCaughtUp) {
                // Phase 2a: GatheringPendingEvents
                val events = eventApi.getEventsSince(checkpoint, limit = 100)
                
                if (events.isEmpty()) {
                    // No more pending events
                    isCaughtUp = true
                    
                    // Switch to WebSocket for real-time delivery
                    webSocketManager.connect()
                    
                    // Emit: we're live now
                    syncStateFlow.emit(SyncState.Live())
                    
                } else {
                    // Process the chunk
                    events.forEach { event ->
                        eventProcessor.process(event)
                        checkpoint = event.id
                    }
                    
                    checkpointRepository.save(checkpoint)
                }
                
            } else {
                // Phase 2b: Live (WebSocket connected)
                // WebSocketManager will deliver events, we just process them
                webSocketManager.onMessage { event ->
                    eventProcessor.process(event)
                    checkpoint = event.id
                    checkpointRepository.save(checkpoint)
                }
                
                // If WebSocket disconnects, revert to polling
                webSocketManager.onDisconnect {
                    isCaughtUp = false
                }
                
                delay(1000)  // Keep the coroutine alive
            }
        }
    }
}
```

### Sync State Transitions

```
┌─────────────────────────────────────────────────────────┐
│                    SYNC STATE MACHINE                   │
└─────────────────────────────────────────────────────────┘

    [Waiting]
        ↓
        └─→ [SlowSync] ──────────────────┐
                ↓                         │
        [GatheringPendingEvents]         │
                ↓                         │
              [Live] ←────────────────────┤
                ↓ (network error)         │
        [GatheringPendingEvents]         │
                ↓ (no more events)        │
              [Live] ←────────────────────┘
                ↓ (network error, offline > 30 days)
            [Failed]

Error Recovery:
- [Live] + network error → [GatheringPendingEvents] (auto-reconnect)
- [Failed] → Automatic SlowSync restart via SyncEventOrClientNotFound
```

### Timing Characteristics

| Scenario | SlowSync | GatheringPendingEvents | Live |
|----------|----------|------------------------|------|
| Cold start (fresh login) | 5-30s | 0-10s | N/A |
| Brief outage (< 1 hour) | N/A | 1-5s | N/A |
| Extended offline (1-30 days) | 10-60s | 5-30s | N/A |
| Message delivery (live) | N/A | N/A | 100-200ms |
| Network glitch recovery | N/A | 1-5s | N/A |

---

## Wire-CLI Integration Layer

### Layered Architecture

Wire-CLI uses **decorator pattern** to add safety guards between user-facing CLI and Kalium SDK:

```
┌──────────────────────────────────────────────────────────┐
│  User Command (e.g., `wire sync status`)                 │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  AuthGuardedSyncService                                  │
│  - Ensures user is logged in                             │
│  - Validates session token                               │
│  - Caches auth state                                     │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  SessionBackedSyncService                                │
│  - Extracts userId from auth context                     │
│  - Passes to implementation                              │
│  - Handles session scope management                      │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  RealKaliumSyncApiClient                                 │
│  - Thin adapter                                          │
│  - Delegates to SdkKaliumSyncRuntime                     │
│  - Maps errors to CLI exit codes                         │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  SdkKaliumSyncRuntime                                    │
│  - Interacts with Kalium CoreLogic                       │
│  - Calls coreLogic.sessionScope(userId) { ... }          │
│  - Subscribes to observeSyncState().firstOrNull()        │
│  - Maps SyncState to SyncStatus and metrics              │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│  Kalium SDK (CoreLogic)                                  │
│  - Real message sending/receiving                        │
│  - Sync management                                       │
│  - Encryption (Proteus, MLS)                             │
│  - Database (SQLite or equivalent)                       │
└──────────────────────────────────────────────────────────┘
```

### CoreLogic Lifecycle

```kotlin
// In Main.kt or KaliumRuntime.kt

// 1. Lazy initialization (on first use, not on startup)
private val coreLogic: CoreLogic by lazy {
    CoreLogic(
        rootPath = "${HOME}/.wire/kalium",
        kaliumConfigs = kaliumCliConfigs(KaliumCliMode.REAL),
        userAgent = "wire-cli/${version}"
    )
}

// 2. Usage: scoped access
runBlocking {
    val userId = UserId(value = "alice", domain = "wire.com")
    
    coreLogic.sessionScope(userId) {
        // Inside scope: can call useCase.invoke(), observeX().firstOrNull(), etc.
        val syncState = observeSyncState().firstOrNull()
        
        // Scope ends here: implicit cleanup
    }
}

// 3. Shutdown: explicit cleanup on process exit
Runtime.getRuntime().addShutdownHook {
    runBlocking {
        coreLogic.getGlobalScope().cancel()
    }
}
```

**Key Properties**:
- ✅ Lazy initialization: Don't load Kalium until first `wire` command runs
- ✅ Session tracking: Maintains set of initialized sessions for cleanup
- ✅ Deterministic shutdown: Closes runtime scopes before process exit
- ✅ CLI mode flags: Control MLS migration, sync warmup, calling support

### SyncService Implementation

```kotlin
class SessionBackedSyncService(
    private val syncApiClient: SyncApiClient,
    private val authContext: AuthContext
) : SyncService {
    
    override fun getCurrentSyncStatus(): SyncStatusResult {
        val session = authContext.currentSession()
            ?: return SyncStatusResult.Failure(
                message = "Not logged in",
                exitCode = 11
            )
        
        return syncApiClient.getSyncStatus(session)
    }
}

class RealKaliumSyncApiClient(
    private val runtime: RealKaliumSyncRuntime
) : SyncApiClient {
    
    override fun getSyncStatus(session: AuthSession): SyncStatusResult {
        return runtime.getSyncStatus(session)  // Delegates
    }
}

class SdkKaliumSyncRuntime : RealKaliumSyncRuntime {
    
    override fun getSyncStatus(session: AuthSession): SyncStatusResult {
        val userId = session.userId.toQualifiedId()
        
        return runBlocking {
            try {
                val syncState: SyncState? = coreLogic.sessionScope(userId) {
                    observeSyncState().firstOrNull()  // Get current state
                }
                
                if (syncState == null) {
                    return@runBlocking SyncStatusResult.Failure(
                        message = "Unable to observe sync state",
                        exitCode = 13
                    )
                }
                
                // Map SyncState to SyncStatus
                val status = when (syncState) {
                    is SyncState.Live -> SyncStatus.READY
                    is SyncState.SlowSync -> SyncStatus.INITIALIZING
                    is SyncState.GatheringPendingEvents -> SyncStatus.INITIALIZING
                    is SyncState.Waiting -> SyncStatus.INITIALIZING
                    is SyncState.Failed -> SyncStatus.DEGRADED
                }
                
                // Calculate metrics
                val lagMs = when (syncState) {
                    is SyncState.Live -> 0L
                    is SyncState.SlowSync -> 5000L
                    is SyncState.GatheringPendingEvents -> 2000L
                    is SyncState.Waiting -> 1000L
                    is SyncState.Failed -> syncState.retryDelay.inWholeMilliseconds
                }
                
                val metrics = HealthMetrics(
                    lag_ms = lagMs,
                    pending_messages = calculatePendingMessages(syncState),
                    mls_pct = calculateMlsPercentage(syncState),
                    timestamp = Instant.now().toString(),
                    network = networkConnectivityChecker.checkNetworkConnectivity()
                )
                
                SyncStatusResult.Success(
                    SyncStatusView(status = status, metrics = metrics)
                )
                
            } catch (error: Throwable) {
                SyncStatusResult.Failure(
                    message = categoryFromThrowable(error).getMessage(),
                    exitCode = categoryFromThrowable(error).getExitCode()
                )
            }
        }
    }
}
```

### Stub vs Real Backend

For testing without network:

```kotlin
// Environment variable: WIRE_BACKEND=stub (or WIRE_BACKEND=real)

when (System.getenv("WIRE_BACKEND")?.lowercase()) {
    "stub" -> {
        // Return deterministic fake data
        syncApiClient = StubSyncApiClient(
            mode = WIRE_STUB_MODE  // Controls which test scenario
        )
    }
    else -> {
        // Full Kalium SDK integration
        syncApiClient = RealKaliumSyncApiClient(
            runtime = SdkKaliumSyncRuntime(environment)
        )
    }
}
```

**Stub Modes** (20+ available):
- `stub_sync_ready` - Sync already live
- `stub_sync_initializing` - In GatheringPendingEvents
- `stub_sync_degraded` - Failed sync, retrying
- `stub_no_auth` - Session invalid
- `stub_network_error` - Network unreachable
- ... and more

---

## Complete Data Flow Diagrams

### Message Sending Flow

```
User Types: "Hello Alice"
    ↓
wire conversation send <conv-id> "Hello Alice"
    ↓
ConversationSendCommand.execute()
    ├─ Resolve conversation (get members, protocol)
    └─ Call SyncService.sendMessage()
    ↓
AuthGuardedConversationService.sendMessage()
    ├─ Check: user logged in?
    └─ Pass to SessionBackedConversationService
    ↓
SessionBackedConversationService.sendMessage()
    ├─ Extract userId from auth context
    └─ Call RealKaliumConversationApiClient.sendMessage()
    ↓
RealKaliumConversationApiClient.sendMessage()
    └─ Delegate to RealKaliumConversationRuntime.sendMessage()
    ↓
SdkKaliumConversationRuntime.sendMessage()
    ├─ coreLogic.sessionScope(userId) {
    │   └─ sendMessageUseCase(convId, text).invoke()
    │       ├─ Resolve conversation metadata
    │       ├─ Select protocol (Proteus or MLS)
    │       ├─ Encrypt:
    │       │   ├─ Proteus: encrypt per-client (256 bytes each)
    │       │   └─ MLS: encrypt once for group
    │       ├─ Add to local DB as PENDING
    │       └─ POST to /conversations/{id}/otr/messages or /mls/messages
    │           ├─ Success: Mark SENT ✓
    │           └─ Failure: Enqueue for retry
    │   }
    └─ Catch errors, map to exit codes
    ↓
CLI Outputs: "Message sent ✓"
```

### Message Receiving Flow

```
Server Event Stream (WebSocket or polling)
    ↓
EventGatherer.fetch() → List<Event>
    ↓
    For each Event:
    ├─ Deserialize from JSON
    └─ Route to handler:
        ├─ NewMessageEvent → NewMessageEventHandler
        ├─ ConversationMemberAdd → MemberJoinEventHandler
        └─ ... (other event types)
        ↓
        For NewMessageEvent:
        ├─ Determine protocol (Proteus or MLS)
        ├─ Call appropriate unpacker:
        │   ├─ ProteusMessageUnpacker:
        │   │   ├─ Establish/retrieve session with sender
        │   │   ├─ Decrypt with Signal protocol
        │   │   ├─ Handle external message if present
        │   │   └─ Protobuf decode to MessageContent
        │   └─ MLSMessageUnpacker:
        │       ├─ Update group context/epoch if needed
        │       ├─ Unprotect message (decrypt)
        │       ├─ Batch process multiple messages
        │       └─ Protobuf decode to MessageContent
        │
        ├─ Route to ApplicationMessageHandler
        │   ├─ TextMessage → TextContentHandler
        │   │   └─ Persist to MessageTextContent + Mentions
        │   ├─ Asset → AssetMessageHandler
        │   │   └─ Queue download
        │   └─ Reaction → ReactionHandler
        │       └─ Persist to ReactionRepository
        │
        └─ Begin database transaction:
            ├─ Insert into messages table
            ├─ Insert into related tables (text, mentions, etc.)
            ├─ Mark event as processed
            ├─ Update conversation lastModified
            └─ Commit (all-or-nothing)
                ↓
                Queue side effects:
                ├─ Send delivery confirmation
                ├─ Start self-deletion timer (if message timer > 0)
                └─ Update read receipts
                    ↓
                    Execute side effects (batched)
```

### Sync Flow (SlowSync + IncrementalSync)

```
User Logs In
    ↓
wire login --email alice@example.com
    ↓
RealKaliumAuthClient.login()
    └─ Kalium CoreLogic:
        ├─ POST /login (get session token)
        ├─ Trigger SlowSync:
        │   ├─ Step 1: Migrate database schema
        │   ├─ Step 2: Fetch self user profile
        │   ├─ Step 3: Fetch feature flags
        │   ├─ Step 4: Fetch all conversations (100+)
        │   ├─ Step 5: Fetch all users (for sessions/encryption)
        │   ├─ Step 6: Establish Proteus sessions
        │   ├─ Step 7: Join MLS groups & download trees
        │   ├─ Step 8: Establish cryptographic contexts
        │   └─ Step 9: Save event checkpoint
        │
        ├─ Emit: SyncState.SlowSync → SyncState.GatheringPendingEvents
        │
        └─ Trigger IncrementalSync:
            ├─ Fetch events since checkpoint (in chunks)
            ├─ Process each event through EventProcessor
            ├─ Emit: SyncState.GatheringPendingEvents
            │
            ├─ When no more pending events:
            │   ├─ Connect WebSocket
            │   └─ Emit: SyncState.Live
            │
            └─ On network error:
                ├─ Disconnect WebSocket
                ├─ Revert to: SyncState.GatheringPendingEvents
                └─ Resume from last checkpoint
    ↓
wire sync status
    ↓
RealKaliumSyncApiClient.getSyncStatus()
    └─ observeSyncState().firstOrNull() → SyncStatus
        ├─ SyncState.Live → "ready"
        ├─ SyncState.GatheringPendingEvents → "initializing"
        └─ Metrics: lag_ms, pending_messages, mls_pct
    ↓
CLI Outputs:
    Status: ready
    Lag: 0ms
    Pending messages: 0
    MLS enrollment: 100%
```

---

## Key Design Decisions

### 1. Why Two-Phase Sync?

**Decision**: Separate SlowSync (metadata) from IncrementalSync (events)

**Rationale**:
- Correctness: Metadata must be complete before processing messages
- Offline detection: Client can detect when offline window exceeded
- Performance: Parallel metadata fetches faster than sequential message fetch
- Robustness: Clear separation of concerns, easier to debug/recover

**Alternative Rejected**: Single message-fetching loop
- ❌ Would fail on metadata gaps (missing users, conversations, keys)
- ❌ No offline detection until data corruption occurs
- ❌ Harder to implement retry/recovery

### 2. Why Proteus AND MLS?

**Decision**: Support both protocols simultaneously

**Rationale**:
- Proteus: Mature, stable, widely deployed
- MLS: Modern, forward-secure, better scaling for groups
- Migration path: Gradual transition from Proteus to MLS
- Fallback: If MLS fails, revert to Proteus

**Implementation**:
```kotlin
val messageProtocol = when {
    conversation.isMLS -> Protocol.MLS
    conversation.isProteus -> Protocol.PROTEUS
    else -> selectProtocolBasedOnMembers()
}
```

### 3. Why Three-Level Retry?

**Decision**: Inline + scheduled + manual retries

**Rationale**:
- Inline: Fast recovery for transient errors (session establishment)
- Scheduled: Background persistence for network errors
- Manual: User control for edge cases

**Benefits**:
- ✅ Doesn't block UI (scheduled runs in background)
- ✅ Exponential backoff prevents thundering herd
- ✅ User can manually retry failed messages
- ✅ Clear feedback on send status

### 4. Why Lazy CoreLogic Initialization?

**Decision**: Don't create CoreLogic until first command uses it

**Rationale**:
- Startup speed: `wire --help` doesn't load Kalium
- Resource efficiency: No database handles if just running auth commands
- Isolation: Each invocation is independent

**Implementation**:
```kotlin
private val coreLogic: CoreLogic by lazy {
    CoreLogic(...)  // Loaded on first use
}

// Explicitly cleaned up on shutdown
Runtime.getRuntime().addShutdownHook {
    runBlocking { coreLogic.getGlobalScope().cancel() }
}
```

---

## Failure Modes and Recovery

### Scenario 1: Offline for 40 Days

**What Happens**:
1. Client reconnects
2. Tries to resume from last checkpoint
3. Server responds: "That checkpoint is outside event window"
4. IncrementalSync detects `EventWindowExceeded` error
5. Automatically triggers SlowSync restart
6. Full state rebuilt
7. IncrementalSync resumes from new checkpoint

**No User Intervention Needed**: Automatic recovery

### Scenario 2: Network Glitch During Message Send

**What Happens**:
1. User sends message
2. Encryption succeeds (message marked PENDING locally)
3. Network timeout during POST
4. PendingMessagesSenderWorker picks up message
5. Retries with exponential backoff (1s, 2s, 4s, 8s, ...)
6. Eventually succeeds or max retries reached
7. User can manually retry via `wire message retry <id>`

**Result**: Message is delivered or clearly marked FAILED

### Scenario 3: Recipient Creates New Device

**What Happens**:
1. User sends message to Alice
2. Alice creates new device before decryption
3. Proteus decryption fails (old session key)
4. Error handler deletes old session
5. Inline retry: Inline retry establishes new session
6. Attempt send again
7. Succeeds with new session

**Result**: Message is delivered to both devices

### Scenario 4: Server Database Corruption (MLS)

**What Happens**:
1. Client receives MLS message from epoch 42
2. But client thinks it's in epoch 50 (group was updated)
3. Decryption fails: `MLSGroupOutOfSync`
4. Error handler requests group state from server
5. Server responds with updated epoch and tree
6. Client syncs group context
7. Reattempt decryption
8. Message is processed

**Result**: Automatic recovery, user sees message

---

## Implementation Guidelines

### For Adding New Message Types

1. **Define content type** in MessageContent.kt:
```kotlin
sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    data class Document(val fileId: String, val fileName: String) : MessageContent()
    // Add new type here
}
```

2. **Implement handler** for receiving:
```kotlin
class DocumentMessageHandler : ContentHandler {
    override suspend fun handle(
        message: Message,
        content: MessageContent.Document
    ) {
        // Download file, store reference, etc.
    }
}
```

3. **Wire handler into EventProcessor**:
```kotlin
when (content) {
    is MessageContent.Document -> documentHandler.handle(message, content)
    // Add case here
}
```

4. **Implement sender** for sending:
```kotlin
suspend fun sendDocument(
    conversationId: String,
    filePath: String
): SendResult {
    val fileBytes = File(filePath).readBytes()
    val assetId = assetUploader.upload(fileBytes)
    
    val content = MessageContent.Document(
        fileId = assetId,
        fileName = File(filePath).name
    )
    
    val message = Message.create(conversationId, content)
    return messageSender.send(message)
}
```

5. **Add CLI command**:
```kotlin
@Command(name = "send-document")
class DocumentSendCommand(
    private val conversationService: ConversationService
) {
    fun execute(
        @Parameters(description = "Conversation ID")
        convId: String,
        
        @Parameters(description = "File path")
        filePath: String
    ) {
        val result = conversationService.sendDocument(convId, filePath)
        println(result.message)
    }
}
```

### For Debugging Sync Issues

**Check sync status**:
```bash
wire sync status
# Output: Status ready | initializing | degraded
```

**Get detailed diagnostics**:
```bash
wire sync diagnostics
# Output: Check results (Auth, Sync Engine, Event Queue, Network)
```

**Reset sync**:
```bash
wire sync reset --force
# Clears checkpoint, forces full SlowSync on next login
```

**Check conversation sync**:
```bash
wire conversation sync-status <conv-id>
# Lag, pending messages, sync completeness %
```

### For Monitoring

**Key metrics to track**:
1. `lag_ms`: How far behind real-time are we? (< 200ms is good)
2. `pending_messages`: How many messages are queued locally? (0 is good)
3. `mls_pct`: What % of conversations are on MLS? (100% is goal)
4. Network error rate: % of requests that fail (< 1% is good)
5. Sync state transitions: How often do we re-sync? (< 5x/day is good)

**Warning signs**:
- ⚠️ `lag_ms` > 5000ms: Sync is lagging
- ⚠️ `pending_messages` > 100: Large backlog
- ⚠️ Repeated `GatheringPendingEvents` -> `Failed` cycles: Network issues
- ⚠️ `mls_pct` < 50%: Many conversations still on Proteus (migration incomplete)

---

## References

### Key Source Files

**Sync Architecture**:
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/SyncManager.kt` - Orchestrator
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/slow/SlowSyncManager.kt` - Phase 1
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/incremental/IncrementalSyncManager.kt` - Phase 2

**Message Sending**:
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/usecase/message/SendTextMessageUseCase.kt`
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/data/message/MessageSender.kt`
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/data/message/encryption/ProteusMessageCreator.kt`
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/data/message/encryption/MLSMessageCreator.kt`

**Message Receiving**:
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/EventReceiver.kt`
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/ConversationEventReceiver.kt`
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/ProteusMessageUnpacker.kt`
- `vendor/kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/MLSMessageUnpacker.kt`

**Wire-CLI Integration**:
- `src/main/kotlin/wirecli/sync/RealKaliumSyncApiClient.kt`
- `src/main/kotlin/wirecli/conversation/RealKaliumConversationApiClient.kt`
- `src/main/kotlin/wirecli/runtime/KaliumRuntime.kt`
- `src/main/kotlin/wirecli/sync/SessionBackedSyncService.kt`

### Related Documentation

- [Wire Protocol Overview](https://docs.wire.com/protocols)
- [Kalium SDK Docs](https://github.com/wireapp/kalium)
- [Signal Protocol (Proteus)](https://signal.org/docs/specifications/doubleratchet/)
- [MLS Specification](https://messaginglayersecurity.rocks/)

---

**Document Generated**: March 15, 2026  
**Last Updated**: March 15, 2026  
**Author**: Research Coordinator + Research-Assistant Subagents  
**Status**: Complete and Comprehensive

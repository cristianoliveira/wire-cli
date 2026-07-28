package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import wirecli.conversation.Conversation
import wirecli.conversation.ConversationListView
import wirecli.conversation.ConversationService
import wirecli.conversation.ConversationStatus
import wirecli.conversation.ConversationType
import wirecli.conversation.CreateConversationResult
import wirecli.conversation.DeleteConversationResult
import wirecli.conversation.GetConversationResult
import wirecli.conversation.GetMembersResult
import wirecli.conversation.ListConversationsResult
import wirecli.hooks.DaemonHookService
import wirecli.message.ConversationMessage
import wirecli.message.FetchMessagesResult
import wirecli.message.FetchMessagesView
import wirecli.message.ListRecentMessagesResult
import wirecli.message.MessageService
import wirecli.message.ReactionAction
import wirecli.message.RecentMessagesQuery
import wirecli.message.RecentMessagesView
import wirecli.message.SearchMessagesResult
import wirecli.message.SendMessageResult
import wirecli.message.ToggleReactionResult
import wirecli.runtime.DaemonProcessMarker
import wirecli.sync.ConversationSyncStatusResult
import wirecli.sync.DiagnosticsResult
import wirecli.sync.HealthMetrics
import wirecli.sync.PerConversationDiagnosticsResult
import wirecli.sync.ResetResult
import wirecli.sync.SyncService
import wirecli.sync.SyncStatus
import wirecli.sync.SyncStatusResult
import wirecli.sync.SyncStatusView
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DaemonCommandTest {
    @Test
    fun `daemon starts continuous sync and waits until process termination`() {
        var awaitedTermination = false
        val service =
            FakeSyncService(
                startResult =
                    SyncStatusResult.Success(
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = HealthMetrics(0L, 0, 100, "2026-07-14T10:00:00Z"),
                        ),
                    ),
            )
        val endpoint = FakeDaemonProcessMarker()
        val command =
            DaemonCommand(
                syncServiceProvider = { service },
                processMarkerProvider = { endpoint },
                awaitTermination = { awaitedTermination = true },
            )

        val result = execute(command)

        assertEquals(0, result.exitCode)
        assertEquals(1, service.startCalls)
        assertEquals(true, awaitedTermination)
        assertEquals(1, endpoint.startCalls)
        assertEquals(1, endpoint.closeCalls)
        assertEquals("Message sync daemon is active.", result.stdout.trim())
    }

    @Test
    fun `daemon observes configured hooks while waiting without verbose mode`() {
        var awaitedTermination = false
        val hookService = FakeDaemonHookService()
        val service =
            FakeSyncService(
                startResult =
                    SyncStatusResult.Success(
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = HealthMetrics(0L, 0, 100, "2026-07-14T10:00:00Z"),
                        ),
                    ),
            )
        val command =
            DaemonCommand(
                syncServiceProvider = { service },
                processMarkerProvider = { FakeDaemonProcessMarker() },
                daemonHookServiceProvider = { hookService },
                awaitTermination = { awaitedTermination = true },
            )

        val result = execute(command)

        assertEquals(0, result.exitCode)
        assertEquals(true, awaitedTermination)
        assertEquals(1, hookService.observeCalls)
    }

    @Test
    fun `daemon reports sync startup failure without waiting`() {
        var awaitedTermination = false
        val service =
            FakeSyncService(
                startResult = SyncStatusResult.Failure("unable to start sync", 12),
            )
        val endpoint = FakeDaemonProcessMarker()
        val command =
            DaemonCommand(
                syncServiceProvider = { service },
                processMarkerProvider = { endpoint },
                awaitTermination = { awaitedTermination = true },
            )

        val result = execute(command)

        assertEquals(1, result.exitCode)
        assertEquals(1, service.startCalls)
        assertEquals(false, awaitedTermination)
        assertEquals(0, endpoint.startCalls)
        assertEquals("unable to start sync", result.stderr.trim())
    }

    @Test
    fun `daemon records update timestamp after sync starts`() {
        var awaitedTermination = false
        val service =
            FakeSyncService(
                startResult =
                    SyncStatusResult.Success(
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = HealthMetrics(0L, 0, 100, "2026-07-14T10:00:00Z"),
                        ),
                    ),
            )
        val endpoint = FakeDaemonProcessMarker()
        val command =
            DaemonCommand(
                syncServiceProvider = { service },
                processMarkerProvider = { endpoint },
                awaitTermination = { awaitedTermination = true },
            )

        execute(command)

        assertEquals(1, endpoint.recordUpdateCalls, "daemon should record an update after sync starts")
        assertNotNull(endpoint.lastUpdateTimestamp(), "last update timestamp should be set")
    }

    @Test
    fun `daemon does not record update when sync fails to start`() {
        var awaitedTermination = false
        val service =
            FakeSyncService(
                startResult = SyncStatusResult.Failure("unable to start sync", 12),
            )
        val endpoint = FakeDaemonProcessMarker()
        val command =
            DaemonCommand(
                syncServiceProvider = { service },
                processMarkerProvider = { endpoint },
                awaitTermination = { awaitedTermination = true },
            )

        execute(command)

        assertEquals(0, endpoint.recordUpdateCalls, "no update recorded on sync failure")
    }

    @Test
    fun `daemon with --verbose emits new message events as JSON`() {
        var eventEmitted = false
        val syncService =
            FakeSyncService(
                startResult =
                    SyncStatusResult.Success(
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = HealthMetrics(0L, 0, 100, "2026-07-14T10:00:00Z"),
                        ),
                    ),
            )
        val messageService =
            FakeMessageService(
                conversations =
                    listOf(
                        Conversation(
                            id = "conv-1",
                            name = "Engineering",
                            type = ConversationType.GROUP,
                            status = ConversationStatus.ACTIVE,
                            memberCount = 2,
                            createdAt = "2026-01-01T00:00:00Z",
                            updatedAt = "2026-01-01T00:00:00Z",
                        ),
                    ),
                messages =
                    listOf(
                        FetchMessagesResult.Success(
                            FetchMessagesView(conversationId = "conv-1", messages = emptyList()),
                        ),
                        FetchMessagesResult.Success(
                            FetchMessagesView(
                                conversationId = "conv-1",
                                messages =
                                    listOf(
                                        ConversationMessage(
                                            id = "msg-1",
                                            senderId = "alice",
                                            senderName = "Alice",
                                            timestamp = "2026-07-14T10:00:00Z",
                                            content = "hello from daemon",
                                        ),
                                    ),
                            ),
                        ),
                    ),
            )
        val command =
            DaemonCommand(
                syncServiceProvider = { syncService },
                processMarkerProvider = { FakeDaemonProcessMarker() },
                messageServiceProvider = { messageService },
                conversationServiceProvider = { messageService },
                awaitTermination = { eventEmitted = true },
            )

        val result = execute(command, listOf("--verbose"))

        assertEquals(0, result.exitCode)
        assertEquals(true, eventEmitted, "should allow termination after events")
        assertTrue(result.stdout.contains("\"messageId\":\"msg-1\""), "stdout should contain message JSON")
        assertTrue(result.stdout.contains("\"senderId\":\"alice\""))
        assertTrue(result.stdout.contains("\"conversationId\":\"conv-1\""))
    }

    @Test
    fun `daemon --verbose without message service still starts sync`() {
        var awaitedTermination = false
        val syncService =
            FakeSyncService(
                startResult =
                    SyncStatusResult.Success(
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = HealthMetrics(0L, 0, 100, "2026-07-14T10:00:00Z"),
                        ),
                    ),
            )
        val command =
            DaemonCommand(
                syncServiceProvider = { syncService },
                processMarkerProvider = { FakeDaemonProcessMarker() },
                awaitTermination = { awaitedTermination = true },
            )

        val result = execute(command, listOf("--verbose"))

        assertEquals(0, result.exitCode)
        assertEquals(true, awaitedTermination)
        assertEquals(1, syncService.startCalls)
        assertTrue(result.stdout.contains("Message sync daemon is active."))
    }

    private fun execute(
        command: DaemonCommand,
        args: List<String> = emptyList(),
    ): ExecutionResult {
        val stdoutBuffer = java.io.ByteArrayOutputStream()
        val stderrBuffer = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        var exitCode = 0

        try {
            System.setOut(java.io.PrintStream(stdoutBuffer))
            System.setErr(java.io.PrintStream(stderrBuffer))
            command.parse(args)
        } catch (programResult: ProgramResult) {
            exitCode = programResult.statusCode
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        return ExecutionResult(
            exitCode = exitCode,
            stdout = stdoutBuffer.toString(Charsets.UTF_8),
            stderr = stderrBuffer.toString(Charsets.UTF_8),
        )
    }

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private class FakeDaemonHookService : DaemonHookService {
        var observeCalls = 0

        override suspend fun observe() {
            observeCalls++
        }
    }

    private class FakeDaemonProcessMarker : DaemonProcessMarker {
        var startCalls = 0
        var closeCalls = 0
        var recordUpdateCalls = 0
        private var recordedTimestamp: Instant? = null

        override fun start() {
            startCalls++
        }

        override fun isRunning(): Boolean = true

        override fun recordUpdate() {
            recordUpdateCalls++
            recordedTimestamp = Instant.now()
        }

        override fun lastUpdateTimestamp(): Instant? = recordedTimestamp

        override fun close() {
            closeCalls++
        }
    }

    private class FakeSyncService(
        private val startResult: SyncStatusResult,
    ) : SyncService {
        var startCalls: Int = 0
            private set

        override fun startContinuousSync(): SyncStatusResult {
            startCalls += 1
            return startResult
        }

        override fun forceSyncAndWait(): SyncStatusResult = error("not used")

        override fun getCurrentSyncStatus(): SyncStatusResult = error("not used")

        override fun getDiagnosticsReport(): DiagnosticsResult = error("not used")

        override fun resetSync(force: Boolean): ResetResult = error("not used")

        override fun getConversationSyncStatus(conversationId: String): ConversationSyncStatusResult = error("not used")

        override fun getPerConversationDiagnostics(conversationId: String): PerConversationDiagnosticsResult = error("not used")
    }

    private class FakeMessageService(
        private val conversations: List<Conversation> = emptyList(),
        private val messages: List<FetchMessagesResult> =
            listOf(
                FetchMessagesResult.Success(FetchMessagesView("", emptyList())),
            ),
    ) : MessageService, ConversationService {
        override fun sendMessage(
            conversationId: String,
            text: String,
        ): SendMessageResult = SendMessageResult.Success

        override fun fetchMessages(
            conversationId: String,
            limit: Int,
        ) = FetchMessagesResult.Success(FetchMessagesView(conversationId, emptyList()))

        override fun observeMessages(conversationId: String): Flow<FetchMessagesResult> = flowOf(*messages.toTypedArray())

        override fun listRecentMessages(query: RecentMessagesQuery): ListRecentMessagesResult =
            ListRecentMessagesResult.Success(RecentMessagesView(emptyList()))

        override fun searchMessages(
            query: String,
            conversationId: String?,
            limit: Int,
        ): SearchMessagesResult = SearchMessagesResult.Success(emptyList())

        override fun toggleReaction(
            conversationId: String,
            messageId: String,
            emoji: String,
        ): ToggleReactionResult = ToggleReactionResult.Success(ReactionAction.ADDED)

        override fun listConversations(): ListConversationsResult = ListConversationsResult.Success(ConversationListView(conversations))

        override fun getConversation(conversationId: String): GetConversationResult = GetConversationResult.Failure("not implemented", 13)

        override fun getMembers(conversationId: String): GetMembersResult = GetMembersResult.Failure("not implemented", 13)

        override fun createConversation(
            name: String,
            type: ConversationType,
        ): CreateConversationResult = CreateConversationResult.Failure("not implemented", 13)

        override fun deleteConversation(conversationId: String): DeleteConversationResult =
            DeleteConversationResult.Failure("not implemented", 13)

        override fun getMemberCount(conversationId: String): GetConversationResult = GetConversationResult.Failure("not implemented", 13)
    }
}

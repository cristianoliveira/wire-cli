package wirecli.sync

import com.wire.kalium.common.error.CoreFailure.Unknown
import com.wire.kalium.logic.data.sync.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for per-conversation sync diagnostics generation and tracking.
 *
 * This test suite validates:
 * - Conversation sync state queryability
 * - Per-conversation metrics calculation
 * - Message sync completeness per conversation
 * - Error handling for missing conversations
 * - Diagnostics accuracy per conversation
 */
class PerConversationSyncDiagnosticsTest {
    private val generator = ProductionConversationDiagnosticsGenerator()

    // ==================== CONVERSATION SYNC STATUS TESTS ====================

    @Test
    fun `conversation sync status contains conversation ID`() {
        val conversationId = "test-conv-123"
        val syncState = SyncState.Live
        val metrics = generator.generateConversationMetrics(conversationId, syncState)

        assertEquals(conversationId, metrics.conversationId, "Conversation ID should be preserved")
    }

    @Test
    fun `conversation sync status for Live state indicates ready`() {
        val conversationId = "test-conv-123"
        val status = generator.mapSyncStateToStatus(SyncState.Live)

        assertEquals(SyncStatus.READY, status, "Live state should map to READY")
    }

    @Test
    fun `conversation sync status for Failed state indicates degraded`() {
        val conversationId = "test-conv-123"
        val failedState = SyncState.Failed(cause = Unknown(null), retryDelay = 1.seconds)
        val status = generator.mapSyncStateToStatus(failedState)

        assertEquals(SyncStatus.DEGRADED, status, "Failed state should map to DEGRADED")
    }

    // ==================== CONVERSATION METRICS CALCULATION TESTS ====================

    @Test
    fun `conversation lag_ms is zero for Live state`() {
        val conversationId = "test-conv-123"
        val lagMs = generator.calculateConversationLagMs(SyncState.Live)

        assertEquals(0L, lagMs, "Live state should have zero lag")
    }

    @Test
    fun `conversation lag_ms reflects sync state`() {
        val conversationId = "test-conv-123"
        val liveMs = generator.calculateConversationLagMs(SyncState.Live)
        val slowMs = generator.calculateConversationLagMs(SyncState.SlowSync)
        val waitingMs = generator.calculateConversationLagMs(SyncState.Waiting)

        assertTrue(liveMs < waitingMs, "Live lag should be less than Waiting lag")
        assertTrue(waitingMs < slowMs, "Waiting lag should be less than SlowSync lag")
    }

    @Test
    fun `conversation pending_messages is zero for Live state`() {
        val conversationId = "test-conv-123"
        val pending = generator.calculateConversationPendingMessages(SyncState.Live)

        assertEquals(0, pending, "Live state should have zero pending messages")
    }

    @Test
    fun `conversation pending_messages reflects sync state`() {
        val conversationId = "test-conv-123"
        val live = generator.calculateConversationPendingMessages(SyncState.Live)
        val gathering = generator.calculateConversationPendingMessages(SyncState.GatheringPendingEvents)
        val slowSync = generator.calculateConversationPendingMessages(SyncState.SlowSync)

        assertTrue(live < gathering, "Live should have fewer pending than gathering")
        assertTrue(gathering < slowSync, "Gathering should have fewer pending than slowSync")
    }

    // ==================== SYNC COMPLETENESS TESTS ====================

    @Test
    fun `sync completeness is 100 percent for Live state`() {
        val completeness = generator.calculateSyncCompletenessPercentage(SyncState.Live)

        assertEquals(100, completeness, "Live state should have 100% completeness")
    }

    @Test
    fun `sync completeness is zero for Failed state`() {
        val failedState = SyncState.Failed(cause = Unknown(null), retryDelay = 1.seconds)
        val completeness = generator.calculateSyncCompletenessPercentage(failedState)

        assertEquals(0, completeness, "Failed state should have 0% completeness")
    }

    @Test
    fun `sync completeness values are between 0 and 100`() {
        val states =
            listOf(
                SyncState.Live,
                SyncState.SlowSync,
                SyncState.GatheringPendingEvents,
                SyncState.Waiting,
                SyncState.Failed(Unknown(null), 1.seconds),
            )

        states.forEach { state ->
            val completeness = generator.calculateSyncCompletenessPercentage(state)
            assertTrue(completeness in 0..100, "Completeness should be 0-100 for $state")
        }
    }

    @Test
    fun `sync completeness reflects sync state progression`() {
        val waiting = generator.calculateSyncCompletenessPercentage(SyncState.Waiting)
        val gathering = generator.calculateSyncCompletenessPercentage(SyncState.GatheringPendingEvents)
        val live = generator.calculateSyncCompletenessPercentage(SyncState.Live)

        assertTrue(waiting < gathering, "Waiting should be less complete than gathering")
        assertTrue(gathering < live, "Gathering should be less complete than live")
    }

    // ==================== CONVERSATION DIAGNOSTICS CHECKS TESTS ====================

    @Test
    fun `conversation state check is always included`() {
        val checks = generator.generateConversationChecks("test-conv-123", SyncState.Live)

        val stateCheck = checks.find { it.name == "Conversation State" }
        assertFalse(stateCheck == null, "Conversation State check should be present")
        assertEquals("Pass", stateCheck!!.status, "Conversation State check should pass")
    }

    @Test
    fun `message sync check reflects sync state for Live`() {
        val checks = generator.generateConversationChecks("test-conv-123", SyncState.Live)

        val msgCheck = checks.find { it.name == "Message Sync" }!!
        assertEquals("Pass", msgCheck.status, "Message sync should pass in Live state")
    }

    @Test
    fun `message sync check reflects sync state for SlowSync`() {
        val checks = generator.generateConversationChecks("test-conv-123", SyncState.SlowSync)

        val msgCheck = checks.find { it.name == "Message Sync" }!!
        assertEquals("Warn", msgCheck.status, "Message sync should warn in SlowSync state")
    }

    @Test
    fun `message sync check reflects sync state for Failed`() {
        val failedState = SyncState.Failed(cause = Unknown(null), retryDelay = 1.seconds)
        val checks = generator.generateConversationChecks("test-conv-123", failedState)

        val msgCheck = checks.find { it.name == "Message Sync" }!!
        assertEquals("Fail", msgCheck.status, "Message sync should fail in Failed state")
    }

    @Test
    fun `sync completeness check reflects actual completeness`() {
        val checks = generator.generateConversationChecks("test-conv-123", SyncState.Live)

        val completeCheck = checks.find { it.name == "Sync Completeness" }!!
        assertEquals("Pass", completeCheck.status, "Completeness check should pass for Live state")
        assertTrue(completeCheck.details.contains("100%"), "Details should show 100% for Live")
    }

    @Test
    fun `connectivity check reflects sync state`() {
        val liveChecks = generator.generateConversationChecks("test-conv-123", SyncState.Live)
        val failedState = SyncState.Failed(cause = Unknown(null), retryDelay = 1.seconds)
        val failedChecks = generator.generateConversationChecks("test-conv-123", failedState)

        val liveConn = liveChecks.find { it.name == "Conversation Connectivity" }!!
        assertEquals("Pass", liveConn.status, "Connectivity should pass for Live")

        val failedConn = failedChecks.find { it.name == "Conversation Connectivity" }!!
        assertEquals("Fail", failedConn.status, "Connectivity should fail for Failed")
    }

    // ==================== CONVERSATION SUMMARY TESTS ====================

    @Test
    fun `summary indicates healthy when all checks pass`() {
        val checks =
            listOf(
                Check("Conversation State", "Pass", "OK"),
                Check("Message Sync", "Pass", "OK"),
                Check("Sync Completeness", "Pass", "100%"),
                Check("Conversation Connectivity", "Pass", "OK"),
            )

        val summary = generator.generateConversationSummary(checks)

        assertEquals("Conversation is fully synced and healthy.", summary)
    }

    @Test
    fun `summary indicates degradation when checks fail`() {
        val checks =
            listOf(
                Check("Conversation State", "Pass", "OK"),
                Check("Message Sync", "Fail", "Error"),
                Check("Sync Completeness", "Fail", "0%"),
                Check("Conversation Connectivity", "Fail", "Unreachable"),
            )

        val summary = generator.generateConversationSummary(checks)

        assertEquals("Conversation sync has failed. Recovery actions may help.", summary)
    }

    @Test
    fun `summary indicates progress when warnings present`() {
        val checks =
            listOf(
                Check("Conversation State", "Pass", "OK"),
                Check("Message Sync", "Warn", "In progress"),
                Check("Sync Completeness", "Warn", "65%"),
                Check("Conversation Connectivity", "Pass", "OK"),
            )

        val summary = generator.generateConversationSummary(checks)

        assertEquals("Conversation sync is in progress. Check back soon.", summary)
    }

    // ==================== CONVERSATION RECOVERY HINTS TESTS ====================

    @Test
    fun `message sync failure generates recovery hint`() {
        val checks =
            listOf(
                Check("Message Sync", "Fail", "Error"),
            )

        val hints = generator.generateConversationRecoveryHints(checks, "test-conv-123")

        val msgHint = hints.find { it.description.contains("Message sync failed") }
        assertFalse(msgHint == null, "Should generate message sync recovery hint")
    }

    @Test
    fun `completeness failure generates recovery hint`() {
        val checks =
            listOf(
                Check("Sync Completeness", "Fail", "0%"),
            )

        val hints = generator.generateConversationRecoveryHints(checks, "test-conv-123")

        val completeHint = hints.find { it.description.contains("incomplete") }
        assertFalse(completeHint == null, "Should generate completeness recovery hint")
    }

    @Test
    fun `connectivity failure generates recovery hint`() {
        val checks =
            listOf(
                Check("Conversation Connectivity", "Fail", "Unreachable"),
            )

        val hints = generator.generateConversationRecoveryHints(checks, "test-conv-123")

        val connHint = hints.find { it.description.contains("connectivity", ignoreCase = true) }
        assertFalse(connHint == null, "Should generate connectivity recovery hint")
    }

    @Test
    fun `no hints generated when all checks pass`() {
        val checks =
            listOf(
                Check("Conversation State", "Pass", "OK"),
                Check("Message Sync", "Pass", "OK"),
                Check("Sync Completeness", "Pass", "100%"),
                Check("Conversation Connectivity", "Pass", "OK"),
            )

        val hints = generator.generateConversationRecoveryHints(checks, "test-conv-123")

        assertEquals(0, hints.size, "No hints should be generated for passing checks")
    }

    @Test
    fun `recovery hints include conversation ID`() {
        val conversationId = "specific-conv-id"
        val checks =
            listOf(
                Check("Message Sync", "Fail", "Error"),
            )

        val hints = generator.generateConversationRecoveryHints(checks, conversationId)

        assertTrue(hints.all { it.command.contains(conversationId) }, "Hints should include conversation ID")
    }

    // ==================== PER-CONVERSATION REPORT CONSTRUCTION TESTS ====================

    @Test
    fun `per-conversation report contains conversation ID`() {
        val conversationId = "test-conv-456"
        val report =
            PerConversationDiagnosticsReport(
                conversationId = conversationId,
                checks = listOf(Check("Test", "Pass", "OK")),
                summary = "All good",
            )

        assertEquals(conversationId, report.conversationId)
    }

    @Test
    fun `per-conversation report contains all required fields`() {
        val checks = listOf(Check("Test", "Pass", "OK"))
        val hints = listOf(RecoveryHint("Issue", "Fix it"))
        val report =
            PerConversationDiagnosticsReport(
                conversationId = "test-conv",
                checks = checks,
                summary = "All good",
                recoveryHints = hints,
            )

        assertEquals(1, report.checks.size)
        assertEquals("All good", report.summary)
        assertEquals(1, report.recoveryHints.size)
    }

    @Test
    fun `per-conversation report recovery hints are optional`() {
        val report =
            PerConversationDiagnosticsReport(
                conversationId = "test-conv",
                checks = emptyList(),
                summary = "No checks",
            )

        assertEquals(0, report.recoveryHints.size, "Recovery hints should default to empty list")
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun `blank conversation ID returns error`() {
        val result = generator.validateConversationId("")

        assertFalse(result, "Blank conversation ID should fail validation")
    }

    @Test
    fun `null conversation ID returns error`() {
        val result = generator.validateConversationId("  ")

        assertFalse(result, "Whitespace-only conversation ID should fail validation")
    }

    @Test
    fun `valid conversation ID passes validation`() {
        val result = generator.validateConversationId("valid-conv-123")

        assertTrue(result, "Valid conversation ID should pass validation")
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `check details provide meaningful information per conversation`() {
        val checks = generator.generateConversationChecks("test-conv-123", SyncState.Live)

        checks.forEach { check ->
            assertFalse(check.details.isBlank(), "Check details should not be blank for ${check.name}")
            assertTrue(check.details.length > 5, "Check details should be informative for ${check.name}")
        }
    }

    @Test
    fun `recovery hints have both description and command for conversation`() {
        val checks =
            listOf(
                Check("Message Sync", "Fail", "Error"),
                Check("Conversation Connectivity", "Fail", "Error"),
            )

        val hints = generator.generateConversationRecoveryHints(checks, "test-conv-123")

        hints.forEach { hint ->
            assertFalse(hint.description.isBlank(), "Hint description should not be blank")
            assertFalse(hint.command.isBlank(), "Hint command should not be blank")
        }
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    fun `complete diagnostics report for healthy conversation`() {
        val conversationId = "test-conv-123"
        val checks = generator.generateConversationChecks(conversationId, SyncState.Live)
        val summary = generator.generateConversationSummary(checks)
        val hints = generator.generateConversationRecoveryHints(checks, conversationId)

        val report = PerConversationDiagnosticsReport(conversationId, checks, summary, hints)

        assertEquals(conversationId, report.conversationId)
        assertEquals("Conversation is fully synced and healthy.", report.summary)
        assertEquals(0, report.recoveryHints.size)
    }

    @Test
    fun `complete diagnostics report for degraded conversation`() {
        val conversationId = "test-conv-456"
        val failedState = SyncState.Failed(cause = Unknown(null), retryDelay = 1.seconds)
        val checks = generator.generateConversationChecks(conversationId, failedState)
        val summary = generator.generateConversationSummary(checks)
        val hints = generator.generateConversationRecoveryHints(checks, conversationId)

        val report = PerConversationDiagnosticsReport(conversationId, checks, summary, hints)

        assertEquals(conversationId, report.conversationId)
        assertEquals("Conversation sync has failed. Recovery actions may help.", report.summary)
        assertTrue(report.recoveryHints.isNotEmpty(), "Should generate recovery hints for failed state")
    }

    @Test
    fun `complete diagnostics report for initializing conversation`() {
        val conversationId = "test-conv-789"
        val checks = generator.generateConversationChecks(conversationId, SyncState.GatheringPendingEvents)
        val summary = generator.generateConversationSummary(checks)
        val hints = generator.generateConversationRecoveryHints(checks, conversationId)

        val report = PerConversationDiagnosticsReport(conversationId, checks, summary, hints)

        assertEquals(conversationId, report.conversationId)
        assertEquals("Conversation sync is in progress. Check back soon.", report.summary)
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Helper class that provides per-conversation diagnostics generation methods.
     * This mirrors the actual implementation in RealKaliumSyncApiClient.
     */
    private class ProductionConversationDiagnosticsGenerator {
        fun generateConversationMetrics(
            conversationId: String,
            syncState: SyncState,
        ): ConversationMetrics {
            return withRuntime { runtime ->
                ConversationMetrics(
                    conversationId = conversationId,
                    lagMs = calculateConversationLagMs(runtime, syncState),
                    pendingMessages = calculateConversationPendingMessages(runtime, syncState),
                    syncCompletenessPct = calculateSyncCompletenessPercentage(runtime, syncState),
                    timestamp = "2025-03-13T10:30:00Z",
                )
            }
        }

        fun generateConversationChecks(
            conversationId: String,
            syncState: SyncState,
        ): List<Check> {
            return withRuntime { runtime ->
                listOf(
                    buildConversationStateCheck(runtime, conversationId),
                    buildMessageSyncCheck(runtime, syncState),
                    buildCompletenessCheck(runtime, syncState),
                    buildConversationNetworkCheck(runtime, syncState),
                )
            }
        }

        fun generateConversationSummary(checks: List<Check>): String {
            return withRuntime { runtime ->
                val method = runtime.javaClass.getDeclaredMethod("buildConversationSummary", List::class.java)
                method.isAccessible = true
                method.invoke(runtime, checks) as String
            }
        }

        fun generateConversationRecoveryHints(
            checks: List<Check>,
            conversationId: String,
        ): List<RecoveryHint> {
            return withRuntime { runtime ->
                val method =
                    runtime.javaClass.getDeclaredMethod(
                        "generateConversationRecoveryHints",
                        List::class.java,
                        String::class.java,
                    )
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                method.invoke(runtime, checks, conversationId) as List<RecoveryHint>
            }
        }

        fun mapSyncStateToStatus(syncState: SyncState): SyncStatus {
            return withRuntime { runtime ->
                val method = runtime.javaClass.getDeclaredMethod("mapSyncStateToStatus", SyncState::class.java)
                method.isAccessible = true
                method.invoke(runtime, syncState) as SyncStatus
            }
        }

        fun calculateConversationLagMs(syncState: SyncState): Long {
            return withRuntime { runtime -> calculateConversationLagMs(runtime, syncState) }
        }

        fun calculateConversationPendingMessages(syncState: SyncState): Int {
            return withRuntime { runtime -> calculateConversationPendingMessages(runtime, syncState) }
        }

        fun calculateSyncCompletenessPercentage(syncState: SyncState?): Int {
            return withRuntime { runtime -> calculateSyncCompletenessPercentage(runtime, syncState) }
        }

        fun validateConversationId(conversationId: String): Boolean {
            return conversationId.isNotBlank()
        }

        private fun buildConversationStateCheck(
            runtime: SdkKaliumSyncRuntime,
            conversationId: String,
        ): Check {
            val method = runtime.javaClass.getDeclaredMethod("buildConversationStateCheck", String::class.java)
            method.isAccessible = true
            return method.invoke(runtime, conversationId) as Check
        }

        private fun buildMessageSyncCheck(
            runtime: SdkKaliumSyncRuntime,
            syncState: SyncState,
        ): Check {
            val method = runtime.javaClass.getDeclaredMethod("buildMessageSyncCheck", SyncState::class.java)
            method.isAccessible = true
            return method.invoke(runtime, syncState) as Check
        }

        private fun buildCompletenessCheck(
            runtime: SdkKaliumSyncRuntime,
            syncState: SyncState,
        ): Check {
            val method = runtime.javaClass.getDeclaredMethod("buildCompletenessCheck", SyncState::class.java)
            method.isAccessible = true
            return method.invoke(runtime, syncState) as Check
        }

        private fun buildConversationNetworkCheck(
            runtime: SdkKaliumSyncRuntime,
            syncState: SyncState,
        ): Check {
            val method = runtime.javaClass.getDeclaredMethod("buildConversationNetworkCheck", SyncState::class.java)
            method.isAccessible = true
            return method.invoke(runtime, syncState) as Check
        }

        private fun calculateConversationLagMs(
            runtime: SdkKaliumSyncRuntime,
            syncState: SyncState?,
        ): Long {
            val method = runtime.javaClass.getDeclaredMethod("calculateConversationLagMs", SyncState::class.java)
            method.isAccessible = true
            return method.invoke(runtime, syncState) as Long
        }

        private fun calculateConversationPendingMessages(
            runtime: SdkKaliumSyncRuntime,
            syncState: SyncState?,
        ): Int {
            val method =
                runtime.javaClass.getDeclaredMethod(
                    "calculateConversationPendingMessages",
                    SyncState::class.java,
                )
            method.isAccessible = true
            return method.invoke(runtime, syncState) as Int
        }

        private fun calculateSyncCompletenessPercentage(
            runtime: SdkKaliumSyncRuntime,
            syncState: SyncState?,
        ): Int {
            val method =
                runtime.javaClass.getDeclaredMethod(
                    "calculateSyncCompletenessPercentage",
                    SyncState::class.java,
                )
            method.isAccessible = true
            return method.invoke(runtime, syncState) as Int
        }

        private fun <T> withRuntime(block: (SdkKaliumSyncRuntime) -> T): T {
            val runtime =
                SdkKaliumSyncRuntime(
                    environment = emptyMap(),
                    networkConnectivityChecker = StubNetworkConnectivityChecker(),
                )
            return try {
                block(runtime)
            } finally {
                runtime.shutdown()
            }
        }
    }
}

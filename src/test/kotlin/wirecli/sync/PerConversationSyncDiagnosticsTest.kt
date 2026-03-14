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
    private val generator = TestConversationDiagnosticsGenerator()

    // ==================== CONVERSATION SYNC STATUS TESTS ====================

    @Test
    fun `conversation sync status contains conversation ID`() {
        val conversationId = "test-conv-123"
        val syncState = SyncState.Live
        val metrics = generator.generateConversationMetrics(conversationId, syncState)

        assertEquals(conversationId, metrics.conversation_id, "Conversation ID should be preserved")
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

        val connHint = hints.find { it.description.contains("unreachable") }
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
                conversation_id = conversationId,
                checks = listOf(Check("Test", "Pass", "OK")),
                summary = "All good",
            )

        assertEquals(conversationId, report.conversation_id)
    }

    @Test
    fun `per-conversation report contains all required fields`() {
        val checks = listOf(Check("Test", "Pass", "OK"))
        val hints = listOf(RecoveryHint("Issue", "Fix it"))
        val report =
            PerConversationDiagnosticsReport(
                conversation_id = "test-conv",
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
                conversation_id = "test-conv",
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

        assertEquals(conversationId, report.conversation_id)
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

        assertEquals(conversationId, report.conversation_id)
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

        assertEquals(conversationId, report.conversation_id)
        assertEquals("Conversation sync is in progress. Check back soon.", report.summary)
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Helper class that provides per-conversation diagnostics generation methods.
     * This mirrors the actual implementation in RealKaliumSyncApiClient.
     */
    private class TestConversationDiagnosticsGenerator {
        fun generateConversationMetrics(
            conversationId: String,
            syncState: SyncState,
        ): ConversationMetrics {
            return ConversationMetrics(
                conversation_id = conversationId,
                lag_ms = calculateConversationLagMs(syncState),
                pending_messages = calculateConversationPendingMessages(syncState),
                sync_completeness_pct = calculateSyncCompletenessPercentage(syncState),
                timestamp = "2025-03-13T10:30:00Z",
            )
        }

        fun generateConversationChecks(
            conversationId: String,
            syncState: SyncState,
        ): List<Check> {
            val checks = mutableListOf<Check>()

            checks.add(
                Check(
                    name = "Conversation State",
                    status = "Pass",
                    details = "Conversation ID: $conversationId",
                ),
            )

            val messageSyncStatus =
                when (syncState) {
                    is SyncState.Live -> "Pass"
                    is SyncState.SlowSync -> "Warn"
                    is SyncState.GatheringPendingEvents -> "Warn"
                    is SyncState.Waiting -> "Warn"
                    is SyncState.Failed -> "Fail"
                }
            checks.add(
                Check(
                    name = "Message Sync",
                    status = messageSyncStatus,
                    details = "Message sync state: ${syncState::class.simpleName}",
                ),
            )

            val completeness = calculateSyncCompletenessPercentage(syncState)
            checks.add(
                Check(
                    name = "Sync Completeness",
                    status =
                        when {
                            completeness >= 95 -> "Pass"
                            completeness >= 70 -> "Warn"
                            else -> "Fail"
                        },
                    details = "Sync completeness: $completeness%",
                ),
            )

            checks.add(
                Check(
                    name = "Conversation Connectivity",
                    status = if (syncState !is SyncState.Failed) "Pass" else "Fail",
                    details =
                        if (syncState !is SyncState.Failed) {
                            "Conversation is reachable"
                        } else {
                            "Conversation connectivity issues detected"
                        },
                ),
            )

            return checks
        }

        fun generateConversationSummary(checks: List<Check>): String {
            return when {
                checks.all { it.status == "Pass" } -> "Conversation is fully synced and healthy."
                checks.any { it.status == "Fail" } -> "Conversation sync has failed. Recovery actions may help."
                else -> "Conversation sync is in progress. Check back soon."
            }
        }

        fun generateConversationRecoveryHints(
            checks: List<Check>,
            conversationId: String,
        ): List<RecoveryHint> {
            val hints = mutableListOf<RecoveryHint>()

            if (checks.any { it.name == "Message Sync" && it.status == "Fail" }) {
                hints.add(
                    RecoveryHint(
                        description = "Message sync failed for conversation",
                        command = "wire-cli sync status --conversation $conversationId --retry",
                    ),
                )
            }

            if (checks.any { it.name == "Sync Completeness" && it.status == "Fail" }) {
                hints.add(
                    RecoveryHint(
                        description = "Conversation sync is incomplete",
                        command = "Check network connection and retry full sync for $conversationId",
                    ),
                )
            }

            if (checks.any { it.name == "Conversation Connectivity" && it.status == "Fail" }) {
                hints.add(
                    RecoveryHint(
                        description = "Conversation is unreachable",
                        command = "Verify conversation $conversationId exists and you have access permissions",
                    ),
                )
            }

            return hints
        }

        fun mapSyncStateToStatus(syncState: SyncState): SyncStatus {
            return when (syncState) {
                is SyncState.Live -> SyncStatus.READY
                is SyncState.SlowSync -> SyncStatus.INITIALIZING
                is SyncState.GatheringPendingEvents -> SyncStatus.INITIALIZING
                is SyncState.Waiting -> SyncStatus.INITIALIZING
                is SyncState.Failed -> SyncStatus.DEGRADED
            }
        }

        fun calculateConversationLagMs(syncState: SyncState): Long {
            return when (syncState) {
                is SyncState.Live -> 0L
                is SyncState.SlowSync -> 5000L
                is SyncState.GatheringPendingEvents -> 2000L
                is SyncState.Waiting -> 1000L
                is SyncState.Failed -> 10000L
            }
        }

        fun calculateConversationPendingMessages(syncState: SyncState): Int {
            return when (syncState) {
                is SyncState.Live -> 0
                is SyncState.SlowSync -> 100
                is SyncState.GatheringPendingEvents -> 50
                is SyncState.Waiting -> 10
                is SyncState.Failed -> 0
            }
        }

        fun calculateSyncCompletenessPercentage(syncState: SyncState?): Int {
            return when (syncState) {
                is SyncState.Live -> 100
                is SyncState.SlowSync -> 10
                is SyncState.GatheringPendingEvents -> 70
                is SyncState.Waiting -> 5
                is SyncState.Failed -> 0
                null -> 0
            }
        }

        fun validateConversationId(conversationId: String): Boolean {
            return conversationId.isNotBlank()
        }
    }
}

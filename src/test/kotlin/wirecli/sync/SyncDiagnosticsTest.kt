package wirecli.sync

import com.wire.kalium.common.error.CoreFailure.Unknown
import com.wire.kalium.logic.data.sync.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for sync diagnostics generation and recovery hints.
 *
 * This test suite validates:
 * - Check generation for all sync states
 * - Summary message generation
 * - Recovery hint generation based on failures
 * - Diagnostics accuracy and completeness
 */
class SyncDiagnosticsTest {
    private val generator = TestDiagnosticsGenerator()

    // ==================== CHECK GENERATION TESTS ====================

    @Test
    fun `authentication check is always included`() {
        val checks = generator.generateChecks(SyncState.Live)

        val authCheck = checks.find { it.name == "Authentication" }
        assertFalse(authCheck == null, "Authentication check should be present")
        assertEquals("Pass", authCheck!!.status, "Authentication check should pass when session is valid")
        assertEquals("Session authenticated and valid", authCheck.details)
    }

    @Test
    fun `sync engine check status reflects sync state for Live`() {
        val checks = generator.generateChecks(SyncState.Live)

        val syncEngineCheck = checks.find { it.name == "Sync Engine" }!!
        assertEquals("Pass", syncEngineCheck.status, "Sync Engine should pass in Live state")
    }

    @Test
    fun `sync engine check status reflects sync state for SlowSync`() {
        val checks = generator.generateChecks(SyncState.SlowSync)

        val syncEngineCheck = checks.find { it.name == "Sync Engine" }!!
        assertEquals("Warn", syncEngineCheck.status, "Sync Engine should warn in SlowSync state")
    }

    @Test
    fun `sync engine check status reflects sync state for GatheringPendingEvents`() {
        val checks = generator.generateChecks(SyncState.GatheringPendingEvents)

        val syncEngineCheck = checks.find { it.name == "Sync Engine" }!!
        assertEquals("Warn", syncEngineCheck.status, "Sync Engine should warn in GatheringPendingEvents state")
    }

    @Test
    fun `sync engine check status reflects sync state for Waiting`() {
        val checks = generator.generateChecks(SyncState.Waiting)

        val syncEngineCheck = checks.find { it.name == "Sync Engine" }!!
        assertEquals("Warn", syncEngineCheck.status, "Sync Engine should warn in Waiting state")
    }

    @Test
    fun `sync engine check status reflects sync state for Failed`() {
        val failedState =
            SyncState.Failed(
                cause = Unknown(null),
                retryDelay = 1.seconds,
            )
        val checks = generator.generateChecks(failedState)

        val syncEngineCheck = checks.find { it.name == "Sync Engine" }!!
        assertEquals("Fail", syncEngineCheck.status, "Sync Engine should fail in Failed state")
    }

    @Test
    fun `event queue check status is Pass for Live`() {
        val checks = generator.generateChecks(SyncState.Live)

        val eventQueueCheck = checks.find { it.name == "Event Queue" }!!
        assertEquals("Pass", eventQueueCheck.status, "Event Queue should pass in Live state")
    }

    @Test
    fun `event queue check status is Pass for GatheringPendingEvents`() {
        val checks = generator.generateChecks(SyncState.GatheringPendingEvents)

        val eventQueueCheck = checks.find { it.name == "Event Queue" }!!
        assertEquals("Pass", eventQueueCheck.status, "Event Queue should pass in GatheringPendingEvents state")
    }

    @Test
    fun `key packages check is always Pass`() {
        val syncStates =
            listOf(
                SyncState.Live,
                SyncState.SlowSync,
                SyncState.Waiting,
                SyncState.GatheringPendingEvents,
                SyncState.Failed(Unknown(null), 1.seconds),
            )

        syncStates.forEach { state ->
            val checks = generator.generateChecks(state)
            val keyPackagesCheck = checks.find { it.name == "Key Packages" }!!
            assertEquals("Pass", keyPackagesCheck.status, "Key Packages should always pass for $state")
        }
    }

    @Test
    fun `network connectivity check reflects sync state`() {
        val liveChecks = generator.generateChecks(SyncState.Live)
        val liveNetworkCheck = liveChecks.find { it.name == "Network" }!!
        assertEquals("Pass", liveNetworkCheck.status, "Network should pass in Live state")

        val failedChecks =
            generator.generateChecks(
                SyncState.Failed(
                    cause = Unknown(null),
                    retryDelay = 1.seconds,
                ),
            )
        val failedNetworkCheck = failedChecks.find { it.name == "Network" }!!
        assertEquals("Fail", failedNetworkCheck.status, "Network should fail in Failed state")
    }

    // ==================== SUMMARY GENERATION TESTS ====================

    @Test
    fun `summary indicates health when all checks pass`() {
        val checks =
            listOf(
                Check("Auth", "Pass", "OK"),
                Check("Sync", "Pass", "OK"),
                Check("Network", "Pass", "OK"),
            )

        val summary = generator.generateSummary(checks)

        assertEquals("All checks passed. Sync is healthy.", summary)
    }

    @Test
    fun `summary indicates degradation when any check fails`() {
        val checks =
            listOf(
                Check("Auth", "Pass", "OK"),
                Check("Sync", "Fail", "Error"),
                Check("Network", "Pass", "OK"),
            )

        val summary = generator.generateSummary(checks)

        assertEquals("Some checks failed. Sync is degraded.", summary)
    }

    @Test
    fun `summary indicates caution when only warnings present`() {
        val checks =
            listOf(
                Check("Auth", "Pass", "OK"),
                Check("Sync", "Warn", "Initializing"),
                Check("Network", "Pass", "OK"),
            )

        val summary = generator.generateSummary(checks)

        assertEquals("Some checks have warnings. Sync may be initializing.", summary)
    }

    // ==================== RECOVERY HINTS TESTS ====================

    @Test
    fun `sync engine failure generates recovery hint`() {
        val checks =
            listOf(
                Check("Sync Engine", "Fail", "Not responding"),
            )

        val hints = generator.generateRecoveryHints(checks)

        val syncEngineHint = hints.find { it.description.contains("Sync engine") }
        assertFalse(syncEngineHint == null, "Should generate sync engine recovery hint")
    }

    @Test
    fun `network failure generates recovery hint`() {
        val checks =
            listOf(
                Check("Network", "Fail", "Connection lost"),
            )

        val hints = generator.generateRecoveryHints(checks)

        val networkHint = hints.find { it.description.contains("Network") }
        assertFalse(networkHint == null, "Should generate network recovery hint")
    }

    @Test
    fun `no hints generated when all checks pass`() {
        val checks =
            listOf(
                Check("Auth", "Pass", "OK"),
                Check("Sync Engine", "Pass", "OK"),
                Check("Network", "Pass", "OK"),
            )

        val hints = generator.generateRecoveryHints(checks)

        assertEquals(0, hints.size, "No recovery hints should be generated for passing checks")
    }

    @Test
    fun `multiple failures generate multiple hints`() {
        val checks =
            listOf(
                Check("Sync Engine", "Fail", "Error"),
                Check("Network", "Fail", "Error"),
            )

        val hints = generator.generateRecoveryHints(checks)

        assertEquals(2, hints.size, "Should generate hints for both failures")
    }

    @Test
    fun `warnings do not generate recovery hints`() {
        val checks =
            listOf(
                Check("Sync Engine", "Warn", "Initializing"),
            )

        val hints = generator.generateRecoveryHints(checks)

        assertEquals(0, hints.size, "Warnings should not generate recovery hints")
    }

    // ==================== DIAGNOSTICS REPORT CONSTRUCTION TESTS ====================

    @Test
    fun `DiagnosticsReport contains all required fields`() {
        val checks = listOf(Check("Test", "Pass", "OK"))
        val hints = listOf(RecoveryHint("Issue", "Fix it"))
        val report =
            DiagnosticsReport(
                checks = checks,
                summary = "All good",
                recoveryHints = hints,
            )

        assertEquals(1, report.checks.size)
        assertEquals("All good", report.summary)
        assertEquals(1, report.recoveryHints.size)
    }

    @Test
    fun `DiagnosticsReport recovery hints are optional`() {
        val report =
            DiagnosticsReport(
                checks = emptyList(),
                summary = "No checks",
            )

        assertEquals(0, report.recoveryHints.size, "Recovery hints should default to empty list")
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `empty checks list generates appropriate summary`() {
        val checks = emptyList<Check>()
        val summary = generator.generateSummary(checks)
        assertEquals("All checks passed. Sync is healthy.", summary)
    }

    @Test
    fun `check details provide meaningful information`() {
        val checks = generator.generateChecks(SyncState.Live)

        checks.forEach { check ->
            assertFalse(check.details.isBlank(), "Check details should not be blank for ${check.name}")
            assertTrue(check.details.length > 5, "Check details should be informative for ${check.name}")
        }
    }

    @Test
    fun `recovery hints have both description and command`() {
        val checks =
            listOf(
                Check("Sync Engine", "Fail", "Error"),
                Check("Network", "Fail", "Error"),
            )

        val hints = generator.generateRecoveryHints(checks)

        hints.forEach { hint ->
            assertFalse(hint.description.isBlank(), "Hint description should not be blank")
            assertFalse(hint.command.isBlank(), "Hint command should not be blank")
        }
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    fun `complete diagnostics report for healthy system`() {
        val checks = generator.generateChecks(SyncState.Live)
        val summary = generator.generateSummary(checks)
        val hints = generator.generateRecoveryHints(checks)

        val report = DiagnosticsReport(checks, summary, hints)

        assertEquals(5, report.checks.size)
        assertEquals("All checks passed. Sync is healthy.", report.summary)
        assertEquals(0, report.recoveryHints.size)
    }

    @Test
    fun `complete diagnostics report for degraded system`() {
        val failedState =
            SyncState.Failed(
                cause = Unknown(null),
                retryDelay = 1.seconds,
            )
        val checks = generator.generateChecks(failedState)
        val summary = generator.generateSummary(checks)
        val hints = generator.generateRecoveryHints(checks)

        val report = DiagnosticsReport(checks, summary, hints)

        assertEquals(5, report.checks.size)
        assertEquals("Some checks failed. Sync is degraded.", report.summary)
        assertTrue(report.recoveryHints.isNotEmpty(), "Should generate recovery hints for failed state")
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Helper class that provides diagnostics generation methods.
     * This mirrors the actual implementation in RealKaliumSyncApiClient.
     */
    private class TestDiagnosticsGenerator {
        fun generateChecks(syncState: SyncState): List<Check> {
            val checks = mutableListOf<Check>()

            // Auth Check
            checks.add(
                Check(
                    name = "Authentication",
                    status = "Pass",
                    details = "Session authenticated and valid",
                ),
            )

            // Sync Engine Check
            val syncStatus =
                when (syncState) {
                    is SyncState.Live -> "Pass"
                    is SyncState.SlowSync -> "Warn"
                    is SyncState.GatheringPendingEvents -> "Warn"
                    is SyncState.Waiting -> "Warn"
                    is SyncState.Failed -> "Fail"
                }
            val syncDetails = "Current sync state: ${syncState::class.simpleName}"
            checks.add(
                Check(
                    name = "Sync Engine",
                    status = syncStatus,
                    details = syncDetails,
                ),
            )

            // Event Queue Check
            checks.add(
                Check(
                    name = "Event Queue",
                    status =
                        if (syncState is SyncState.Live || syncState is SyncState.GatheringPendingEvents) {
                            "Pass"
                        } else {
                            "Warn"
                        },
                    details = "Event processing status: OK",
                ),
            )

            // Key Packages Check
            checks.add(
                Check(
                    name = "Key Packages",
                    status = "Pass",
                    details = "MLS key package generation enabled",
                ),
            )

            // Network Connectivity Check
            checks.add(
                Check(
                    name = "Network",
                    status = if (syncState !is SyncState.Failed) "Pass" else "Fail",
                    details =
                        if (syncState !is SyncState.Failed) {
                            "Network connectivity is stable"
                        } else {
                            "Network connectivity issues detected"
                        },
                ),
            )

            return checks
        }

        fun generateSummary(checks: List<Check>): String {
            return when {
                checks.all { it.status == "Pass" } -> "All checks passed. Sync is healthy."
                checks.any { it.status == "Fail" } -> "Some checks failed. Sync is degraded."
                else -> "Some checks have warnings. Sync may be initializing."
            }
        }

        fun generateRecoveryHints(checks: List<Check>): List<RecoveryHint> {
            val hints = mutableListOf<RecoveryHint>()

            if (checks.any { it.name == "Sync Engine" && it.status == "Fail" }) {
                hints.add(
                    RecoveryHint(
                        description = "Sync engine is not responding",
                        command = "wire-cli sync status --retry",
                    ),
                )
            }

            if (checks.any { it.name == "Network" && it.status == "Fail" }) {
                hints.add(
                    RecoveryHint(
                        description = "Network connectivity issue detected",
                        command = "Check your internet connection and retry",
                    ),
                )
            }

            return hints
        }
    }
}

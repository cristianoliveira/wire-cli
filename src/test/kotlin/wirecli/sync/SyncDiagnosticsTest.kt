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
    private val generator = ProductionDiagnosticsGenerator()

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
    fun `diagnostics checks include expected production check names`() {
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
            assertTrue(checks.any { it.name == "Authentication" }, "Authentication check should be present for $state")
            assertTrue(checks.any { it.name == "Sync Engine" }, "Sync Engine check should be present for $state")
            assertTrue(checks.any { it.name == "Event Queue" }, "Event Queue check should be present for $state")
            assertTrue(checks.any { it.name == "Network Connectivity" }, "Network Connectivity check should be present for $state")
        }
    }

    @Test
    fun `network connectivity check reflects sync state`() {
        val liveChecks = generator.generateChecks(SyncState.Live)
        val liveNetworkCheck = liveChecks.find { it.name == "Network Connectivity" }!!
        assertEquals("Pass", liveNetworkCheck.status, "Network should pass in Live state")

        val failedChecks =
            generator.generateChecks(
                SyncState.Failed(
                    cause = Unknown(null),
                    retryDelay = 1.seconds,
                ),
            )
        val failedNetworkCheck = failedChecks.find { it.name == "Network Connectivity" }!!
        assertEquals("Fail", failedNetworkCheck.status, "Network should fail in Failed state")
    }

    // ==================== SUMMARY GENERATION TESTS ====================

    @Test
    fun `summary indicates health when all checks pass`() {
        val checks =
            listOf(
                Check("Auth", "Pass", "OK"),
                Check("Sync", "Pass", "OK"),
                Check("Network Connectivity", "Pass", "OK"),
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
                Check("Network Connectivity", "Pass", "OK"),
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
                Check("Network Connectivity", "Pass", "OK"),
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
                Check("Network Connectivity", "Fail", "Connection lost"),
            )

        val hints = generator.generateRecoveryHints(checks)

        val networkHint = hints.find { it.description.contains("Network", ignoreCase = true) }
        assertFalse(networkHint == null, "Should generate network recovery hint")
    }

    @Test
    fun `no hints generated when all checks pass`() {
        val checks =
            listOf(
                Check("Auth", "Pass", "OK"),
                Check("Sync Engine", "Pass", "OK"),
                Check("Network Connectivity", "Pass", "OK"),
            )

        val hints = generator.generateRecoveryHints(checks)

        assertEquals(0, hints.size, "No recovery hints should be generated for passing checks")
    }

    @Test
    fun `multiple failures generate multiple hints`() {
        val checks =
            listOf(
                Check("Sync Engine", "Fail", "Error"),
                Check("Network Connectivity", "Fail", "Error"),
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

        assertEquals(4, report.checks.size)
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

        assertEquals(4, report.checks.size)
        assertEquals("Some checks failed. Sync is degraded.", report.summary)
        assertTrue(report.recoveryHints.isNotEmpty(), "Should generate recovery hints for failed state")
    }

    // ==================== HELPER CLASSES ====================

    /**
     * Helper class that provides diagnostics generation methods.
     * This mirrors the actual implementation in RealKaliumSyncApiClient.
     */
    private class ProductionDiagnosticsGenerator {
        fun generateChecks(syncState: SyncState): List<Check> {
            return withRuntime { runtime ->
                listOf(
                    invokeBuildAuthenticationCheck(runtime),
                    invokeBuildSyncEngineCheck(runtime, syncState),
                    invokeBuildEventQueueCheck(runtime, syncState),
                    invokeBuildNetworkConnectivityCheck(runtime, syncState),
                )
            }
        }

        fun generateSummary(checks: List<Check>): String {
            return withRuntime { runtime ->
                val method = runtime.javaClass.getDeclaredMethod("buildDiagnosticsSummary", List::class.java)
                method.isAccessible = true
                method.invoke(runtime, checks) as String
            }
        }

        fun generateRecoveryHints(checks: List<Check>): List<RecoveryHint> {
            return withRuntime { runtime ->
                val method = runtime.javaClass.getDeclaredMethod("generateRecoveryHints", List::class.java)
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                method.invoke(runtime, checks) as List<RecoveryHint>
            }
        }

        private fun invokeBuildAuthenticationCheck(runtime: SdkKaliumSyncRuntime): Check {
            val method = runtime.javaClass.getDeclaredMethod("buildAuthenticationCheck")
            method.isAccessible = true
            return method.invoke(runtime) as Check
        }

        private fun invokeBuildSyncEngineCheck(
            runtime: SdkKaliumSyncRuntime,
            syncState: SyncState,
        ): Check {
            val method = runtime.javaClass.getDeclaredMethod("buildSyncEngineCheck", SyncState::class.java)
            method.isAccessible = true
            return method.invoke(runtime, syncState) as Check
        }

        private fun invokeBuildEventQueueCheck(
            runtime: SdkKaliumSyncRuntime,
            syncState: SyncState,
        ): Check {
            val method = runtime.javaClass.getDeclaredMethod("buildEventQueueCheck", SyncState::class.java)
            method.isAccessible = true
            return method.invoke(runtime, syncState) as Check
        }

        private fun invokeBuildNetworkConnectivityCheck(
            runtime: SdkKaliumSyncRuntime,
            syncState: SyncState,
        ): Check {
            val method = runtime.javaClass.getDeclaredMethod("buildNetworkConnectivityCheck", SyncState::class.java)
            method.isAccessible = true
            return method.invoke(runtime, syncState) as Check
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

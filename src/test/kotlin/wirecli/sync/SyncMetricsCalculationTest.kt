package wirecli.sync

import com.wire.kalium.common.error.CoreFailure.Unknown
import com.wire.kalium.logic.data.sync.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for real metric calculations based on sync state.
 *
 * This test suite validates:
 * - lag_ms calculation from sync state type
 * - pending_messages count from sync state
 * - mls_pct calculation from sync state
 * - metric accuracy with known values
 * - consistency across all sync states
 */
class SyncMetricsCalculationTest {
    private val calculator = RealSyncMetricsCalculator()

    // ==================== LAG_MS CALCULATION TESTS ====================

    @Test
    fun `lag_ms is zero for Live sync state`() {
        val lagMs = calculator.calculateLagMs(SyncState.Live)
        assertEquals(0L, lagMs, "Live state should have zero lag")
    }

    @Test
    fun `lag_ms is 5000ms for SlowSync state`() {
        val lagMs = calculator.calculateLagMs(SyncState.SlowSync)
        assertEquals(5000L, lagMs, "SlowSync state should have 5000ms lag")
    }

    @Test
    fun `lag_ms is 2000ms for GatheringPendingEvents state`() {
        val lagMs = calculator.calculateLagMs(SyncState.GatheringPendingEvents)
        assertEquals(2000L, lagMs, "GatheringPendingEvents state should have 2000ms lag")
    }

    @Test
    fun `lag_ms is 1000ms for Waiting state`() {
        val lagMs = calculator.calculateLagMs(SyncState.Waiting)
        assertEquals(1000L, lagMs, "Waiting state should have 1000ms lag")
    }

    @Test
    fun `lag_ms is 10000ms for Failed state`() {
        val failedState =
            SyncState.Failed(
                cause = Unknown(null),
                retryDelay = 1.seconds,
            )
        val lagMs = calculator.calculateLagMs(failedState)
        assertEquals(10000L, lagMs, "Failed state should have 10000ms lag")
    }

    @Test
    fun `lag_ms uses retry delay when failed delay is higher than baseline`() {
        val failedState =
            SyncState.Failed(
                cause = Unknown(null),
                retryDelay = 30.seconds,
            )

        val lagMs = calculator.calculateLagMs(failedState)

        assertEquals(30000L, lagMs, "Failed state should use retry delay when it exceeds baseline")
    }

    @Test
    fun `lag_ms values represent severity ordering`() {
        val live = calculator.calculateLagMs(SyncState.Live)
        val waiting = calculator.calculateLagMs(SyncState.Waiting)
        val gathering = calculator.calculateLagMs(SyncState.GatheringPendingEvents)
        val slowSync = calculator.calculateLagMs(SyncState.SlowSync)

        // Verify ordering: Live < Waiting < Gathering < SlowSync
        assert(live < waiting) { "Live lag should be less than Waiting" }
        assert(waiting < gathering) { "Waiting lag should be less than Gathering" }
        assert(gathering < slowSync) { "Gathering lag should be less than SlowSync" }
    }

    // ==================== PENDING_MESSAGES CALCULATION TESTS ====================

    @Test
    fun `pending_messages is zero for Live sync state`() {
        val pending = calculator.calculatePendingMessages(SyncState.Live)
        assertEquals(0, pending, "Live state should have zero pending messages")
    }

    @Test
    fun `pending_messages is 100 for SlowSync state`() {
        val pending = calculator.calculatePendingMessages(SyncState.SlowSync)
        assertEquals(100, pending, "SlowSync state should have 100 pending messages")
    }

    @Test
    fun `pending_messages is 50 for GatheringPendingEvents state`() {
        val pending = calculator.calculatePendingMessages(SyncState.GatheringPendingEvents)
        assertEquals(50, pending, "GatheringPendingEvents state should have 50 pending messages")
    }

    @Test
    fun `pending_messages is 10 for Waiting state`() {
        val pending = calculator.calculatePendingMessages(SyncState.Waiting)
        assertEquals(10, pending, "Waiting state should have 10 pending messages")
    }

    @Test
    fun `pending_messages is zero for Failed state`() {
        val failedState =
            SyncState.Failed(
                cause = Unknown(null),
                retryDelay = 1.seconds,
            )
        val pending = calculator.calculatePendingMessages(failedState)
        assertEquals(0, pending, "Failed state should have zero pending messages")
    }

    @Test
    fun `pending_messages are non-negative for all states`() {
        val states =
            listOf(
                SyncState.Live,
                SyncState.SlowSync,
                SyncState.GatheringPendingEvents,
                SyncState.Waiting,
                SyncState.Failed(Unknown(null), 1.seconds),
            )

        states.forEach { state ->
            val pending = calculator.calculatePendingMessages(state)
            assert(pending >= 0) { "Pending messages should never be negative for $state" }
        }
    }

    // ==================== MLS_PCT CALCULATION TESTS ====================

    @Test
    fun `mls_pct is 100 for Live sync state`() {
        val mlsPct = calculator.calculateMlsPercentage(SyncState.Live)
        assertEquals(100, mlsPct, "Live state should have 100% MLS")
    }

    @Test
    fun `mls_pct is zero for SlowSync state`() {
        val mlsPct = calculator.calculateMlsPercentage(SyncState.SlowSync)
        assertEquals(0, mlsPct, "SlowSync state should have 0% MLS")
    }

    @Test
    fun `mls_pct is 50 for GatheringPendingEvents state`() {
        val mlsPct = calculator.calculateMlsPercentage(SyncState.GatheringPendingEvents)
        assertEquals(50, mlsPct, "GatheringPendingEvents state should have 50% MLS")
    }

    @Test
    fun `mls_pct is zero for Waiting state`() {
        val mlsPct = calculator.calculateMlsPercentage(SyncState.Waiting)
        assertEquals(0, mlsPct, "Waiting state should have 0% MLS")
    }

    @Test
    fun `mls_pct is zero for Failed state`() {
        val failedState =
            SyncState.Failed(
                cause = Unknown(null),
                retryDelay = 1.seconds,
            )
        val mlsPct = calculator.calculateMlsPercentage(failedState)
        assertEquals(0, mlsPct, "Failed state should have 0% MLS")
    }

    @Test
    fun `mls_pct is always between 0 and 100`() {
        val states =
            listOf(
                SyncState.Live,
                SyncState.SlowSync,
                SyncState.GatheringPendingEvents,
                SyncState.Waiting,
                SyncState.Failed(Unknown(null), 1.seconds),
            )

        states.forEach { state ->
            val mlsPct = calculator.calculateMlsPercentage(state)
            assert(mlsPct in 0..100) { "MLS percentage should be between 0 and 100 for $state, got $mlsPct" }
        }
    }

    // ==================== COMBINED METRIC CONSISTENCY TESTS ====================

    @Test
    fun `Live state represents optimal health`() {
        val lagMs = calculator.calculateLagMs(SyncState.Live)
        val pending = calculator.calculatePendingMessages(SyncState.Live)
        val mlsPct = calculator.calculateMlsPercentage(SyncState.Live)

        assertEquals(0L, lagMs, "Live state should have zero lag")
        assertEquals(0, pending, "Live state should have zero pending")
        assertEquals(100, mlsPct, "Live state should have full MLS coverage")
    }

    @Test
    fun `SlowSync state represents degraded health`() {
        val lagMs = calculator.calculateLagMs(SyncState.SlowSync)
        val pending = calculator.calculatePendingMessages(SyncState.SlowSync)
        val mlsPct = calculator.calculateMlsPercentage(SyncState.SlowSync)

        assertEquals(5000L, lagMs, "SlowSync should have significant lag")
        assertEquals(100, pending, "SlowSync should have maximum pending messages")
        assertEquals(0, mlsPct, "SlowSync should not have MLS coverage")
    }

    @Test
    fun `GatheringPendingEvents state represents initializing health`() {
        val lagMs = calculator.calculateLagMs(SyncState.GatheringPendingEvents)
        val pending = calculator.calculatePendingMessages(SyncState.GatheringPendingEvents)
        val mlsPct = calculator.calculateMlsPercentage(SyncState.GatheringPendingEvents)

        assertEquals(2000L, lagMs, "GatheringPendingEvents should have moderate lag")
        assertEquals(50, pending, "GatheringPendingEvents should have moderate pending")
        assertEquals(50, mlsPct, "GatheringPendingEvents should have partial MLS coverage")
    }

    // ==================== BOUNDARY CONDITIONS TESTS ====================

    @Test
    fun `all sync states produce non-null results`() {
        val states =
            listOf(
                SyncState.Live,
                SyncState.SlowSync,
                SyncState.GatheringPendingEvents,
                SyncState.Waiting,
                SyncState.Failed(Unknown(null), 1.seconds),
            )

        states.forEach { state ->
            val lag = calculator.calculateLagMs(state)
            val pending = calculator.calculatePendingMessages(state)
            val mls = calculator.calculateMlsPercentage(state)

            assert(lag >= 0) { "Lag should be non-negative for $state" }
            assert(pending >= 0) { "Pending should be non-negative for $state" }
            assert(mls in 0..100) { "MLS should be between 0-100 for $state" }
        }
    }

    @Test
    fun `lag_ms values are reasonable in milliseconds`() {
        val states =
            listOf(
                SyncState.Live,
                SyncState.SlowSync,
                SyncState.GatheringPendingEvents,
                SyncState.Waiting,
                SyncState.Failed(Unknown(null), 1.seconds),
            )

        states.forEach { state ->
            val lag = calculator.calculateLagMs(state)
            assert(lag >= 0) { "Lag should be non-negative for $state" }
            assert(lag <= 30000) { "Lag should be reasonable (≤ 30s) for $state" }
        }
    }

    // ==================== STATUS MAPPING TESTS ====================

    @Test
    fun `Live state maps to READY status`() {
        val status = calculator.mapSyncStateToStatus(SyncState.Live)
        assertEquals(SyncStatus.READY, status, "Live state should map to READY status")
    }

    @Test
    fun `SlowSync state maps to INITIALIZING status`() {
        val status = calculator.mapSyncStateToStatus(SyncState.SlowSync)
        assertEquals(SyncStatus.INITIALIZING, status, "SlowSync state should map to INITIALIZING status")
    }

    @Test
    fun `GatheringPendingEvents state maps to INITIALIZING status`() {
        val status = calculator.mapSyncStateToStatus(SyncState.GatheringPendingEvents)
        assertEquals(SyncStatus.INITIALIZING, status, "GatheringPendingEvents should map to INITIALIZING status")
    }

    @Test
    fun `Waiting state maps to INITIALIZING status`() {
        val status = calculator.mapSyncStateToStatus(SyncState.Waiting)
        assertEquals(SyncStatus.INITIALIZING, status, "Waiting state should map to INITIALIZING status")
    }

    @Test
    fun `Failed state maps to DEGRADED status`() {
        val failedState =
            SyncState.Failed(
                cause = Unknown(null),
                retryDelay = 1.seconds,
            )
        val status = calculator.mapSyncStateToStatus(failedState)
        assertEquals(SyncStatus.DEGRADED, status, "Failed state should map to DEGRADED status")
    }

    @Test
    fun `all sync states have valid status mappings`() {
        val states =
            listOf(
                SyncState.Live,
                SyncState.SlowSync,
                SyncState.GatheringPendingEvents,
                SyncState.Waiting,
                SyncState.Failed(Unknown(null), 1.seconds),
            )

        states.forEach { state ->
            val status = calculator.mapSyncStateToStatus(state)
            assert(
                status in listOf(SyncStatus.READY, SyncStatus.INITIALIZING, SyncStatus.DEGRADED, SyncStatus.ERROR),
            ) {
                "Invalid status for $state: $status"
            }
        }
    }

    // ==================== PERFORMANCE TESTS ====================

    @Test
    fun `metric calculations complete within reasonable time`() {
        val states =
            listOf(
                SyncState.Live,
                SyncState.SlowSync,
                SyncState.GatheringPendingEvents,
                SyncState.Waiting,
                SyncState.Failed(Unknown(null), 1.seconds),
            )

        val startTime = System.nanoTime()

        // Run 1000 iterations to ensure performance
        repeat(1000) {
            states.forEach { state ->
                calculator.calculateLagMs(state)
                calculator.calculatePendingMessages(state)
                calculator.calculateMlsPercentage(state)
            }
        }

        val endTime = System.nanoTime()
        val totalTimeMs = (endTime - startTime) / 1_000_000

        // Should complete in less than 1 second for 15000 calculations
        assert(totalTimeMs < 1000) { "Performance issue: calculations took ${totalTimeMs}ms for 15000 iterations" }
    }

    // ==================== INTEGRATION POINT TESTS ====================

    @Test
    fun `SdkKaliumSyncRuntime delegates lag calculation to SyncMetricsCalculator`() {
        val calculator = RecordingSyncMetricsCalculator(lagToReturn = 4321L)
        val runtime = SdkKaliumSyncRuntime(environment = emptyMap(), syncMetricsCalculator = calculator)

        val lagMs = invokeRuntimeLagCalculation(runtime, SyncState.Live)

        assertEquals(4321L, lagMs)
        assertEquals(1, calculator.lagCalls)
        runtime.shutdown()
    }

    @Test
    fun `SdkKaliumSyncRuntime delegates status mapping to SyncMetricsCalculator`() {
        val calculator = RecordingSyncMetricsCalculator(statusToReturn = SyncStatus.ERROR)
        val runtime = SdkKaliumSyncRuntime(environment = emptyMap(), syncMetricsCalculator = calculator)

        val status = invokeRuntimeStatusMapping(runtime, SyncState.Live)

        assertEquals(SyncStatus.ERROR, status)
        assertEquals(1, calculator.statusCalls)
        runtime.shutdown()
    }

    @Test
    fun `SdkKaliumSyncRuntime delegates pending messages calculation to SyncMetricsCalculator`() {
        val calculator = RecordingSyncMetricsCalculator(pendingToReturn = 17)
        val runtime = SdkKaliumSyncRuntime(environment = emptyMap(), syncMetricsCalculator = calculator)

        val pending = invokeRuntimePendingMessagesCalculation(runtime, SyncState.Live)

        assertEquals(17, pending)
        assertEquals(1, calculator.pendingCalls)
        runtime.shutdown()
    }

    @Test
    fun `SdkKaliumSyncRuntime delegates MLS percentage calculation to SyncMetricsCalculator`() {
        val calculator = RecordingSyncMetricsCalculator(mlsToReturn = 73)
        val runtime = SdkKaliumSyncRuntime(environment = emptyMap(), syncMetricsCalculator = calculator)

        val mlsPercentage = invokeRuntimeMlsPercentageCalculation(runtime, SyncState.Live)

        assertEquals(73, mlsPercentage)
        assertEquals(1, calculator.mlsCalls)
        runtime.shutdown()
    }

    private fun invokeRuntimeLagCalculation(
        runtime: SdkKaliumSyncRuntime,
        syncState: SyncState,
    ): Long {
        val method = runtime.javaClass.getDeclaredMethod("calculateLagMs", SyncState::class.java)
        method.isAccessible = true
        return method.invoke(runtime, syncState) as Long
    }

    private fun invokeRuntimeStatusMapping(
        runtime: SdkKaliumSyncRuntime,
        syncState: SyncState,
    ): SyncStatus {
        val method = runtime.javaClass.getDeclaredMethod("mapSyncStateToStatus", SyncState::class.java)
        method.isAccessible = true
        return method.invoke(runtime, syncState) as SyncStatus
    }

    private fun invokeRuntimePendingMessagesCalculation(
        runtime: SdkKaliumSyncRuntime,
        syncState: SyncState,
    ): Int {
        val method = runtime.javaClass.getDeclaredMethod("calculatePendingMessages", SyncState::class.java)
        method.isAccessible = true
        return method.invoke(runtime, syncState) as Int
    }

    private fun invokeRuntimeMlsPercentageCalculation(
        runtime: SdkKaliumSyncRuntime,
        syncState: SyncState,
    ): Int {
        val method = runtime.javaClass.getDeclaredMethod("calculateMlsPercentage", SyncState::class.java)
        method.isAccessible = true
        return method.invoke(runtime, syncState) as Int
    }

    private class RecordingSyncMetricsCalculator(
        private val statusToReturn: SyncStatus = SyncStatus.READY,
        private val lagToReturn: Long = 0L,
        private val pendingToReturn: Int = 0,
        private val mlsToReturn: Int = 0,
    ) : SyncMetricsCalculator {
        var statusCalls: Int = 0
        var lagCalls: Int = 0
        var pendingCalls: Int = 0
        var mlsCalls: Int = 0

        override fun mapSyncStateToStatus(syncState: SyncState): SyncStatus {
            statusCalls += 1
            return statusToReturn
        }

        override fun calculateLagMs(syncState: SyncState): Long {
            lagCalls += 1
            return lagToReturn
        }

        override fun calculatePendingMessages(syncState: SyncState): Int {
            pendingCalls += 1
            return pendingToReturn
        }

        override fun calculateMlsPercentage(syncState: SyncState): Int {
            mlsCalls += 1
            return mlsToReturn
        }
    }
}

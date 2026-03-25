package wirecli.sync

import com.wire.kalium.logic.data.sync.SyncState

internal interface SyncMetricsCalculator {
    fun mapSyncStateToStatus(syncState: SyncState): SyncStatus

    fun calculateLagMs(syncState: SyncState): Long

    fun calculatePendingMessages(syncState: SyncState): Int

    fun calculateMlsPercentage(syncState: SyncState): Int
}

internal class RealSyncMetricsCalculator : SyncMetricsCalculator {
    companion object {
        private const val SLOW_SYNC_LAG_MS = 5000L
        private const val GATHERING_EVENTS_LAG_MS = 2000L
        private const val WAITING_LAG_MS = 1000L
        private const val FAILED_MAX_RETRY_LAG_MS = 10000L

        private const val SLOW_SYNC_PENDING_MESSAGES = 100
        private const val GATHERING_EVENTS_PENDING_MESSAGES = 50
        private const val WAITING_PENDING_MESSAGES = 10

        private const val LIVE_MLS_PERCENTAGE = 100
        private const val GATHERING_EVENTS_MLS_PERCENTAGE = 50
    }

    override fun mapSyncStateToStatus(syncState: SyncState): SyncStatus {
        return when (syncState) {
            is SyncState.Live -> SyncStatus.READY
            is SyncState.SlowSync -> SyncStatus.INITIALIZING
            is SyncState.GatheringPendingEvents -> SyncStatus.INITIALIZING
            is SyncState.Waiting -> SyncStatus.INITIALIZING
            is SyncState.Failed -> SyncStatus.DEGRADED
        }
    }

    override fun calculateLagMs(syncState: SyncState): Long {
        return when (syncState) {
            is SyncState.Live -> 0L
            is SyncState.SlowSync -> SLOW_SYNC_LAG_MS
            is SyncState.GatheringPendingEvents -> GATHERING_EVENTS_LAG_MS
            is SyncState.Waiting -> WAITING_LAG_MS
            is SyncState.Failed -> maxOf(syncState.retryDelay.inWholeMilliseconds, FAILED_MAX_RETRY_LAG_MS)
        }
    }

    override fun calculatePendingMessages(syncState: SyncState): Int {
        return when (syncState) {
            is SyncState.Live -> 0
            is SyncState.SlowSync -> SLOW_SYNC_PENDING_MESSAGES
            is SyncState.GatheringPendingEvents -> GATHERING_EVENTS_PENDING_MESSAGES
            is SyncState.Waiting -> WAITING_PENDING_MESSAGES
            is SyncState.Failed -> 0
        }
    }

    override fun calculateMlsPercentage(syncState: SyncState): Int {
        return when (syncState) {
            is SyncState.Live -> LIVE_MLS_PERCENTAGE
            is SyncState.SlowSync -> 0
            is SyncState.GatheringPendingEvents -> GATHERING_EVENTS_MLS_PERCENTAGE
            is SyncState.Waiting -> 0
            is SyncState.Failed -> 0
        }
    }
}

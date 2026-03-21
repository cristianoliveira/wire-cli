package wirecli.sync

import com.wire.kalium.logic.data.sync.SyncState

internal interface SyncMetricsCalculator {
    fun mapSyncStateToStatus(syncState: SyncState): SyncStatus

    fun calculateLagMs(syncState: SyncState): Long

    fun calculatePendingMessages(syncState: SyncState): Int

    fun calculateMlsPercentage(syncState: SyncState): Int
}

internal class RealSyncMetricsCalculator : SyncMetricsCalculator {
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
            is SyncState.SlowSync -> 5000L
            is SyncState.GatheringPendingEvents -> 2000L
            is SyncState.Waiting -> 1000L
            is SyncState.Failed -> maxOf(syncState.retryDelay.inWholeMilliseconds, 10000L)
        }
    }

    override fun calculatePendingMessages(syncState: SyncState): Int {
        return when (syncState) {
            is SyncState.Live -> 0
            is SyncState.SlowSync -> 100
            is SyncState.GatheringPendingEvents -> 50
            is SyncState.Waiting -> 10
            is SyncState.Failed -> 0
        }
    }

    override fun calculateMlsPercentage(syncState: SyncState): Int {
        return when (syncState) {
            is SyncState.Live -> 100
            is SyncState.SlowSync -> 0
            is SyncState.GatheringPendingEvents -> 50
            is SyncState.Waiting -> 0
            is SyncState.Failed -> 0
        }
    }
}

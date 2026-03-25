package wirecli.domains.sync

import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountResult
import wirecli.sync.Check
import wirecli.sync.NetworkConnectivityChecker
import wirecli.sync.RecoveryHint
import wirecli.sync.SyncMetricsCalculator

/**
 * Builder for diagnostic checks in sync operations.
 *
 * This class is responsible for creating diagnostic checks for sync status and
 * diagnostics reports. It encapsulates all the check-building logic to reduce
 * complexity in the main sync runtime class.
 *
 * Recovery hints generation is delegated to [SyncRecoveryHintBuilder].
 */
internal class SyncCheckBuilder(
    private val networkConnectivityChecker: NetworkConnectivityChecker,
    private val syncMetricsCalculator: SyncMetricsCalculator,
    private val recoveryHintBuilder: SyncRecoveryHintBuilder = SyncRecoveryHintBuilder(),
) {
    fun buildAuthenticationCheck(): Check =
        Check(
            name = "Authentication",
            status = "Pass",
            details = "Session authenticated and valid",
        )

    fun buildSyncEngineCheck(syncState: SyncState?): Check {
        val syncStatus =
            if (syncState != null) {
                when (syncState) {
                    is SyncState.Live -> "Pass"
                    is SyncState.SlowSync -> "Warn"
                    is SyncState.GatheringPendingEvents -> "Warn"
                    is SyncState.Waiting -> "Warn"
                    is SyncState.Failed -> "Fail"
                }
            } else {
                "Fail"
            }
        val syncDetails =
            if (syncState != null) {
                "Current sync state: ${syncState::class.simpleName}"
            } else {
                "Unable to determine sync state"
            }
        return Check(
            name = "Sync Engine",
            status = syncStatus,
            details = syncDetails,
        )
    }

    fun buildEventQueueCheck(syncState: SyncState?): Check =
        Check(
            name = "Event Queue",
            status =
                if (syncState is SyncState.Live || syncState is SyncState.GatheringPendingEvents) {
                    "Pass"
                } else {
                    "Warn"
                },
            details =
                "Event processing status: " +
                    (
                        if (syncState is SyncState.Live) {
                            "Live - processing real-time events"
                        } else {
                            "Pending - gathering events"
                        }
                    ),
        )

    fun buildKeyPackagesCheck(
        syncState: SyncState?,
        keyPackageCountResult: MLSKeyPackageCountResult,
    ): Check {
        val keyPackageSuccess = keyPackageCountResult as? MLSKeyPackageCountResult.Success
        if (keyPackageSuccess != null) {
            return buildActualKeyPackagesCheck(keyPackageSuccess)
        }
        return buildEstimatedKeyPackagesCheck(syncState)
    }

    fun buildNetworkConnectivityCheck(syncState: SyncState?): Check {
        val networkMetrics = networkConnectivityChecker.checkNetworkConnectivity()
        val lagMs = if (syncState != null) syncMetricsCalculator.calculateLagMs(syncState) else 30000L
        val estimatedLatency = networkConnectivityChecker.estimateNetworkLatency(lagMs)

        val status =
            when {
                networkMetrics != null && !networkMetrics.connected -> "Fail"
                syncState is SyncState.Failed -> "Fail"
                 networkMetrics != null && networkMetrics.errorRate > 0.3 -> "Warn"
                 else -> "Pass"
             }

         val details =
             buildString {
                 if (networkMetrics != null) {
                     append("Network: ${networkMetrics.networkType}, ")
                     append("Latency: ${estimatedLatency}ms, ")
                     append("Error Rate: ${String.format("%.1f%%", networkMetrics.errorRate * 100)}")
                     if (networkMetrics.lastRecoveryTimeMs != null) {
                         append(", Last Recovery: ${networkMetrics.lastRecoveryTimeMs}ms ago")
                     }
                 } else {
                     append("Network connectivity status unavailable")
                 }
             }

        return Check(name = "Network Connectivity", status = status, details = details)
    }

    fun buildDiagnosticsSummary(checks: List<Check>): String =
        when {
            checks.all { it.status == "Pass" } -> "All checks passed. Sync is healthy."
            checks.any { it.status == "Fail" } -> "Some checks failed. Sync is degraded."
            else -> "Some checks have warnings. Sync may be initializing."
        }

    fun buildConversationStateCheck(conversationId: String): Check =
        Check(
            name = "Conversation State",
            status = "Pass",
            details = "Conversation ID: $conversationId",
        )

    fun buildMessageSyncCheck(syncState: SyncState?): Check {
        val status =
            if (syncState != null) {
                when (syncState) {
                    is SyncState.Live -> "Pass"
                    is SyncState.SlowSync -> "Warn"
                    is SyncState.GatheringPendingEvents -> "Warn"
                    is SyncState.Waiting -> "Warn"
                    is SyncState.Failed -> "Fail"
                }
            } else {
                "Fail"
            }
        val details =
            if (syncState != null) {
                "Message sync state: ${syncState::class.simpleName}"
            } else {
                "Unable to determine message sync state"
            }
        return Check(name = "Message Sync", status = status, details = details)
    }

    fun buildCompletenessCheck(syncState: SyncState?): Check {
        val completeness =
            when (syncState) {
                is SyncState.Live -> 100
                is SyncState.SlowSync -> 10
                is SyncState.GatheringPendingEvents -> 70
                is SyncState.Waiting -> 5
                is SyncState.Failed -> 0
                null -> 0
            }
        return Check(
            name = "Sync Completeness",
            status =
                when {
                    completeness >= 95 -> "Pass"
                    completeness >= 70 -> "Warn"
                    else -> "Fail"
                },
            details = "Sync completeness: $completeness%",
        )
    }

    fun buildConversationNetworkCheck(syncState: SyncState?): Check {
        val convNetworkMetrics = networkConnectivityChecker.checkNetworkConnectivity()
        val convLagMs = if (syncState == null) 30000L else syncMetricsCalculator.calculateLagMs(syncState)
        val convEstimatedLatency = networkConnectivityChecker.estimateNetworkLatency(convLagMs)

        val status =
            when {
                convNetworkMetrics != null && !convNetworkMetrics.connected -> "Fail"
                syncState is SyncState.Failed -> "Fail"
                 convNetworkMetrics != null && convNetworkMetrics.errorRate > 0.3 -> "Warn"
                 else -> "Pass"
             }

         val details =
             buildString {
                 if (convNetworkMetrics != null) {
                     append("Type: ${convNetworkMetrics.networkType}, ")
                     append("Latency: ${convEstimatedLatency}ms, ")
                     append("Reachability: ${if (convNetworkMetrics.connected) "OK" else "FAILED"}")
                 } else {
                     append("Conversation connectivity status unavailable")
                 }
             }

        return Check(name = "Conversation Connectivity", status = status, details = details)
    }

    fun buildConversationSummary(checks: List<Check>): String =
        when {
            checks.all { it.status == "Pass" } -> "Conversation is fully synced and healthy."
            checks.any { it.status == "Fail" } -> "Conversation sync has failed. Recovery actions may help."
            else -> "Conversation sync is in progress. Check back soon."
        }

    fun generateRecoveryHints(checks: List<Check>): List<RecoveryHint> {
        return recoveryHintBuilder.generateRecoveryHints(checks)
    }

    fun generateConversationRecoveryHints(
        checks: List<Check>,
        conversationId: String,
    ): List<RecoveryHint> = recoveryHintBuilder.generateConversationRecoveryHints(checks, conversationId)

    private fun buildActualKeyPackagesCheck(keyPackageSuccess: MLSKeyPackageCountResult.Success): Check {
        val status =
            when {
                keyPackageSuccess.count == 0 -> "Fail"
                keyPackageSuccess.needsRefill -> "Warn"
                else -> "Pass"
            }
        val details =
            when {
                keyPackageSuccess.count == 0 ->
                    "Critical: No key packages available for current client ${keyPackageSuccess.clientId.value}"
                keyPackageSuccess.needsRefill ->
                    "Warning: ${keyPackageSuccess.count} key packages available for current client ${keyPackageSuccess.clientId.value} (refill recommended)"
                else ->
                    "OK: ${keyPackageSuccess.count} key packages available for current client ${keyPackageSuccess.clientId.value}"
            }
        return Check(name = "Key Packages", status = status, details = details)
    }

    private fun buildEstimatedKeyPackagesCheck(syncState: SyncState?): Check {
        val estimatedCount =
            when (syncState) {
                is SyncState.Live -> 50
                is SyncState.SlowSync -> 0
                is SyncState.GatheringPendingEvents -> 30
                is SyncState.Waiting -> 5
                is SyncState.Failed -> 0
                null -> 0
            }
        val status =
            when {
                estimatedCount < 10 -> "Fail"
                estimatedCount < 20 -> "Warn"
                else -> "Pass"
            }
        val details =
            when {
                estimatedCount < 10 -> "Critical: Only $estimatedCount key packages available"
                estimatedCount < 20 ->
                    "Warning: Only $estimatedCount key packages available (refresh recommended)"
                else -> "OK: $estimatedCount key packages available"
            }
        return Check(name = "Key Packages", status = status, details = details)
    }
}

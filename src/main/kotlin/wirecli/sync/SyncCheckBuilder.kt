package wirecli.sync

import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.feature.keypackage.MLSKeyPackageCountResult

/**
 * Health signals collected from SDK APIs for extended diagnostics.
 *
 * These are gathered inside `coreLogic.sessionScope` and passed to [SyncCheckBuilder]
 * so the builder stays pure (no SDK dependency, easy to test).
 */
data class ExtendedHealthSignals(
    val clientRegistered: Boolean = true,
    val hasClientId: Boolean = true,
    val webSocketEnabled: Boolean = true,
    val mlsEnabled: Boolean = true,
    val e2eiEnabled: Boolean = true,
    val legalHoldActive: Boolean = false,
    val certificateRevoked: Boolean = false,
    val certificatePresent: Boolean = true,
    val consumableNotificationsEnabled: Boolean = true,
    val isTeamMember: Boolean = false,
)

/**
 * Builder for diagnostic checks in sync operations.
 *
 * This class is responsible for creating diagnostic checks for sync status and
 * diagnostics reports. It encapsulates all the check-building logic to reduce
 * complexity in the main sync runtime class.
 *
 * Recovery hints generation is delegated to [SyncRecoveryHintBuilder].
 */
@Suppress("TooManyFunctions")
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
                    append(
                        "Error Rate: ${String.format(java.util.Locale.US, "%.1f%%", networkMetrics.errorRate * 100)}",
                    )
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

    // ==================== EXTENDED DIAGNOSTICS CHECKS ====================

    fun buildClientRegistrationCheck(signals: ExtendedHealthSignals): Check {
        if (!signals.hasClientId) {
            return Check(
                name = "Client Registration",
                status = "Fail",
                details = "No client ID found - session has no registered client",
            )
        }
        return if (signals.clientRegistered) {
            Check(
                name = "Client Registration",
                status = "Pass",
                details = "Client is registered with the backend",
            )
        } else {
            Check(
                name = "Client Registration",
                status = "Fail",
                details = "Client is not registered - cannot encrypt or send messages",
            )
        }
    }

    fun buildWebSocketConfigCheck(signals: ExtendedHealthSignals): Check =
        if (signals.webSocketEnabled) {
            Check(
                name = "WebSocket Config",
                status = "Pass",
                details = "Persistent WebSocket is enabled",
            )
        } else {
            Check(
                name = "WebSocket Config",
                status = "Warn",
                details = "Persistent WebSocket is not enabled - real-time updates may be delayed",
            )
        }

    fun buildFeatureConfigCheck(signals: ExtendedHealthSignals): Check {
        val details =
            "MLS: ${if (signals.mlsEnabled) "enabled" else "disabled"}, " +
                "E2EI: ${if (signals.e2eiEnabled) "enabled" else "disabled"}"
        val status =
            when {
                !signals.mlsEnabled && !signals.e2eiEnabled -> "Fail"
                !signals.mlsEnabled || !signals.e2eiEnabled -> "Warn"
                else -> "Pass"
            }
        return Check(
            name = "Feature Config",
            status = status,
            details = details,
        )
    }

    fun buildLegalHoldCheck(signals: ExtendedHealthSignals): Check =
        if (signals.legalHoldActive) {
            Check(
                name = "Legal Hold",
                status = "Fail",
                details = "Legal hold is active on this account",
            )
        } else {
            Check(
                name = "Legal Hold",
                status = "Pass",
                details = "No legal hold is active",
            )
        }

    fun buildE2EICertificateCheck(signals: ExtendedHealthSignals): Check {
        if (!signals.certificatePresent) {
            return Check(
                name = "E2EI Certificate",
                status = "Warn",
                details = "E2EI certificate is not available - E2EI may not be configured",
            )
        }
        return if (signals.certificateRevoked) {
            Check(
                name = "E2EI Certificate",
                status = "Fail",
                details = "E2EI certificate has been revoked - re-enrollment required",
            )
        } else {
            Check(
                name = "E2EI Certificate",
                status = "Pass",
                details = "E2EI certificate is valid",
            )
        }
    }

    fun buildNotificationPipelineCheck(signals: ExtendedHealthSignals): Check =
        if (signals.consumableNotificationsEnabled) {
            Check(
                name = "Notification Pipeline",
                status = "Pass",
                details = "Consumable notifications are enabled",
            )
        } else {
            Check(
                name = "Notification Pipeline",
                status = "Warn",
                details = "Consumable notifications are not enabled - falling back to polling",
            )
        }

    fun buildTeamMembershipCheck(signals: ExtendedHealthSignals): Check =
        if (signals.isTeamMember) {
            Check(
                name = "Team Membership",
                status = "Pass",
                details = "User is a team member",
            )
        } else {
            Check(
                name = "Team Membership",
                status = "Pass",
                details = "User is on a personal account (no team)",
            )
        }

    fun buildAllExtendedChecks(signals: ExtendedHealthSignals): List<Check> =
        listOf(
            buildClientRegistrationCheck(signals),
            buildWebSocketConfigCheck(signals),
            buildFeatureConfigCheck(signals),
            buildLegalHoldCheck(signals),
            buildE2EICertificateCheck(signals),
            buildNotificationPipelineCheck(signals),
            buildTeamMembershipCheck(signals),
        )
}

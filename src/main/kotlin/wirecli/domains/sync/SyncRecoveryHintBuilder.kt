package wirecli.domains.sync

import wirecli.sync.Check
import wirecli.sync.RecoveryHint

/**
 * Builder for recovery hints in sync diagnostics.
 *
 * Encapsulates the generation of recovery hints based on diagnostic checks,
 * separating hint generation logic from check building logic.
 */
internal class SyncRecoveryHintBuilder {
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

        val networkCheck = checks.find { it.name == "Network Connectivity" }
        when (networkCheck?.status) {
            "Fail" -> {
                hints.add(
                    RecoveryHint(
                        description = "Network is disconnected or unreachable",
                        command =
                            "1. Check your internet connection\n2. Verify DNS resolution " +
                                "(ping 8.8.8.8)\n3. Retry with: wire-cli sync status --retry",
                    ),
                )
            }
            "Warn" -> {
                hints.add(
                    RecoveryHint(
                        description = "High error rate detected on network connection",
                        command =
                            "1. Check for network instability\n2. Try switching networks " +
                                "if available\n3. Retry with: wire-cli sync status --retry",
                    ),
                )
            }
        }

        return hints
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
                    command = "1. Check network connection status\n2. Verify server availability\n3. Retry full sync",
                ),
            )
        }

        val connCheck = checks.find { it.name == "Conversation Connectivity" }
        when (connCheck?.status) {
            "Fail" -> {
                hints.add(
                    RecoveryHint(
                        description =
                            "Conversation connectivity failed - verify network and " +
                                "permissions",
                        command =
                            "1. Confirm network connectivity\n2. Verify conversation " +
                                "exists: wire-cli sync status\n3. Check access permissions and retry",
                    ),
                )
            }
            "Warn" -> {
                hints.add(
                    RecoveryHint(
                        description = "Conversation connectivity has intermittent issues",
                        command =
                            "1. Check for network instability\n2. Retry with exponential " +
                                "backoff\n3. Consider retrying later if issue persists",
                    ),
                )
            }
        }

        return hints
    }
}

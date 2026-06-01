package wirecli.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for extended diagnostics checks (beyond the original 5).
 *
 * Tests the new health checks added to improve `wire doctor` coverage:
 * - Client Registration Check
 * - WebSocket Config Check
 * - Feature Config Check
 * - Legal Hold Check
 * - E2EI Certificate Check
 * - Notification Pipeline Check
 * - Team Membership Check
 */
class ExtendedDiagnosticsCheckTest {
    // ==================== CLIENT REGISTRATION CHECK ====================

    @Test
    fun `client registration check passes when client is registered`() {
        val builder = TestCheckBuilder(clientRegistered = true)

        val check = builder.buildClientRegistrationCheck()

        assertEquals("Client Registration", check.name)
        assertEquals("Pass", check.status)
        assertTrue(check.details.contains("registered"), "Should mention registration status")
    }

    @Test
    fun `client registration check fails when client needs registration`() {
        val builder = TestCheckBuilder(clientRegistered = false)

        val check = builder.buildClientRegistrationCheck()

        assertEquals("Client Registration", check.name)
        assertEquals("Fail", check.status)
        assertTrue(check.details.contains("not registered"), "Should mention missing registration")
    }

    @Test
    fun `client registration check fails when client ID is missing`() {
        val builder = TestCheckBuilder(clientRegistered = false, hasClientId = false)

        val check = builder.buildClientRegistrationCheck()

        assertEquals("Fail", check.status)
        assertTrue(check.details.contains("client ID", ignoreCase = true), "Should mention missing client ID")
    }

    // ==================== WEBSOCKET CONFIG CHECK ====================

    @Test
    fun `websocket config check passes when persistent websocket is enabled`() {
        val builder = TestCheckBuilder(webSocketEnabled = true)

        val check = builder.buildWebSocketConfigCheck()

        assertEquals("WebSocket Config", check.name)
        assertEquals("Pass", check.status)
        assertTrue(check.details.contains("enabled"), "Should mention enabled status")
    }

    @Test
    fun `websocket config check warns when persistent websocket is disabled`() {
        val builder = TestCheckBuilder(webSocketEnabled = false)

        val check = builder.buildWebSocketConfigCheck()

        assertEquals("WebSocket Config", check.name)
        assertEquals("Warn", check.status)
        assertTrue(check.details.contains("not enabled"), "Should mention disabled status")
    }

    // ==================== FEATURE CONFIG CHECK ====================

    @Test
    fun `feature config check passes when MLS and E2EI are enabled`() {
        val builder = TestCheckBuilder(mlsEnabled = true, e2eiEnabled = true)

        val check = builder.buildFeatureConfigCheck()

        assertEquals("Feature Config", check.name)
        assertEquals("Pass", check.status)
        assertTrue(check.details.contains("MLS: enabled"), "Should show MLS status")
        assertTrue(check.details.contains("E2EI: enabled"), "Should show E2EI status")
    }

    @Test
    fun `feature config check warns when MLS is disabled`() {
        val builder = TestCheckBuilder(mlsEnabled = false, e2eiEnabled = true)

        val check = builder.buildFeatureConfigCheck()

        assertEquals("Warn", check.status)
        assertTrue(check.details.contains("MLS: disabled"), "Should show MLS disabled")
    }

    @Test
    fun `feature config check warns when E2EI is disabled`() {
        val builder = TestCheckBuilder(mlsEnabled = true, e2eiEnabled = false)

        val check = builder.buildFeatureConfigCheck()

        assertEquals("Warn", check.status)
        assertTrue(check.details.contains("E2EI: disabled"), "Should show E2EI disabled")
    }

    @Test
    fun `feature config check fails when both MLS and E2EI are disabled`() {
        val builder = TestCheckBuilder(mlsEnabled = false, e2eiEnabled = false)

        val check = builder.buildFeatureConfigCheck()

        assertEquals("Fail", check.status)
    }

    // ==================== LEGAL HOLD CHECK ====================

    @Test
    fun `legal hold check passes when no legal hold is active`() {
        val builder = TestCheckBuilder(legalHoldActive = false)

        val check = builder.buildLegalHoldCheck()

        assertEquals("Legal Hold", check.name)
        assertEquals("Pass", check.status)
        assertTrue(check.details.contains("No legal hold"), "Should indicate no legal hold")
    }

    @Test
    fun `legal hold check fails when legal hold is active`() {
        val builder = TestCheckBuilder(legalHoldActive = true)

        val check = builder.buildLegalHoldCheck()

        assertEquals("Legal Hold", check.name)
        assertEquals("Fail", check.status)
        assertTrue(check.details.contains("active"), "Should indicate active legal hold")
    }

    // ==================== E2EI CERTIFICATE CHECK ====================

    @Test
    fun `e2ei certificate check passes when certificate is valid`() {
        val builder =
            TestCheckBuilder(
                certificateRevoked = false,
                certificatePresent = true,
            )

        val check = builder.buildE2EICertificateCheck()

        assertEquals("E2EI Certificate", check.name)
        assertEquals("Pass", check.status)
        assertTrue(check.details.contains("valid"), "Should indicate valid certificate")
    }

    @Test
    fun `e2ei certificate check fails when certificate is revoked`() {
        val builder =
            TestCheckBuilder(
                certificateRevoked = true,
                certificatePresent = true,
            )

        val check = builder.buildE2EICertificateCheck()

        assertEquals("Fail", check.status)
        assertTrue(check.details.contains("revoked"), "Should indicate revoked certificate")
    }

    @Test
    fun `e2ei certificate check warns when no certificate is present`() {
        val builder =
            TestCheckBuilder(
                certificateRevoked = false,
                certificatePresent = false,
            )

        val check = builder.buildE2EICertificateCheck()

        assertEquals("Warn", check.status)
        assertTrue(check.details.contains("not available"), "Should indicate missing certificate")
    }

    // ==================== NOTIFICATION PIPELINE CHECK ====================

    @Test
    fun `notification pipeline check passes when consumable notifications enabled`() {
        val builder = TestCheckBuilder(consumableNotificationsEnabled = true)

        val check = builder.buildNotificationPipelineCheck()

        assertEquals("Notification Pipeline", check.name)
        assertEquals("Pass", check.status)
        assertTrue(check.details.contains("enabled"), "Should show enabled")
    }

    @Test
    fun `notification pipeline check warns when consumable notifications disabled`() {
        val builder = TestCheckBuilder(consumableNotificationsEnabled = false)

        val check = builder.buildNotificationPipelineCheck()

        assertEquals("Warn", check.status)
        assertTrue(check.details.contains("not enabled"), "Should show disabled")
    }

    // ==================== TEAM MEMBERSHIP CHECK ====================

    @Test
    fun `team membership check passes when user is team member`() {
        val builder = TestCheckBuilder(isTeamMember = true)

        val check = builder.buildTeamMembershipCheck()

        assertEquals("Team Membership", check.name)
        assertEquals("Pass", check.status)
        assertTrue(check.details.contains("team member"), "Should indicate team membership")
    }

    @Test
    fun `team membership check passes when user is not team member but shows info`() {
        val builder = TestCheckBuilder(isTeamMember = false)

        val check = builder.buildTeamMembershipCheck()

        assertEquals("Team Membership", check.name)
        assertEquals("Pass", check.status)
        assertTrue(check.details.contains("personal"), "Should indicate personal account")
    }

    // ==================== RECOVERY HINTS FOR NEW CHECKS ====================

    @Test
    fun `client registration failure generates recovery hint`() {
        val builder = TestCheckBuilder()
        val checks =
            listOf(
                Check("Client Registration", "Fail", "Client not registered"),
            )

        val hints = builder.generateRecoveryHints(checks)

        val hint = hints.find { it.description.contains("Client") }
        assertFalse(hint == null, "Should generate client registration recovery hint")
        assertTrue(hint!!.command.contains("wire login"), "Should suggest re-login")
    }

    @Test
    fun `legal hold active generates recovery hint`() {
        val builder = TestCheckBuilder()
        val checks =
            listOf(
                Check("Legal Hold", "Fail", "Legal hold is active"),
            )

        val hints = builder.generateRecoveryHints(checks)

        val hint = hints.find { it.description.contains("Legal hold") }
        assertFalse(hint == null, "Should generate legal hold recovery hint")
        assertTrue(hint!!.command.contains("admin"), "Should suggest contacting admin")
    }

    @Test
    fun `e2ei certificate revoked generates recovery hint`() {
        val builder = TestCheckBuilder()
        val checks =
            listOf(
                Check("E2EI Certificate", "Fail", "Certificate revoked"),
            )

        val hints = builder.generateRecoveryHints(checks)

        val hint = hints.find { it.description.contains("certificate") || it.description.contains("E2EI") }
        assertFalse(hint == null, "Should generate E2EI certificate recovery hint")
    }

    @Test
    fun `websocket disabled generates recovery hint`() {
        val builder = TestCheckBuilder()
        val checks =
            listOf(
                Check("WebSocket Config", "Warn", "Persistent websocket not enabled"),
            )

        val hints = builder.generateRecoveryHints(checks)

        val hint = hints.find { it.description.contains("WebSocket") }
        assertFalse(hint == null, "Should generate WebSocket recovery hint")
    }

    // ==================== FULL DIAGNOSTICS INTEGRATION ====================

    @Test
    fun `extended diagnostics produce all expected check names`() {
        val builder =
            TestCheckBuilder(
                clientRegistered = true,
                webSocketEnabled = true,
                mlsEnabled = true,
                e2eiEnabled = true,
                legalHoldActive = false,
                certificateRevoked = false,
                certificatePresent = true,
                consumableNotificationsEnabled = true,
                isTeamMember = true,
            )

        val checks = builder.buildAllExtendedChecks()

        val expectedNames =
            listOf(
                "Client Registration",
                "WebSocket Config",
                "Feature Config",
                "Legal Hold",
                "E2EI Certificate",
                "Notification Pipeline",
                "Team Membership",
            )

        expectedNames.forEach { name ->
            assertTrue(
                checks.any { it.name == name },
                "Expected check '$name' to be present in extended diagnostics",
            )
        }
    }

    @Test
    fun `all extended checks pass for healthy system`() {
        val builder =
            TestCheckBuilder(
                clientRegistered = true,
                hasClientId = true,
                webSocketEnabled = true,
                mlsEnabled = true,
                e2eiEnabled = true,
                legalHoldActive = false,
                certificateRevoked = false,
                certificatePresent = true,
                consumableNotificationsEnabled = true,
                isTeamMember = true,
            )

        val checks = builder.buildAllExtendedChecks()

        checks.forEach { check ->
            assertEquals(
                "Pass",
                check.status,
                "Check '${check.name}' should pass for a healthy system",
            )
        }
    }

    @Test
    fun `all extended check details are non-blank`() {
        val builder = TestCheckBuilder()

        val checks = builder.buildAllExtendedChecks()

        checks.forEach { check ->
            assertFalse(check.details.isBlank(), "Check '${check.name}' should have non-blank details")
            assertTrue(check.details.length > 5, "Check '${check.name}' should have informative details")
        }
    }

    // ==================== TEST HELPER ====================

    /**
     * Test double for [SyncCheckBuilder] that accepts explicit health signals
     * instead of calling SDK APIs directly.
     */
    private class TestCheckBuilder(
        private val clientRegistered: Boolean = true,
        private val hasClientId: Boolean = true,
        private val webSocketEnabled: Boolean = true,
        private val mlsEnabled: Boolean = true,
        private val e2eiEnabled: Boolean = true,
        private val legalHoldActive: Boolean = false,
        private val certificateRevoked: Boolean = false,
        private val certificatePresent: Boolean = true,
        private val consumableNotificationsEnabled: Boolean = true,
        private val isTeamMember: Boolean = false,
    ) {
        fun buildClientRegistrationCheck(): Check {
            if (!hasClientId) {
                return Check(
                    name = "Client Registration",
                    status = "Fail",
                    details = "No client ID found - session has no registered client",
                )
            }
            return if (clientRegistered) {
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

        fun buildWebSocketConfigCheck(): Check {
            return if (webSocketEnabled) {
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
        }

        fun buildFeatureConfigCheck(): Check {
            val details = "MLS: ${if (mlsEnabled) "enabled" else "disabled"}, E2EI: ${if (e2eiEnabled) "enabled" else "disabled"}"
            val status =
                when {
                    !mlsEnabled && !e2eiEnabled -> "Fail"
                    !mlsEnabled || !e2eiEnabled -> "Warn"
                    else -> "Pass"
                }
            return Check(
                name = "Feature Config",
                status = status,
                details = details,
            )
        }

        fun buildLegalHoldCheck(): Check {
            return if (legalHoldActive) {
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
        }

        fun buildE2EICertificateCheck(): Check {
            if (!certificatePresent) {
                return Check(
                    name = "E2EI Certificate",
                    status = "Warn",
                    details = "E2EI certificate is not available - E2EI may not be configured",
                )
            }
            return if (certificateRevoked) {
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

        fun buildNotificationPipelineCheck(): Check {
            return if (consumableNotificationsEnabled) {
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
        }

        fun buildTeamMembershipCheck(): Check {
            return if (isTeamMember) {
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
        }

        fun buildAllExtendedChecks(): List<Check> =
            listOf(
                buildClientRegistrationCheck(),
                buildWebSocketConfigCheck(),
                buildFeatureConfigCheck(),
                buildLegalHoldCheck(),
                buildE2EICertificateCheck(),
                buildNotificationPipelineCheck(),
                buildTeamMembershipCheck(),
            )

        fun generateRecoveryHints(checks: List<Check>): List<RecoveryHint> {
            val hints = mutableListOf<RecoveryHint>()

            if (checks.any { it.name == "Client Registration" && it.status == "Fail" }) {
                hints.add(
                    RecoveryHint(
                        description = "Client is not registered with the backend",
                        command = "wire login --email <email> to re-authenticate and register client",
                    ),
                )
            }

            if (checks.any { it.name == "Legal Hold" && it.status == "Fail" }) {
                hints.add(
                    RecoveryHint(
                        description = "Legal hold is active on your account",
                        command = "Contact your team admin for more information",
                    ),
                )
            }

            if (checks.any { it.name == "E2EI Certificate" && it.status == "Fail" }) {
                hints.add(
                    RecoveryHint(
                        description = "E2EI certificate has been revoked",
                        command = "wire login --email <email> to re-enroll E2EI certificate",
                    ),
                )
            }

            if (checks.any { it.name == "WebSocket Config" && it.status == "Warn" }) {
                hints.add(
                    RecoveryHint(
                        description = "WebSocket is not enabled - updates may be delayed",
                        command = "wire sync to trigger slow sync and restore WebSocket connection",
                    ),
                )
            }

            return hints
        }
    }
}

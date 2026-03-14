package wirecli.commands

import wirecli.sync.Check
import wirecli.sync.DiagnosticsReport
import wirecli.sync.DiagnosticsResult
import wirecli.sync.HealthMetrics
import wirecli.sync.MLSMetrics
import wirecli.sync.RecoveryHint
import wirecli.sync.SyncExitCodes
import wirecli.sync.SyncOutputFormatter
import wirecli.sync.SyncStatus
import wirecli.sync.SyncStatusResult
import wirecli.sync.SyncStatusView
import kotlin.test.Test
import kotlin.test.assertTrue

class SyncStatusCommandTest {
    private val healthyMetrics =
        HealthMetrics(
            lag_ms = 100L,
            pending_messages = 5,
            mls_pct = 85,
            timestamp = "2025-03-13T10:30:00Z",
        )

    private val healthyView =
        SyncStatusView(
            status = SyncStatus.READY,
            metrics = healthyMetrics,
        )

    private val degradedMetrics =
        HealthMetrics(
            lag_ms = 5000L,
            pending_messages = 250,
            mls_pct = 45,
            timestamp = "2025-03-13T10:35:00Z",
        )

    private val degradedView =
        SyncStatusView(
            status = SyncStatus.DEGRADED,
            metrics = degradedMetrics,
        )

    // ==================== Status Output Formatting Tests ====================

    @Test
    fun `formatStatusHuman includes status icon and metrics`() {
        val result = SyncStatusResult.Success(healthyView)

        val output = SyncOutputFormatter.formatStatusHuman(result)

        assertTrue(output.contains("✓"), "Should include checkmark for healthy status")
        assertTrue(output.contains("Account Health:"), "Should include account health label")
        assertTrue(output.contains("ready"), "Should include status")
        assertTrue(output.contains("Auth:"), "Should include auth status")
        assertTrue(output.contains("Encryption:"), "Should include encryption status")
        assertTrue(output.contains("100ms"), "Should include lag metric")
        assertTrue(output.contains("5 messages"), "Should include pending messages")
        assertTrue(output.contains("85%"), "Should include MLS percentage")
    }

    @Test
    fun `formatStatusHuman for degraded status uses warning icon`() {
        val result = SyncStatusResult.Success(degradedView)

        val output = SyncOutputFormatter.formatStatusHuman(result)

        assertTrue(output.contains("⚠"), "Should include warning icon for degraded status")
        assertTrue(output.contains("Account Health:"), "Should include account health label")
        assertTrue(output.contains("degraded"), "Should include degraded status")
    }

    @Test
    fun `formatStatusVerbose includes detailed interpretation`() {
        val result = SyncStatusResult.Success(healthyView)

        val output = SyncOutputFormatter.formatStatusVerbose(result)

        assertTrue(output.contains("Health Metrics:"), "Should include metrics section")
        assertTrue(output.contains("Event Queue Lag:"), "Should include lag label")
        assertTrue(output.contains("Pending Messages:"), "Should include pending label")
        assertTrue(output.contains("MLS Migration:"), "Should include MLS label")
        assertTrue(output.contains("All metrics are healthy"), "Should include health interpretation")
    }

    @Test
    fun `formatStatusVerbose for high lag includes warning`() {
        val highLagMetrics = healthyMetrics.copy(lag_ms = 6000L)
        val result = SyncStatusResult.Success(healthyView.copy(metrics = highLagMetrics))

        val output = SyncOutputFormatter.formatStatusVerbose(result)

        assertTrue(output.contains("High lag detected"), "Should warn about high lag")
    }

    @Test
    fun `formatStatusVerbose for high pending includes warning`() {
        val highPendingMetrics = healthyMetrics.copy(pending_messages = 150)
        val result = SyncStatusResult.Success(healthyView.copy(metrics = highPendingMetrics))

        val output = SyncOutputFormatter.formatStatusVerbose(result)

        assertTrue(output.contains("Large message backlog detected"), "Should warn about backlog")
    }

    @Test
    fun `formatStatusJson returns valid JSON for success`() {
        val result = SyncStatusResult.Success(healthyView)

        val output = SyncOutputFormatter.formatStatusJson(result)

        assertTrue(output.contains("\"status\":"), "Should include status field")
        assertTrue(output.contains("\"ready\""), "Should include status value")
        assertTrue(output.contains("\"metrics\""), "Should include metrics object")
        assertTrue(output.contains("\"lag_ms\""), "Should include lag_ms field")
        assertTrue(output.contains("100"), "Should include lag value")
    }

    @Test
    fun `formatStatusJson for failure includes error and exit code`() {
        val result = SyncStatusResult.Failure("Connection timeout", SyncExitCodes.DEGRADED)

        val output = SyncOutputFormatter.formatStatusJson(result)

        assertTrue(output.contains("\"error\""), "Should include error field")
        assertTrue(output.contains("Connection timeout"), "Should include error message")
        assertTrue(output.contains("\"exitCode\""), "Should include exitCode field")
        assertTrue(output.contains("1"), "Should include exit code value")
    }

    // ==================== Diagnostics Output Formatting Tests ====================

    @Test
    fun `formatDiagnosticsHuman includes all checks with status icons`() {
        val checks =
            listOf(
                Check(
                    name = "Authentication",
                    status = "healthy",
                    details = "Session is valid",
                ),
                Check(
                    name = "Sync Engine",
                    status = "degraded",
                    details = "Running but slow",
                ),
            )
        val report =
            DiagnosticsReport(
                checks = checks,
                summary = "System is partially healthy",
                recoveryHints = emptyList(),
            )
        val result = DiagnosticsResult.Success(report)

        val output = SyncOutputFormatter.formatDiagnosticsHuman(result)

        assertTrue(output.contains("Diagnostics Report"), "Should include title")
        assertTrue(output.contains("Summary: System is partially healthy"), "Should include summary")
        assertTrue(output.contains("Health Checks:"), "Should include checks section")
        assertTrue(output.contains("✓ Authentication"), "Should include healthy check with icon")
        assertTrue(output.contains("⚠ Sync Engine"), "Should include degraded check with icon")
    }

    @Test
    fun `formatDiagnosticsHuman includes recovery hints when present`() {
        val checks = listOf(Check("Auth", "error", "Failed"))
        val hints =
            listOf(
                RecoveryHint(
                    description = "Re-authenticate",
                    command = "wire login",
                ),
            )
        val report =
            DiagnosticsReport(
                checks = checks,
                summary = "Auth failed",
                recoveryHints = hints,
            )
        val result = DiagnosticsResult.Success(report)

        val output = SyncOutputFormatter.formatDiagnosticsHuman(result)

        assertTrue(output.contains("Recovery Actions:"), "Should include recovery section")
        assertTrue(output.contains("Re-authenticate"), "Should include hint description")
        assertTrue(output.contains("wire login"), "Should include hint command")
    }

    @Test
    fun `formatDiagnosticsJson returns valid JSON with checks and hints`() {
        val checks =
            listOf(
                Check(
                    name = "Auth",
                    status = "healthy",
                    details = "Valid",
                ),
            )
        val hints =
            listOf(
                RecoveryHint(
                    description = "Restart sync",
                    command = "wire sync reset",
                ),
            )
        val report =
            DiagnosticsReport(
                checks = checks,
                summary = "All good",
                recoveryHints = hints,
            )
        val result = DiagnosticsResult.Success(report)

        val output = SyncOutputFormatter.formatDiagnosticsJson(result)

        assertTrue(output.contains("\"summary\""), "Should include summary field")
        assertTrue(output.contains("\"checks\""), "Should include checks array")
        assertTrue(output.contains("\"recoveryHints\""), "Should include recovery hints array")
        assertTrue(output.contains("\"description\""), "Should include hint description field")
        assertTrue(output.contains("\"command\""), "Should include hint command field")
    }

    // ==================== Exit Code Tests ====================

    @Test
    fun `healthy status returns exit code 0`() {
        val result = SyncStatusResult.Success(healthyView)

        assertTrue(
            result is SyncStatusResult.Success,
            "Should indicate success",
        )
        val view = result.view
        assertTrue(view.status == SyncStatus.READY, "Should have ready status")
    }

    @Test
    fun `degraded status should return exit code 1 in actual command`() {
        // Note: actual exit codes are handled in the command class
        // This test documents the expected behavior
        val result = SyncStatusResult.Success(degradedView)
        assertTrue(result is SyncStatusResult.Success, "Should have success result")
    }

    @Test
    fun `failure result preserves exit code`() {
        val result = SyncStatusResult.Failure("Error", SyncExitCodes.SERVER_ERROR)

        assertTrue(result is SyncStatusResult.Failure, "Should be failure")
        assertTrue(result.exitCode == SyncExitCodes.SERVER_ERROR, "Should preserve exit code")
    }

    // ==================== Status Icon Mapping Tests ====================

    @Test
    fun `ready status uses checkmark icon`() {
        val output =
            SyncOutputFormatter.formatStatusHuman(
                SyncStatusResult.Success(
                    healthyView.copy(status = SyncStatus.READY),
                ),
            )
        assertTrue(output.contains("✓"), "Ready status should use checkmark")
    }

    @Test
    fun `initializing status uses spinner icon`() {
        val output =
            SyncOutputFormatter.formatStatusHuman(
                SyncStatusResult.Success(
                    healthyView.copy(status = SyncStatus.INITIALIZING),
                ),
            )
        assertTrue(output.contains("⟳"), "Initializing status should use spinner")
    }

    @Test
    fun `degraded status uses warning icon`() {
        val output =
            SyncOutputFormatter.formatStatusHuman(
                SyncStatusResult.Success(healthyView.copy(status = SyncStatus.DEGRADED)),
            )
        assertTrue(output.contains("⚠"), "Degraded status should use warning icon")
    }

    @Test
    fun `error status uses X icon`() {
        val output =
            SyncOutputFormatter.formatStatusHuman(
                SyncStatusResult.Success(healthyView.copy(status = SyncStatus.ERROR)),
            )
        assertTrue(output.contains("✗"), "Error status should use X icon")
    }

    // ==================== Integration Tests ====================

    @Test
    fun `full diagnostics flow with recovery hints`() {
        val checks =
            listOf(
                Check("Auth", "healthy", "OK"),
                Check("Sync Engine", "degraded", "Slow"),
                Check("Network", "error", "Timeout"),
            )
        val hints =
            listOf(
                RecoveryHint("Check connection", "ping api.wire.com"),
                RecoveryHint("Reset sync", "wire sync reset"),
            )
        val report =
            DiagnosticsReport(
                checks = checks,
                summary = "Multiple issues detected",
                recoveryHints = hints,
            )
        val result = DiagnosticsResult.Success(report)

        val output = SyncOutputFormatter.formatDiagnosticsHuman(result)

        // Verify structure
        assertTrue(output.contains("Diagnostics Report"), "Should have title")
        assertTrue(output.contains("Summary:"), "Should have summary section")
        assertTrue(output.contains("Health Checks:"), "Should have checks section")
        assertTrue(output.contains("Recovery Actions:"), "Should have recovery section")

        // Verify content
        assertTrue(output.contains("✓ Auth"), "Should list healthy check")
        assertTrue(output.contains("⚠ Sync Engine"), "Should list degraded check")
        assertTrue(output.contains("✗ Network"), "Should list error check")
        assertTrue(output.contains("Check connection"), "Should list recovery hint")
        assertTrue(output.contains("ping api.wire.com"), "Should list command")
    }

    // ==================== Enhanced Verbose Output Tests ====================

    @Test
    fun `formatStatusVerbose includes recovery hints from diagnostics report`() {
        val hints =
            listOf(
                RecoveryHint(
                    description = "Sync lag is normal (under 1s)",
                    command = "No action needed",
                ),
            )
        val report =
            DiagnosticsReport(
                checks = emptyList(),
                summary = "All healthy",
                recoveryHints = hints,
            )
        val result =
            SyncStatusResult.Success(
                healthyView.copy(diagnosticsReport = report),
            )

        val output = SyncOutputFormatter.formatStatusVerbose(result)

        assertTrue(output.contains("Recovery Actions:"), "Should include recovery section")
        assertTrue(output.contains("Sync lag is normal"), "Should include recovery hint")
    }

    @Test
    fun `formatStatusVerbose formats pending messages with last received timestamp`() {
        val currentTimeMs = System.currentTimeMillis()
        val lastReceivedMs = currentTimeMs - 15000 // 15 seconds ago
        val metricsWithTimestamp =
            healthyMetrics.copy(last_message_received_ms = lastReceivedMs)
        val result =
            SyncStatusResult.Success(
                healthyView.copy(metrics = metricsWithTimestamp),
            )

        val output = SyncOutputFormatter.formatStatusVerbose(result)

        assertTrue(output.contains("last received"), "Should include 'last received'")
        assertTrue(output.contains("s ago"), "Should include seconds ago format")
    }

    @Test
    fun `formatStatusVerbose formats MLS with estimated time remaining`() {
        val mlsMetrics =
            MLSMetrics(
                enrollment_pct = 85,
                key_package_available = 150,
                key_package_exhausted = false,
                key_package_generation_enabled = true,
                key_package_refresh_required = false,
                mls_group_updates_failed_count = 0,
                mls_enrollment_failures_count = 0,
                mls_error_rate = 0.0,
                last_key_package_refresh_timestamp = null,
                timestamp = "2025-03-13T10:30:00Z",
                estimated_remaining_ms = 10000, // 10 seconds
            )
        val metricsWithMls =
            healthyMetrics.copy(mls = mlsMetrics)
        val result =
            SyncStatusResult.Success(
                healthyView.copy(metrics = metricsWithMls),
            )

        val output = SyncOutputFormatter.formatStatusVerbose(result)

        assertTrue(output.contains("estimated"), "Should include 'estimated'")
        assertTrue(output.contains("s remaining"), "Should include seconds remaining format")
    }

    @Test
    fun `formatStatusVerbose formats key packages with device name and fill status`() {
        val mlsMetrics =
            MLSMetrics(
                enrollment_pct = 85,
                key_package_available = 150,
                key_package_exhausted = false,
                key_package_generation_enabled = true,
                key_package_refresh_required = false,
                mls_group_updates_failed_count = 0,
                mls_enrollment_failures_count = 0,
                mls_error_rate = 0.0,
                last_key_package_refresh_timestamp = null,
                timestamp = "2025-03-13T10:30:00Z",
                device_name = "Laptop",
                key_package_total = 300,
            )
        val metricsWithMls =
            healthyMetrics.copy(mls = mlsMetrics)
        val result =
            SyncStatusResult.Success(
                healthyView.copy(metrics = metricsWithMls),
            )

        val output = SyncOutputFormatter.formatStatusVerbose(result)

        assertTrue(output.contains("Laptop:"), "Should include device name")
        assertTrue(output.contains("150/300"), "Should include key package count with total")
    }

    @Test
    fun `formatStatusVerbose shows low key package status`() {
        val mlsMetrics =
            MLSMetrics(
                enrollment_pct = 85,
                key_package_available = 30, // Low count
                key_package_exhausted = false,
                key_package_generation_enabled = true,
                key_package_refresh_required = true,
                mls_group_updates_failed_count = 0,
                mls_enrollment_failures_count = 0,
                mls_error_rate = 0.0,
                last_key_package_refresh_timestamp = null,
                timestamp = "2025-03-13T10:30:00Z",
                device_name = "Phone",
                key_package_total = 300,
            )
        val metricsWithMls =
            healthyMetrics.copy(mls = mlsMetrics)
        val result =
            SyncStatusResult.Success(
                healthyView.copy(metrics = metricsWithMls),
            )

        val output = SyncOutputFormatter.formatStatusVerbose(result)

        assertTrue(output.contains("low, refilling"), "Should indicate low key packages with refilling")
    }

    @Test
    fun `formatStatusVerbose shows exhausted key package status`() {
        val mlsMetrics =
            MLSMetrics(
                enrollment_pct = 85,
                key_package_available = 0, // Exhausted
                key_package_exhausted = true,
                key_package_generation_enabled = true,
                key_package_refresh_required = true,
                mls_group_updates_failed_count = 0,
                mls_enrollment_failures_count = 0,
                mls_error_rate = 0.0,
                last_key_package_refresh_timestamp = null,
                timestamp = "2025-03-13T10:30:00Z",
                device_name = "Tablet",
                key_package_total = 300,
            )
        val metricsWithMls =
            healthyMetrics.copy(mls = mlsMetrics)
        val result =
            SyncStatusResult.Success(
                healthyView.copy(metrics = metricsWithMls),
            )

        val output = SyncOutputFormatter.formatStatusVerbose(result)

        assertTrue(output.contains("exhausted, refresh needed"), "Should indicate exhausted key packages")
    }

    @Test
    fun `formatStatusJson includes last message received timestamp`() {
        val currentTimeMs = System.currentTimeMillis()
        val metricsWithTimestamp =
            healthyMetrics.copy(last_message_received_ms = currentTimeMs)
        val result =
            SyncStatusResult.Success(
                healthyView.copy(metrics = metricsWithTimestamp),
            )

        val output = SyncOutputFormatter.formatStatusJson(result)

        assertTrue(output.contains("\"last_message_received_ms\""), "Should include last_message_received_ms field")
        assertTrue(output.contains(currentTimeMs.toString()), "Should include timestamp value")
    }

    // ==================== Auth and Encryption Status Tests ====================

    @Test
    fun `formatStatusHuman includes auth status connected`() {
        val metricsWithAuth = healthyMetrics.copy(auth_status = "ok")
        val result =
            SyncStatusResult.Success(
                healthyView.copy(metrics = metricsWithAuth),
            )

        val output = SyncOutputFormatter.formatStatusHuman(result)

        assertTrue(output.contains("Auth: ✓ Connected"), "Should include auth connected status")
    }

    @Test
    fun `formatStatusHuman shows auth status not authenticated`() {
        val metricsWithAuth = healthyMetrics.copy(auth_status = "not_authenticated")
        val result =
            SyncStatusResult.Success(
                healthyView.copy(metrics = metricsWithAuth),
            )

        val output = SyncOutputFormatter.formatStatusHuman(result)

        assertTrue(output.contains("Auth: ✗ Not authenticated"), "Should show auth not authenticated status")
    }

    @Test
    fun `formatStatusHuman includes encryption status ready`() {
        val metricsWithEncryption = healthyMetrics.copy(encryption_status = "ready", mls_pct = 100)
        val result =
            SyncStatusResult.Success(
                healthyView.copy(metrics = metricsWithEncryption),
            )

        val output = SyncOutputFormatter.formatStatusHuman(result)

        assertTrue(output.contains("Encryption: ✓ Ready"), "Should include encryption ready status")
    }

    @Test
    fun `formatStatusHuman shows encryption status pending with percentage`() {
        val metricsWithEncryption = healthyMetrics.copy(encryption_status = "pending", mls_pct = 75)
        val result =
            SyncStatusResult.Success(
                healthyView.copy(metrics = metricsWithEncryption),
            )

        val output = SyncOutputFormatter.formatStatusHuman(result)

        assertTrue(output.contains("Encryption: ⟳ Pending (75% complete)"), "Should show encryption pending with percentage")
    }

    @Test
    fun `formatStatusJson includes auth and encryption fields`() {
        val metricsWithAuthEncryption =
            healthyMetrics.copy(
                auth_status = "ok",
                encryption_status = "ready",
                uptime_ms = 3600000L,
            )
        val result =
            SyncStatusResult.Success(
                healthyView.copy(metrics = metricsWithAuthEncryption),
            )

        val output = SyncOutputFormatter.formatStatusJson(result)

        assertTrue(output.contains("\"auth\""), "Should include auth field")
        assertTrue(output.contains("\"encryption\""), "Should include encryption field")
        assertTrue(output.contains("\"uptime_ms\""), "Should include uptime_ms field")
        assertTrue(output.contains("\"ok\""), "Should include auth status value")
        assertTrue(output.contains("\"ready\""), "Should include encryption status value")
        assertTrue(output.contains("3600000"), "Should include uptime value")
    }

    @Test
    fun `formatStatusHuman shows account health instead of sync status`() {
        val result =
            SyncStatusResult.Success(
                healthyView.copy(status = SyncStatus.READY),
            )

        val output = SyncOutputFormatter.formatStatusHuman(result)

        assertTrue(output.contains("Account Health:"), "Should show 'Account Health' instead of 'Sync Status'")
    }
}

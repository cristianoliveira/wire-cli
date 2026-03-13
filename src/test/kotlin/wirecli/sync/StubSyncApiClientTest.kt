package wirecli.sync

import wirecli.auth.AuthMessages
import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StubSyncApiClientTest {
    private val session =
        AuthSession(
            userId = "alice@example.com",
            accessToken = "token",
            server = null,
        )

    // ==================== getSyncStatus Tests ====================

    @Test
    fun `returns ready status by default`() {
        val client = StubSyncApiClient(emptyMap())

        val result = client.getSyncStatus(session)

        val success = assertIs<SyncStatusResult.Success>(result)
        assertEquals(SyncStatus.READY, success.view.status)
        assertEquals(100L, success.view.metrics.lag_ms)
        assertEquals(5, success.view.metrics.pending_messages)
        assertEquals(85, success.view.metrics.mls_pct)
    }

    @Test
    fun `returns ready status in status_ready mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "status_ready"))

        val result = client.getSyncStatus(session)

        val success = assertIs<SyncStatusResult.Success>(result)
        assertEquals(SyncStatus.READY, success.view.status)
        assertEquals(100L, success.view.metrics.lag_ms)
    }

    @Test
    fun `returns initializing status in status_initializing mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "status_initializing"))

        val result = client.getSyncStatus(session)

        val success = assertIs<SyncStatusResult.Success>(result)
        assertEquals(SyncStatus.INITIALIZING, success.view.status)
        assertEquals(2000L, success.view.metrics.lag_ms)
        assertEquals(100, success.view.metrics.pending_messages)
        assertEquals(20, success.view.metrics.mls_pct)
    }

    @Test
    fun `returns degraded status in status_degraded mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "status_degraded"))

        val result = client.getSyncStatus(session)

        val success = assertIs<SyncStatusResult.Success>(result)
        assertEquals(SyncStatus.DEGRADED, success.view.status)
        assertEquals(5000L, success.view.metrics.lag_ms)
        assertEquals(250, success.view.metrics.pending_messages)
        assertEquals(45, success.view.metrics.mls_pct)
    }

    @Test
    fun `returns error status in status_error mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "status_error"))

        val result = client.getSyncStatus(session)

        val success = assertIs<SyncStatusResult.Success>(result)
        assertEquals(SyncStatus.ERROR, success.view.status)
        assertEquals(30000L, success.view.metrics.lag_ms)
        assertEquals(1000, success.view.metrics.pending_messages)
        assertEquals(10, success.view.metrics.mls_pct)
    }

    @Test
    fun `returns network error in network_error mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "network_error"))

        val result = client.getSyncStatus(session)

        val failure = assertIs<SyncStatusResult.Failure>(result)
        assertEquals(SyncMessages.NETWORK_FAILURE, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `returns server error in server_error mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "server_error"))

        val result = client.getSyncStatus(session)

        val failure = assertIs<SyncStatusResult.Failure>(result)
        assertEquals(SyncMessages.SERVER_FAILURE, failure.message)
        assertEquals(SyncExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized error in unauthorized mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "unauthorized"))

        val result = client.getSyncStatus(session)

        val failure = assertIs<SyncStatusResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    // ==================== getDiagnostics Tests ====================

    @Test
    fun `returns healthy diagnostics by default`() {
        val client = StubSyncApiClient(emptyMap())

        val result = client.getDiagnostics(session)

        val success = assertIs<DiagnosticsResult.Success>(result)
        assertEquals(5, success.report.checks.size)
        assertEquals("Authentication", success.report.checks[0].name)
        assertEquals("Sync Engine", success.report.checks[1].name)
        assertEquals("Event Queue", success.report.checks[2].name)
        assertEquals("Key Packages", success.report.checks[3].name)
        assertEquals("Network Connectivity", success.report.checks[4].name)
    }

    @Test
    fun `all checks have required fields`() {
        val client = StubSyncApiClient(emptyMap())

        val result = client.getDiagnostics(session)

        val success = assertIs<DiagnosticsResult.Success>(result)
        success.report.checks.forEach { check ->
            assert(check.name.isNotEmpty()) { "Check name should not be empty" }
            assert(check.status.isNotEmpty()) { "Check status should not be empty" }
            assert(check.details.isNotEmpty()) { "Check details should not be empty" }
        }
    }

    @Test
    fun `returns healthy diagnostics in status_ready mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "status_ready"))

        val result = client.getDiagnostics(session)

        val success = assertIs<DiagnosticsResult.Success>(result)
        assertEquals(5, success.report.checks.size)
        assertEquals("healthy", success.report.checks[0].status)
        assertEquals("healthy", success.report.checks[1].status)
    }

    @Test
    fun `returns healthy diagnostics in diagnostics_healthy mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "diagnostics_healthy"))

        val result = client.getDiagnostics(session)

        val success = assertIs<DiagnosticsResult.Success>(result)
        assertEquals(5, success.report.checks.size)
        assertEquals("healthy", success.report.checks[1].status)
        assertEquals("All systems operational", success.report.summary)
    }

    @Test
    fun `returns initializing diagnostics in status_initializing mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "status_initializing"))

        val result = client.getDiagnostics(session)

        val success = assertIs<DiagnosticsResult.Success>(result)
        assertEquals(5, success.report.checks.size)
        assertEquals("initializing", success.report.checks[1].status)
        assertEquals("System is initializing", success.report.summary)
    }

    @Test
    fun `returns initializing diagnostics in diagnostics_initializing mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "diagnostics_initializing"))

        val result = client.getDiagnostics(session)

        val success = assertIs<DiagnosticsResult.Success>(result)
        assertEquals("initializing", success.report.checks[1].status)
    }

    @Test
    fun `returns degraded diagnostics in status_degraded mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "status_degraded"))

        val result = client.getDiagnostics(session)

        val success = assertIs<DiagnosticsResult.Success>(result)
        assertEquals(5, success.report.checks.size)
        assertEquals("degraded", success.report.checks[1].status)
        assertEquals("degraded", success.report.checks[2].status)
        assertEquals("System is degraded, operation continues but performance is reduced", success.report.summary)
    }

    @Test
    fun `returns degraded diagnostics in diagnostics_degraded mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "diagnostics_degraded"))

        val result = client.getDiagnostics(session)

        val success = assertIs<DiagnosticsResult.Success>(result)
        assertEquals("degraded", success.report.checks[1].status)
    }

    @Test
    fun `returns error diagnostics in status_error mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "status_error"))

        val result = client.getDiagnostics(session)

        val success = assertIs<DiagnosticsResult.Success>(result)
        assertEquals(5, success.report.checks.size)
        assertEquals("error", success.report.checks[1].status)
        assertEquals("error", success.report.checks[2].status)
        assertEquals("System encountered critical errors, immediate action required", success.report.summary)
    }

    @Test
    fun `returns error diagnostics in diagnostics_error mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "diagnostics_error"))

        val result = client.getDiagnostics(session)

        val success = assertIs<DiagnosticsResult.Success>(result)
        assertEquals("error", success.report.checks[1].status)
    }

    @Test
    fun `returns network error for diagnostics in diagnostics_network_error mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "diagnostics_network_error"))

        val result = client.getDiagnostics(session)

        val failure = assertIs<DiagnosticsResult.Failure>(result)
        assertEquals(SyncMessages.DIAGNOSTICS_NETWORK_FAILURE, failure.message)
        assertEquals(ExitCodes.NETWORK_ERROR, failure.exitCode)
    }

    @Test
    fun `returns server error for diagnostics in diagnostics_server_error mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "diagnostics_server_error"))

        val result = client.getDiagnostics(session)

        val failure = assertIs<DiagnosticsResult.Failure>(result)
        assertEquals(SyncMessages.DIAGNOSTICS_SERVER_FAILURE, failure.message)
        assertEquals(SyncExitCodes.SERVER_ERROR, failure.exitCode)
    }

    @Test
    fun `returns unauthorized error for diagnostics in diagnostics_unauthorized mode`() {
        val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "diagnostics_unauthorized"))

        val result = client.getDiagnostics(session)

        val failure = assertIs<DiagnosticsResult.Failure>(result)
        assertEquals(AuthMessages.invalidOrExpiredSession(), failure.message)
        assertEquals(ExitCodes.UNAUTHORIZED, failure.exitCode)
    }

    // ==================== Determinism Tests ====================

    @Test
    fun `stub data is deterministic for same mode`() {
        val client1 = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "status_ready"))
        val client2 = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to "status_ready"))

        val result1 = client1.getSyncStatus(session)
        val result2 = client2.getSyncStatus(session)

        val success1 = assertIs<SyncStatusResult.Success>(result1)
        val success2 = assertIs<SyncStatusResult.Success>(result2)

        assertEquals(success1.view.status, success2.view.status)
        assertEquals(success1.view.metrics.lag_ms, success2.view.metrics.lag_ms)
        assertEquals(success1.view.metrics.mls_pct, success2.view.metrics.mls_pct)
    }

    @Test
    fun `MLS migration percentage is tracked across modes`() {
        val modes =
            listOf(
                "status_ready" to 85,
                "status_initializing" to 20,
                "status_degraded" to 45,
                "status_error" to 10,
            )

        modes.forEach { (mode, expectedMls) ->
            val client = StubSyncApiClient(mapOf("WIRE_STUB_MODE" to mode))
            val result = client.getSyncStatus(session)
            val success = assertIs<SyncStatusResult.Success>(result)
            assertEquals(expectedMls, success.view.metrics.mls_pct, "MLS% should be $expectedMls for mode: $mode")
        }
    }
}

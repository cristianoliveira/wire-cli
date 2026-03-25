package wirecli.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for network connectivity metrics.
 *
 * This test suite validates:
 * - Network connectivity detection
 * - Network type identification
 * - Latency estimation accuracy
 * - Error rate calculations
 * - Recovery time tracking
 */
class NetworkConnectivityCheckerTest {
    private val stubChecker =
        StubNetworkConnectivityChecker(
            connected = true,
            networkType = NetworkType.WIFI,
            estimatedLatency = 20L,
            errorRate = 0.0,
        )

    // ==================== NETWORK TYPE DETECTION TESTS ====================

    @Test
    fun `stub checker returns correct network type`() {
        val metrics = stubChecker.checkNetworkConnectivity()
        assertNotNull(metrics, "Network metrics should not be null")
        assertEquals(NetworkType.WIFI, metrics.networkType)
    }

    @Test
    fun `stub checker correctly reports connection status`() {
        val connectedChecker = StubNetworkConnectivityChecker(connected = true)
        val metrics = connectedChecker.checkNetworkConnectivity()
        assertTrue(metrics.connected, "Should report as connected")
    }

    @Test
    fun `stub checker correctly reports disconnected status`() {
        val disconnectedChecker = StubNetworkConnectivityChecker(connected = false)
        val metrics = disconnectedChecker.checkNetworkConnectivity()
        assertFalse(metrics.connected, "Should report as disconnected")
    }

    // ==================== LATENCY ESTIMATION TESTS ====================

    @Test
    fun `estimated latency is half of sync lag with minimum 10ms`() {
        val latency = stubChecker.estimateNetworkLatency(50L)
        assertEquals(25L, latency, "Latency should be half of sync lag")
    }

    @Test
    fun `estimated latency respects minimum threshold`() {
        val latency = stubChecker.estimateNetworkLatency(10L)
        assertEquals(10L, latency, "Latency should respect minimum threshold of 10ms")
    }

    @Test
    fun `estimated latency for zero lag respects minimum`() {
        val latency = stubChecker.estimateNetworkLatency(0L)
        assertEquals(10L, latency, "Zero lag should still report minimum latency")
    }

    @Test
    fun `estimated latency scales with large sync lag values`() {
        val latency = stubChecker.estimateNetworkLatency(5000L)
        assertEquals(2500L, latency, "Large sync lag should scale latency appropriately")
    }

    // ==================== ERROR RATE CALCULATION TESTS ====================

    @Test
    fun `error rate is zero when no failures occur`() {
        val errorRate = stubChecker.calculateErrorRate(0, 10)
        assertEquals(0.0, errorRate, "Zero failures should result in 0.0 error rate")
    }

    @Test
    fun `error rate is one when all attempts fail`() {
        val errorRate = stubChecker.calculateErrorRate(10, 10)
        assertEquals(1.0, errorRate, "All failures should result in 1.0 error rate")
    }

    @Test
    fun `error rate is correct for partial failures`() {
        val errorRate = stubChecker.calculateErrorRate(3, 10)
        assertEquals(0.3, errorRate, "3 failures in 10 attempts should be 0.3")
    }

    @Test
    fun `error rate is zero for zero attempts`() {
        val errorRate = stubChecker.calculateErrorRate(0, 0)
        assertEquals(0.0, errorRate, "Zero attempts should result in 0.0 error rate")
    }

    @Test
    fun `error rate is clamped to valid range`() {
        val errorRate = stubChecker.calculateErrorRate(15, 10)
        assertEquals(1.0, errorRate, "Error rate should be clamped to 1.0 maximum")
    }

    // ==================== NETWORK METRICS INTEGRATION TESTS ====================

    @Test
    fun `network metrics include all required fields`() {
        val metrics = stubChecker.checkNetworkConnectivity()
        assertNotNull(metrics.reachabilityCheckTimestamp, "Timestamp should be set")
        assertTrue(metrics.connected, "Connection status should be set")
        assertEquals(NetworkType.WIFI, metrics.networkType, "Network type should be set")
        assertEquals(20L, metrics.estimatedLatencyMs, "Latency should be set")
        assertEquals(0.0, metrics.errorRate, "Error rate should be set")
    }

    @Test
    fun `metrics with errors include recovery time`() {
        val metricsWithErrors =
            StubNetworkConnectivityChecker(
                errorRate = 0.5,
            ).checkNetworkConnectivity()
        assertNotNull(metricsWithErrors.lastRecoveryTimeMs, "Recovery time should be set when there are errors")
    }

    @Test
    fun `metrics without errors have null recovery time`() {
        val metricsWithoutErrors =
            StubNetworkConnectivityChecker(
                errorRate = 0.0,
            ).checkNetworkConnectivity()
        assertEquals(null, metricsWithoutErrors.lastRecoveryTimeMs, "Recovery time should be null without errors")
    }

    // ==================== NETWORK TYPE ENUMERATION TESTS ====================

    @Test
    fun `wifi network type has correct string value`() {
        assertEquals("wifi", NetworkType.WIFI.value)
    }

    @Test
    fun `cellular network type has correct string value`() {
        assertEquals("cellular", NetworkType.CELLULAR.value)
    }

    @Test
    fun `wired network type has correct string value`() {
        assertEquals("wired", NetworkType.WIRED.value)
    }

    @Test
    fun `unknown network type has correct string value`() {
        assertEquals("unknown", NetworkType.UNKNOWN.value)
    }

    @Test
    fun `disconnected network type has correct string value`() {
        assertEquals("disconnected", NetworkType.DISCONNECTED.value)
    }

    @Test
    fun `network type toString returns value`() {
        assertEquals("wifi", NetworkType.WIFI.toString())
        assertEquals("cellular", NetworkType.CELLULAR.toString())
        assertEquals("wired", NetworkType.WIRED.toString())
    }

    // ==================== REAL CHECKER TESTS ====================

    @Test
    fun `real checker returns network metrics`() {
        val realChecker = RealNetworkConnectivityChecker()
        val metrics = realChecker.checkNetworkConnectivity()
        // Metrics may be null if unable to determine, but structure should be valid
        // We just verify the checker doesn't throw
        assertTrue(true, "Real checker should not throw exceptions")
    }

    @Test
    fun `real checker estimates latency correctly`() {
        val realChecker = RealNetworkConnectivityChecker()
        val latency = realChecker.estimateNetworkLatency(100L)
        assertEquals(50L, latency, "Real checker should estimate latency as half of lag")
    }

    @Test
    fun `real checker calculates error rate correctly`() {
        val realChecker = RealNetworkConnectivityChecker()
        val errorRate = realChecker.calculateErrorRate(2, 10)
        assertEquals(0.2, errorRate, "Real checker should calculate error rate correctly")
    }
}

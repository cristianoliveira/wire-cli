package wirecli.sync

import java.time.Instant

/**
 * Network connectivity checker interface.
 *
 * Provides network status and quality metrics.
 */
internal interface NetworkConnectivityChecker {
    /**
     * Check current network connectivity and collect metrics.
     *
     * @return Network metrics or null if unable to determine
     */
    fun checkNetworkConnectivity(): NetworkMetrics?

    /**
     * Estimate network latency based on sync performance.
     *
     * @param syncLagMs The current sync lag in milliseconds
     * @return Estimated latency in milliseconds
     */
    fun estimateNetworkLatency(syncLagMs: Long): Long

    /**
     * Calculate error rate based on sync failures.
     *
     * @param failureCount Number of failures
     * @param totalAttempts Total number of attempts
     * @return Error rate as a decimal (0.0 to 1.0)
     */
    fun calculateErrorRate(
        failureCount: Int,
        totalAttempts: Int,
    ): Double
}

/**
 * Real implementation of network connectivity checker.
 *
 * Provides actual network metrics by checking system connectivity
 * and estimating latency from sync performance.
 */
internal class RealNetworkConnectivityChecker : NetworkConnectivityChecker {
    private var lastErrorTime: Instant? = null
    private var errorCount = 0
    private var attemptCount = 0

    override fun checkNetworkConnectivity(): NetworkMetrics? {
        return try {
            val isConnected = isNetworkConnected()
            val networkType = detectNetworkType()

            NetworkMetrics(
                connected = isConnected,
                network_type = networkType,
                estimated_latency_ms = estimateLatencyBasedOnSystemMetrics(),
                error_rate = calculateErrorRate(errorCount, attemptCount),
                last_recovery_time_ms = calculateLastRecoveryTime(),
                reachability_check_timestamp = Instant.now().toString(),
            )
        } catch (e: Exception) {
            // If we can't determine network status, return null
            null
        }
    }

    override fun estimateNetworkLatency(syncLagMs: Long): Long {
        // Estimate latency as approximately half the sync lag (since lag includes processing time)
        // Minimum 10ms to account for actual network latency
        return maxOf(10L, syncLagMs / 2)
    }

    override fun calculateErrorRate(
        failureCount: Int,
        totalAttempts: Int,
    ): Double {
        return if (totalAttempts == 0) {
            0.0
        } else {
            (failureCount.toDouble() / totalAttempts.toDouble()).coerceIn(0.0, 1.0)
        }
    }

    /**
     * Check if network is available by attempting to resolve a known hostname.
     */
    private fun isNetworkConnected(): Boolean {
        return try {
            // Try to resolve a reliable DNS - Google's public DNS
            java.net.InetAddress.getByName("8.8.8.8").hostAddress != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect the network type by checking system properties and network interfaces.
     */
    private fun detectNetworkType(): NetworkType {
        if (!isNetworkConnected()) {
            return NetworkType.DISCONNECTED
        }

        return try {
            val osName = System.getProperty("os.name").lowercase()
            when {
                osName.contains("mac") -> {
                    // On macOS, we can detect WiFi vs Ethernet
                    if (isWiFiConnected()) NetworkType.WIFI else NetworkType.WIRED
                }
                osName.contains("linux") -> {
                    // On Linux, check for WiFi vs Ethernet
                    if (isWiFiConnected()) NetworkType.WIFI else NetworkType.WIRED
                }
                osName.contains("windows") -> {
                    // On Windows, similar detection
                    if (isWiFiConnected()) NetworkType.WIFI else NetworkType.WIRED
                }
                else -> NetworkType.UNKNOWN
            }
        } catch (e: Exception) {
            NetworkType.UNKNOWN
        }
    }

    /**
     * Check if WiFi is the active network connection.
     */
    private fun isWiFiConnected(): Boolean {
        return try {
            val osName = System.getProperty("os.name").lowercase()
            when {
                osName.contains("mac") -> {
                    // macOS: check for en0 or en1 (WiFi interfaces)
                    val result = Runtime.getRuntime().exec("networksetup -getairportnetwork en0").inputStream.bufferedReader().readText()
                    result.isNotEmpty() && !result.contains("off")
                }
                osName.contains("linux") -> {
                    // Linux: check for wlan interface
                    val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                    interfaces.asSequence()
                        .any { it.name.startsWith("wlan") && it.isUp }
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Estimate system latency based on typical ping results.
     */
    private fun estimateLatencyBasedOnSystemMetrics(): Long {
        return try {
            // Try to ping a reliable host with timeout
            val runtime = Runtime.getRuntime()
            val process = runtime.exec(arrayOf("ping", "-c", "1", "-W", "1000", "8.8.8.8"))
            val startTime = System.currentTimeMillis()
            val exitCode = process.waitFor()
            val endTime = System.currentTimeMillis()

            if (exitCode == 0) {
                (endTime - startTime).coerceIn(1L, 5000L)
            } else {
                100L // Default estimate if ping fails
            }
        } catch (e: Exception) {
            50L // Default estimate if unable to ping
        }
    }

    /**
     * Calculate the time since the last error recovery.
     */
    private fun calculateLastRecoveryTime(): Long? {
        return if (lastErrorTime != null) {
            java.time.Duration.between(lastErrorTime, Instant.now()).toMillis()
        } else {
            null
        }
    }

    /**
     * Record a network error for tracking error rate.
     */
    fun recordNetworkError() {
        errorCount++
        attemptCount++
        lastErrorTime = Instant.now()
    }

    /**
     * Record a successful network attempt for tracking error rate.
     */
    fun recordNetworkSuccess() {
        attemptCount++
    }
}

/**
 * Stub implementation for testing purposes.
 */
internal class StubNetworkConnectivityChecker(
    private val connected: Boolean = true,
    private val networkType: NetworkType = NetworkType.WIFI,
    private val estimatedLatency: Long = 20L,
    private val errorRate: Double = 0.0,
) : NetworkConnectivityChecker {
    override fun checkNetworkConnectivity(): NetworkMetrics {
        return NetworkMetrics(
            connected = connected,
            network_type = networkType,
            estimated_latency_ms = estimatedLatency,
            error_rate = errorRate,
            last_recovery_time_ms = if (errorRate > 0.0) 5000L else null,
            reachability_check_timestamp = Instant.now().toString(),
        )
    }

    override fun estimateNetworkLatency(syncLagMs: Long): Long {
        // Use real calculation logic: approximately half the sync lag with minimum 10ms
        return maxOf(10L, syncLagMs / 2)
    }

    override fun calculateErrorRate(
        failureCount: Int,
        totalAttempts: Int,
    ): Double {
        // Use real calculation logic
        return if (totalAttempts == 0) {
            0.0
        } else {
            (failureCount.toDouble() / totalAttempts.toDouble()).coerceIn(0.0, 1.0)
        }
    }
}

package wirecli.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

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
        logger.debug { "Starting network connectivity check" }
        return try {
            val isConnected = isNetworkConnected()
            logger.debug { "Network connection status: connected=$isConnected" }

            val networkType = detectNetworkType()
            logger.debug { "Detected network type: $networkType" }

            val latency = estimateLatencyBasedOnSystemMetrics()
            logger.debug { "Estimated network latency: ${latency}ms" }

            val errorRate = calculateErrorRate(errorCount, attemptCount)
            logger.debug {
                "Network error rate: ${String.format("%.2f%%", errorRate * 100)} " +
                    "(errors: $errorCount, attempts: $attemptCount)"
            }

            val lastRecovery = calculateLastRecoveryTime()
            if (lastRecovery != null) {
                logger.debug { "Time since last recovery: ${lastRecovery}ms ago" }
            }

            val metrics =
                NetworkMetrics(
                    connected = isConnected,
                    network_type = networkType,
                    estimated_latency_ms = latency,
                    error_rate = errorRate,
                    last_recovery_time_ms = lastRecovery,
                    reachability_check_timestamp = Instant.now().toString(),
                )

            logger.info { "Network connectivity check completed: connected=$isConnected, type=$networkType, latency=${latency}ms" }
            metrics
        } catch (e: Exception) {
            logger.error(e) { "Failed to check network connectivity" }
            null
        }
    }

    override fun estimateNetworkLatency(syncLagMs: Long): Long {
        val estimated = maxOf(10L, syncLagMs / 2)
        logger.debug { "Estimated network latency from sync lag: ${syncLagMs}ms -> ${estimated}ms" }
        return estimated
    }

    override fun calculateErrorRate(
        failureCount: Int,
        totalAttempts: Int,
    ): Double {
        val rate =
            if (totalAttempts == 0) {
                0.0
            } else {
                (failureCount.toDouble() / totalAttempts.toDouble()).coerceIn(0.0, 1.0)
            }
        logger.debug {
            "Calculated error rate: $failureCount failures / $totalAttempts attempts = ${String.format(
                "%.2f%%",
                rate * 100,
            )}"
        }
        return rate
    }

    /**
     * Check if network is available by attempting to resolve a known hostname.
     */
    private fun isNetworkConnected(): Boolean {
        return try {
            logger.debug { "Checking network connectivity by resolving DNS (8.8.8.8)" }
            val address = java.net.InetAddress.getByName("8.8.8.8")
            val connected = address.hostAddress != null
            logger.debug { "DNS resolution result: ${address.hostAddress} (connected: $connected)" }
            connected
        } catch (e: Exception) {
            logger.warn(e) { "DNS resolution failed - network may be unavailable" }
            false
        }
    }

    /**
     * Detect the network type by checking system properties and network interfaces.
     */
    private fun detectNetworkType(): NetworkType {
        if (!isNetworkConnected()) {
            logger.debug { "Network is disconnected - returning DISCONNECTED type" }
            return NetworkType.DISCONNECTED
        }

        return try {
            val osName = System.getProperty("os.name").lowercase()
            logger.debug { "Detecting network type on OS: $osName" }

            val type =
                when {
                    osName.contains("mac") -> {
                        logger.debug { "macOS detected - checking WiFi vs Ethernet" }
                        if (isWiFiConnected()) NetworkType.WIFI else NetworkType.WIRED
                    }
                    osName.contains("linux") -> {
                        logger.debug { "Linux detected - checking WiFi vs Ethernet" }
                        if (isWiFiConnected()) NetworkType.WIFI else NetworkType.WIRED
                    }
                    osName.contains("windows") -> {
                        logger.debug { "Windows detected - checking WiFi vs Ethernet" }
                        if (isWiFiConnected()) NetworkType.WIFI else NetworkType.WIRED
                    }
                    else -> {
                        logger.debug { "Unknown OS - defaulting to UNKNOWN network type" }
                        NetworkType.UNKNOWN
                    }
                }
            logger.debug { "Detected network type: $type" }
            type
        } catch (e: Exception) {
            logger.warn(e) { "Failed to detect network type - defaulting to UNKNOWN" }
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
                    logger.debug { "Checking WiFi connection on macOS using networksetup" }
                    val process = Runtime.getRuntime().exec(arrayOf("networksetup", "-getairportnetwork", "en0"))
                    val result = process.inputStream.bufferedReader().readText()
                    val isConnected = result.isNotEmpty() && !result.contains("off")
                    logger.debug { "macOS WiFi check result: connected=$isConnected (output: ${result.trim()})" }
                    isConnected
                }
                osName.contains("linux") -> {
                    logger.debug { "Checking WiFi connection on Linux by scanning network interfaces" }
                    val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                    val hasWiFi =
                        interfaces.asSequence()
                            .filter { it.name.startsWith("wlan") && it.isUp }
                            .toList()
                    logger.debug { "Linux WiFi interfaces found: ${hasWiFi.map { it.name }}" }
                    hasWiFi.isNotEmpty()
                }
                else -> {
                    logger.debug { "WiFi detection not supported on this OS" }
                    false
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to check WiFi connection status" }
            false
        }
    }

    /**
     * Estimate system latency based on typical ping results.
     */
    private fun estimateLatencyBasedOnSystemMetrics(): Long {
        return try {
            logger.debug { "Estimating latency by pinging 8.8.8.8" }
            val runtime = Runtime.getRuntime()
            val process = runtime.exec(arrayOf("ping", "-c", "1", "-W", "1000", "8.8.8.8"))
            val startTime = System.currentTimeMillis()
            val exitCode = process.waitFor()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            if (exitCode == 0) {
                logger.debug { "Ping successful: ${duration}ms" }
                duration.coerceIn(1L, 5000L)
            } else {
                logger.debug { "Ping failed with exit code $exitCode - using default latency estimate" }
                100L // Default estimate if ping fails
            }
        } catch (e: Exception) {
            logger.debug(e) { "Unable to ping for latency estimation - using default estimate" }
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
        val errorRate = calculateErrorRate(errorCount, attemptCount)
        logger.warn {
            "Network error recorded: errorCount=$errorCount, attemptCount=$attemptCount, " +
                "errorRate=${String.format("%.2f%%", errorRate * 100)}"
        }
    }

    /**
     * Record a successful network attempt for tracking error rate.
     */
    fun recordNetworkSuccess() {
        attemptCount++
        val errorRate = calculateErrorRate(errorCount, attemptCount)
        logger.debug {
            "Network success recorded: errorCount=$errorCount, attemptCount=$attemptCount, " +
                "errorRate=${String.format("%.2f%%", errorRate * 100)}"
        }
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

package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.runtime.DaemonProcessMarker
import wirecli.sync.SyncService
import wirecli.sync.SyncStatusResult
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch

private val logger = KotlinLogging.logger {}

class DaemonCommand(
    private val syncServiceProvider: () -> SyncService,
    private val processMarkerProvider: () -> DaemonProcessMarker,
    private val awaitTermination: () -> Unit = { CountDownLatch(1).await() },
) : CliktCommand(
        name = "daemon",
        help = "Keep Wire message synchronization active and cache messages locally.",
    ) {
    override fun run() {
        when (val result = syncServiceProvider().startContinuousSync()) {
            is SyncStatusResult.Success -> {
                processMarkerProvider().use { marker ->
                    marker.start()
                    marker.recordUpdate()
                    echo("Message sync daemon is active.")
                    startPeriodicHealthLogging()
                    awaitTermination()
                }
            }

            is SyncStatusResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }

    private fun startPeriodicHealthLogging() {
        val syncService = syncServiceProvider()
        val daemonStart = Instant.now()
        var lastLoggedReachable: Boolean? = null

        Thread.ofPlatform().daemon().start {
            while (true) {
                try {
                    Thread.sleep(DAEMON_HEALTH_INTERVAL_MS)
                    lastLoggedReachable = logHealthSnapshot(syncService, daemonStart, lastLoggedReachable)
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: InterruptedException,
                ) {
                    logger.info { "Daemon health logging stopped." }
                    break
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    logger.warn(e) { "Daemon health check error (continuing)" }
                }
            }
        }
    }

    private fun logHealthSnapshot(
        syncService: SyncService,
        daemonStart: Instant,
        lastLoggedReachable: Boolean?,
    ): Boolean? {
        val uptime = Duration.between(daemonStart, Instant.now())
        val statusResult = syncService.getCurrentSyncStatus()
        when (statusResult) {
            is SyncStatusResult.Success -> {
                val m = statusResult.view.metrics
                val statusName = statusResult.view.status.name
                val reachable = m.network?.connected ?: true

                logConnectivityChange(uptime, lastLoggedReachable, reachable, statusName, m)
                logHeartbeat(uptime, statusName, m)
                return reachable
            }

            is SyncStatusResult.Failure -> {
                logger.warn { "Daemon health check failed: ${statusResult.message}" }
                return null
            }
        }
    }

    private fun logConnectivityChange(
        uptime: Duration,
        lastLoggedReachable: Boolean?,
        reachable: Boolean,
        statusName: String,
        m: wirecli.sync.HealthMetrics,
    ) {
        if (lastLoggedReachable != null && lastLoggedReachable && !reachable) {
            logger.warn {
                "Daemon lost network connectivity after ${formatDuration(uptime)} " +
                    "(state=$statusName, lag=${m.lagMs}ms, pending=${m.pendingMessages}, " +
                    "mls=${m.mlsPct}%, latency=${m.network?.estimatedLatencyMs ?: "?"}ms)"
            }
        } else if (lastLoggedReachable != null && !lastLoggedReachable && reachable) {
            logger.info {
                "Daemon recovered network connectivity after ${formatDuration(uptime)} " +
                    "(state=$statusName, lag=${m.lagMs}ms, pending=${m.pendingMessages}, " +
                    "mls=${m.mlsPct}%)"
            }
        }
    }

    private fun logHeartbeat(
        uptime: Duration,
        statusName: String,
        m: wirecli.sync.HealthMetrics,
    ) {
        logger.info {
            "Daemon heartbeat after ${formatDuration(uptime)} — " +
                "state=$statusName, lag=${m.lagMs}ms, pending=${m.pendingMessages}, " +
                "mls=${m.mlsPct}%, net=${m.network?.let { "${it.connected}" } ?: "?"}, " +
                "lat=${m.network?.estimatedLatencyMs ?: "?"}ms"
        }
    }

    private fun formatDuration(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60
        return when {
            hours > 0 -> "${hours}h${minutes}m"
            minutes > 0 -> "${minutes}m${seconds}s"
            else -> "${seconds}s"
        }
    }

    private companion object {
        private const val DAEMON_HEALTH_INTERVAL_MS = 60_000L
    }
}

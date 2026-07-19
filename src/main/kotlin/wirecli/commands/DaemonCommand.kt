package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import wirecli.runtime.DaemonProcessMarker
import wirecli.sync.SyncService
import wirecli.sync.SyncStatusResult
import java.util.concurrent.CountDownLatch

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
                    awaitTermination()
                }
            }

            is SyncStatusResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(processExitCode(result.exitCode))
            }
        }
    }
}

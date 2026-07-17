package wirecli.runtime

import wirecli.message.DaemonStatus
import java.nio.file.Files
import java.nio.file.Path

interface DaemonProcessMarker : DaemonStatus, AutoCloseable {
    fun start()
}

class FileDaemonProcessMarker(
    private val markerPath: Path = DaemonMarkerPath.resolve(),
    private val currentProcessId: Long = ProcessHandle.current().pid(),
    private val processIsAlive: (Long) -> Boolean = { processId ->
        ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false)
    },
) : DaemonProcessMarker {
    override fun start() {
        Files.createDirectories(markerPath.parent)
        Files.writeString(markerPath, currentProcessId.toString())
    }

    override fun isRunning(): Boolean {
        val processId = runCatching { Files.readString(markerPath).trim().toLong() }.getOrNull() ?: return false
        if (processIsAlive(processId)) return true

        Files.deleteIfExists(markerPath)
        return false
    }

    override fun close() {
        val ownedProcessId = runCatching { Files.readString(markerPath).trim().toLong() }.getOrNull()
        if (ownedProcessId == currentProcessId) Files.deleteIfExists(markerPath)
    }
}

object DaemonMarkerPath {
    const val ENV_MARKER_PATH = "WIRECLI_DAEMON_MARKER"

    fun resolve(environment: Map<String, String> = System.getenv()): Path {
        val configured = environment[ENV_MARKER_PATH]
        if (!configured.isNullOrBlank()) return Path.of(configured)

        val home = environment["HOME"] ?: System.getProperty("user.home")
        return Path.of(home, ".wire", "wire-cli-daemon.pid")
    }
}

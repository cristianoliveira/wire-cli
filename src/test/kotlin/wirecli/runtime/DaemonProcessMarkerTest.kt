package wirecli.runtime

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaemonProcessMarkerTest {
    @Test
    fun `started marker reports daemon running and is removed on close`() {
        val markerPath = createTempDirectory("wire-daemon-marker").resolve("daemon.pid")
        val marker = FileDaemonProcessMarker(markerPath, currentProcessId = 42L, processIsAlive = { it == 42L })

        marker.start()

        assertTrue(marker.isRunning())
        marker.close()
        assertFalse(marker.isRunning())
        markerPath.parent.toFile().deleteRecursively()
    }

    @Test
    fun `stale marker is removed and reports daemon stopped`() {
        val markerPath = createTempDirectory("wire-daemon-marker").resolve("daemon.pid")
        markerPath.toFile().writeText("99")
        val marker = FileDaemonProcessMarker(markerPath, currentProcessId = 42L, processIsAlive = { false })

        assertFalse(marker.isRunning())
        assertFalse(markerPath.toFile().exists())
        markerPath.parent.toFile().deleteRecursively()
    }
}

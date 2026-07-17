package wirecli.runtime

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun `recordUpdate writes timestamp and lastUpdateTimestamp returns it`() {
        val markerPath = createTempDirectory("wire-daemon-marker").resolve("daemon.pid")
        val marker = FileDaemonProcessMarker(markerPath, currentProcessId = 42L, processIsAlive = { it == 42L })

        val before = marker.lastUpdateTimestamp()
        assertNull(before, "no update recorded before start")

        marker.recordUpdate()
        val after = marker.lastUpdateTimestamp()
        assertNotNull(after, "timestamp recorded after update")

        markerPath.parent.toFile().deleteRecursively()
    }

    @Test
    fun `lastUpdateTimestamp returns null when marker file does not exist`() {
        val markerPath = createTempDirectory("wire-daemon-marker").resolve("daemon.pid")
        val marker = FileDaemonProcessMarker(markerPath, currentProcessId = 42L, processIsAlive = { it == 42L })

        assertNull(marker.lastUpdateTimestamp())

        markerPath.parent.toFile().deleteRecursively()
    }

    @Test
    fun `recordUpdate overwrites previous timestamp`() {
        val markerPath = createTempDirectory("wire-daemon-marker").resolve("daemon.pid")
        val marker = FileDaemonProcessMarker(markerPath, currentProcessId = 42L, processIsAlive = { it == 42L })

        marker.recordUpdate()
        val first = marker.lastUpdateTimestamp()
        assertNotNull(first)

        Thread.sleep(5)
        marker.recordUpdate()
        val second = marker.lastUpdateTimestamp()
        assertNotNull(second)
        assertTrue(second > first, "second timestamp should be later")

        markerPath.parent.toFile().deleteRecursively()
    }
}

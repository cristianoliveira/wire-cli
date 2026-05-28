package wirecli.runtime

import wirecli.config.KaliumCliMode
import wirecli.config.kaliumCliConfigs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KaliumCliModeTest {
    @Test
    fun `uses safe defaults when env vars are absent`() {
        val mode = KaliumCliMode.fromEnvironment(emptyMap())

        assertFalse(mode.enableCalling)
        assertFalse(mode.disableSessionSyncWait)
        assertFalse(mode.disableMlsMigrationScheduler)
    }

    @Test
    fun `enables flags for truthy env values`() {
        val mode =
            KaliumCliMode.fromEnvironment(
                mapOf(
                    KaliumCliMode.ENV_ENABLE_CALLING to "true",
                    KaliumCliMode.ENV_DISABLE_SESSION_SYNC_WAIT to "1",
                    KaliumCliMode.ENV_DISABLE_MLS_MIGRATION_SCHEDULER to "yes",
                ),
            )

        assertTrue(mode.enableCalling)
        assertTrue(mode.disableSessionSyncWait)
        assertTrue(mode.disableMlsMigrationScheduler)
    }

    @Test
    fun `falls back to defaults for invalid env values`() {
        val mode =
            KaliumCliMode.fromEnvironment(
                mapOf(
                    KaliumCliMode.ENV_ENABLE_CALLING to "sometimes",
                    KaliumCliMode.ENV_DISABLE_SESSION_SYNC_WAIT to "later",
                    KaliumCliMode.ENV_DISABLE_MLS_MIGRATION_SCHEDULER to "not-now",
                ),
            )

        assertFalse(mode.enableCalling)
        assertFalse(mode.disableSessionSyncWait)
        assertFalse(mode.disableMlsMigrationScheduler)
    }

    @Test
    fun `maps cli mode into kalium configs`() {
        val defaults = kaliumCliConfigs()
        val tuned =
            kaliumCliConfigs(
                KaliumCliMode(
                    enableCalling = true,
                    disableMlsMigrationScheduler = true,
                ),
            )

        assertFalse(defaults.enableCalling)
        assertEquals(24, defaults.mlsMigrationInterval.inWholeHours)
        assertTrue(tuned.enableCalling)
        assertEquals(365 * 24, tuned.mlsMigrationInterval.inWholeHours)
    }
}

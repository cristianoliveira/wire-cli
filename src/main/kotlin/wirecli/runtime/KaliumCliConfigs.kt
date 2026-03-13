package wirecli.runtime

import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Kalium configuration for CLI environment.
 * Calling is disabled to prevent eager-loading of AVS (Audio Video Signaling) libraries
 * that may cause startup crashes in CLI environments where those native libraries are unavailable.
 */
internal fun kaliumCliConfigs(): KaliumConfigs {
    return kaliumCliConfigs(KaliumCliMode())
}

internal fun kaliumCliConfigs(mode: KaliumCliMode): KaliumConfigs {
    val migrationInterval =
        if (mode.disableMlsMigrationScheduler) {
            DISABLED_MLS_MIGRATION_INTERVAL
        } else {
            DEFAULT_MLS_MIGRATION_INTERVAL
        }

    return KaliumConfigs(
        enableCalling = mode.enableCalling,
        mlsMigrationInterval = migrationInterval,
    )
}

private val DEFAULT_MLS_MIGRATION_INTERVAL: Duration = 24.hours
private val DISABLED_MLS_MIGRATION_INTERVAL: Duration = 365.days

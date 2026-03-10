package wirecli.runtime

import com.wire.kalium.logic.featureFlags.KaliumConfigs

/**
 * Kalium configuration for CLI environment.
 * Calling is disabled to prevent eager-loading of AVS (Audio Video Signaling) libraries
 * that may cause startup crashes in CLI environments where those native libraries are unavailable.
 */
internal fun kaliumCliConfigs(): KaliumConfigs {
    return KaliumConfigs(enableCalling = false)
}

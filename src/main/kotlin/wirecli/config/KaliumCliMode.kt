package wirecli.config

internal enum class SessionSyncRequirement {
    READ,
    WRITE,
}

internal data class KaliumCliMode(
    val enableCalling: Boolean = false,
    val disableSessionSyncWait: Boolean = false,
    val disableMlsMigrationScheduler: Boolean = false,
) {
    fun shouldAwaitLiveSessionSync(requirement: SessionSyncRequirement): Boolean =
        requirement == SessionSyncRequirement.WRITE && !disableSessionSyncWait

    companion object {
        const val ENV_ENABLE_CALLING = "WIRE_KALIUM_ENABLE_CALLING"
        const val ENV_DISABLE_SESSION_SYNC_WAIT = "WIRE_KALIUM_DISABLE_SESSION_SYNC_WAIT"
        const val ENV_DISABLE_MLS_MIGRATION_SCHEDULER = "WIRE_KALIUM_DISABLE_MLS_MIGRATION_SCHEDULER"

        fun fromEnvironment(environment: Map<String, String>): KaliumCliMode {
            return KaliumCliMode(
                enableCalling = environment.parseBoolean(ENV_ENABLE_CALLING, default = false),
                disableSessionSyncWait = environment.parseBoolean(ENV_DISABLE_SESSION_SYNC_WAIT, default = false),
                disableMlsMigrationScheduler =
                    environment.parseBoolean(ENV_DISABLE_MLS_MIGRATION_SCHEDULER, default = false),
            )
        }

        private fun Map<String, String>.parseBoolean(
            key: String,
            default: Boolean,
        ): Boolean {
            val raw = this[key]?.trim()?.lowercase() ?: return default
            return when (raw) {
                "1", "true", "yes", "on" -> true
                "0", "false", "no", "off" -> false
                else -> default
            }
        }
    }
}

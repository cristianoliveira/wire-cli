package wirecli.auth

object AuthMessages {
    private const val RELOGIN_GUIDANCE = "Run wire login to re-authenticate."

    fun noActiveSession(): String = "No active session. $RELOGIN_GUIDANCE"

    fun noValidSession(invalidSessions: Int): String {
        val noun = if (invalidSessions == 1) "session" else "sessions"
        return "No valid active session. Found $invalidSessions invalid or expired stored $noun. $RELOGIN_GUIDANCE"
    }

    fun unsupportedSessionSchema(version: String): String =
        "Stored session format version '$version' is unsupported. Run wire login to recreate local credentials."

    fun legacySessionMigrationFailed(): String =
        "Stored session format is outdated and migration failed. Run wire login to recreate local credentials."

    fun invalidOrExpiredSession(): String = "Session is invalid or expired. $RELOGIN_GUIDANCE"

    fun invalidCredentials(): String = "Invalid email or password. Verify credentials and try again."

    fun passwordRequired(): String = "Password confirmation required."

    fun networkFailure(action: String): String = "$action failed: network is unreachable. Check your connection and retry."

    fun authServiceUnavailable(): String = "Authentication service is unavailable. Retry later or check server settings."

    fun unauthorizedAction(action: String): String = "$action failed: account is unauthorized or session expired. $RELOGIN_GUIDANCE"

    fun localSessionPersistenceFailed(): String = "Authentication succeeded, but account state could not be persisted. Retry login."

    fun sessionBootstrapFailed(): String = "Authentication succeeded, but session bootstrap failed. Retry login."

    fun clientRegistrationFailed(): String = "Authentication succeeded, but client registration failed. Retry login."
}

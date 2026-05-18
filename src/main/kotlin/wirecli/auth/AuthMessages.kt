package wirecli.auth

object AuthMessages {
    private const val RELOGIN_GUIDANCE = "Run wire login to re-authenticate."
    const val LEGACY_SESSION_MIGRATION_FAILED =
        "Stored session format is outdated and migration failed. Run wire login to recreate local credentials."
    const val INVALID_CREDENTIALS = "Invalid email or password. Verify credentials and try again."
    const val PASSWORD_REQUIRED = "Password confirmation required."
    const val AUTH_SERVICE_UNAVAILABLE =
        "Authentication service is unavailable. Retry later or check server settings."
    const val LOCAL_SESSION_PERSISTENCE_FAILED =
        "Authentication succeeded, but account state could not be persisted. Retry login."
    const val SESSION_BOOTSTRAP_FAILED = "Authentication succeeded, but session bootstrap failed. Retry login."
    const val CLIENT_REGISTRATION_FAILED =
        "Authentication succeeded, but client registration failed. Retry login."

    fun noActiveSession(): String = "No active session. $RELOGIN_GUIDANCE"

    fun noValidSession(invalidSessions: Int): String {
        val noun = if (invalidSessions == 1) "session" else "sessions"
        return "No valid active session. Found $invalidSessions invalid or expired stored $noun. $RELOGIN_GUIDANCE"
    }

    fun unsupportedSessionSchema(version: String): String =
        "Stored session format version '$version' is unsupported. Run wire login to recreate local credentials."

    fun invalidOrExpiredSession(): String = "Session is invalid or expired. $RELOGIN_GUIDANCE"

    fun networkFailure(action: String): String {
        return "$action failed: network is unreachable. Check your connection and retry."
    }

    fun unauthorizedAction(action: String): String {
        return "$action failed: account is unauthorized or session expired. $RELOGIN_GUIDANCE"
    }
}

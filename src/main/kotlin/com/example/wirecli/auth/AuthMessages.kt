package com.example.wirecli.auth

object AuthMessages {
    private const val RELOGIN_GUIDANCE = "Run wire login to re-authenticate."

    fun noActiveSession(): String = "No active session. $RELOGIN_GUIDANCE"

    fun noValidSession(invalidSessions: Int): String {
        val noun = if (invalidSessions == 1) "session" else "sessions"
        return "No valid active session. Found $invalidSessions invalid or expired stored $noun. $RELOGIN_GUIDANCE"
    }

    fun invalidOrExpiredSession(): String = "Session is invalid or expired. $RELOGIN_GUIDANCE"
}

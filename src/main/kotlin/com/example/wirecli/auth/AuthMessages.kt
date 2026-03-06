package com.example.wirecli.auth

object AuthMessages {
    private const val RELOGIN_GUIDANCE = "Run wire login to re-authenticate."

    fun noActiveSession(): String = "No active session. $RELOGIN_GUIDANCE"

    fun invalidOrExpiredSession(): String = "Session is invalid or expired. $RELOGIN_GUIDANCE"
}

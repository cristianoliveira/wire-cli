package com.example.wirecli.auth

import java.io.File

class FileAuthSessionStore(
    // File format: plain text with three lines: userId, accessToken, server (optional).
    // Empty server line is allowed. Lines are trimmed; blank lines are ignored.
    private val sessionFile: File = defaultSessionFile()
) : AuthSessionStore {
    override fun readActiveSession(): AuthSession? {
        if (!sessionFile.exists()) {
            return null
        }

        val lines = sessionFile.readLines()
        if (lines.size < 2) {
            return null
        }

        val userId = lines[0]
        val accessToken = lines[1]
        val server = lines.getOrNull(2)?.ifBlank { null }

        if (userId.isBlank() || accessToken.isBlank()) {
            return null
        }

        return AuthSession(userId = userId, accessToken = accessToken, server = server)
    }

    override fun writeActiveSession(session: AuthSession) {
        sessionFile.parentFile?.mkdirs()
        val serverLine = session.server.orEmpty()
        sessionFile.writeText("${session.userId}\n${session.accessToken}\n${serverLine}\n")
    }

    override fun clearActiveSession() {
        if (sessionFile.exists()) {
            sessionFile.delete()
        }
    }
}

private fun defaultSessionFile(): File {
    val explicitFile = System.getenv("WIRE_SESSION_FILE")
    if (!explicitFile.isNullOrBlank()) {
        return File(explicitFile)
    }

    val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")
    if (!xdgConfigHome.isNullOrBlank()) {
        return File(xdgConfigHome, "wire/session")
    }

    val home = System.getenv("HOME") ?: "."
    return File(home, ".config/wire/session")
}

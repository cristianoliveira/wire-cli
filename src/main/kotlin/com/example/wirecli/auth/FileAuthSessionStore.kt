package com.example.wirecli.auth

import java.io.File

class FileAuthSessionStore(
    // File format: plain text session records grouped by 3 lines:
    // userId, accessToken, server (optional).
    // Multiple records are allowed to support inventory diagnostics.
    private val sessionFile: File = defaultSessionFile()
) : AuthSessionStore {
    private val LINES_PER_RECORD = 3
    override fun readActiveSession(): AuthSession? {
        return readSessionInventory().activeSession
    }

    override fun readSessionInventory(): SessionInventory {
        if (!sessionFile.exists()) {
            return SessionInventory(activeSession = null, validSessions = 0, invalidSessions = 0)
        }

        val lines = sessionFile.readLines()
        val candidates = mutableListOf<AuthSession>()
        var invalidSessions = 0

        var index = 0
        while (index < lines.size) {
            val userId = lines.getOrNull(index)
            val accessToken = lines.getOrNull(index + 1)
            val server = lines.getOrNull(index + 2)?.ifBlank { null }

            if (userId == null || accessToken == null) {
                invalidSessions += 1
                break
            }

            if (userId.isBlank() || accessToken.isBlank()) {
                invalidSessions += 1
            } else {
                candidates += AuthSession(userId = userId, accessToken = accessToken, server = server)
            }

            index += LINES_PER_RECORD
        }

        val activeSession = candidates
            .sortedWith(compareBy<AuthSession> { it.userId }.thenBy { it.server.orEmpty() }.thenBy { it.accessToken })
            .firstOrNull()

        return SessionInventory(
            activeSession = activeSession,
            validSessions = candidates.size,
            invalidSessions = invalidSessions
        )
    }

    override fun writeActiveSession(session: AuthSession) {
        sessionFile.parentFile?.mkdirs()
        val serverLine = session.server.orEmpty()
        sessionFile.writeText("${session.userId}\n${session.accessToken}\n${serverLine}\n")
    }

    override fun clearActiveSession() {
        if (sessionFile.exists() && !sessionFile.delete()) {
            throw IllegalStateException("Failed to clear active session file: ${sessionFile.absolutePath}")
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

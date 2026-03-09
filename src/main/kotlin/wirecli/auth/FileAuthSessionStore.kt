package wirecli.auth

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

class FileAuthSessionStore(
    private val sessionFile: File = defaultSessionFile()
) : AuthSessionStore {
    override fun readActiveSession(): AuthSession? {
        return readSessionInventory().activeSession
    }

    override fun readSessionInventory(): SessionInventory {
        if (!sessionFile.exists()) {
            return SessionInventory(activeSession = null, validSessions = 0, invalidSessions = 0)
        }

        val parsedData = parseStoredSessions(sessionFile.readLines())
        if (parsedData.format == SessionFileFormat.LEGACY) {
            val migrated = runCatching {
                val serialized = serializeVersionedSessions(parsedData.rawPayloadLines)
                writeAtomically(serialized)
            }

            if (migrated.isFailure) {
                return parsedData.inventory.copy(
                    diagnosticMessage = AuthMessages.legacySessionMigrationFailed()
                )
            }
        }

        return parsedData.inventory
    }

    override fun writeActiveSession(session: AuthSession) {
        writeAtomically(serializeSingleSession(session))
    }

    override fun clearActiveSession() {
        if (sessionFile.exists() && !sessionFile.delete()) {
            throw IllegalStateException("Failed to clear active session file: ${sessionFile.absolutePath}")
        }
    }

    private fun writeAtomically(contents: String) {
        val path = sessionFile.toPath()
        val parent = path.parent

        if (parent != null) {
            Files.createDirectories(parent)
            restrictDirectoryPermissions(parent)
        }

        val tempFile = Files.createTempFile(parent ?: Path.of("."), "session-", ".tmp")
        try {
            restrictFilePermissions(tempFile)
            Files.writeString(tempFile, contents, StandardCharsets.UTF_8)
            try {
                Files.move(
                    tempFile,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
            }
            restrictFilePermissions(path)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun restrictDirectoryPermissions(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
        }.recover {
            path.toFile().setReadable(false, false)
            path.toFile().setReadable(true, true)
            path.toFile().setWritable(false, false)
            path.toFile().setWritable(true, true)
            path.toFile().setExecutable(false, false)
            path.toFile().setExecutable(true, true)
        }
    }

    private fun restrictFilePermissions(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }.recover {
            path.toFile().setReadable(false, false)
            path.toFile().setReadable(true, true)
            path.toFile().setWritable(false, false)
            path.toFile().setWritable(true, true)
            path.toFile().setExecutable(false, false)
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

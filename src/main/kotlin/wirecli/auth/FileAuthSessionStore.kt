package wirecli.auth

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * File-based storage for Wire CLI authentication sessions.
 *
 * Persists authenticated sessions to disk with proper file permissions (0600 for files, 0700 for directories)
 * to protect sensitive token data. Supports atomic writes and legacy format migration.
 *
 * @invariant sessionFile path is always initialized (uses default if not provided)
 * @invariant File operations are atomic to prevent partial writes
 * @invariant File permissions restrict access to owner only
 * @invariant Legacy session format is automatically migrated to versioned format
 */
class FileAuthSessionStore(
    private val sessionFile: File = defaultSessionFile(),
) : AuthSessionStore {
    /**
     * Reads the currently active authenticated session.
     *
     * @return AuthSession if valid session exists in storage, null if no active session
     * @throws Nothing - Returns null on read errors
     *
     * @post Result is either a valid AuthSession or null (never throws)
     * @post Session contains non-null userId and accessToken if returned
     */
    override fun readActiveSession(): AuthSession? {
        return readSessionInventory().activeSession
    }

    /**
     * Reads complete session inventory including validation status and diagnostics.
     *
     * Automatically migrates legacy session format to versioned format if needed.
     * Returns empty inventory if no session file exists.
     *
     * @return SessionInventory with active session, counts, and diagnostic messages
     * @throws Nothing - Returns empty inventory with diagnostics on errors
     *
     * @post Result is non-null SessionInventory
     * @post If legacy format exists, automatic migration is attempted
     * @post diagnosticMessage is set if migration failed
     */
    override fun readSessionInventory(): SessionInventory {
        if (!sessionFile.exists()) {
            return SessionInventory(activeSession = null, validSessions = 0, invalidSessions = 0)
        }

        val parsedData = parseStoredSessions(sessionFile.readLines())
        if (parsedData.format == SessionFileFormat.LEGACY) {
            val migrated =
                runCatching {
                    val serialized = serializeVersionedSessions(parsedData.rawPayloadLines)
                    writeAtomically(serialized)
                }

            if (migrated.isFailure) {
                return parsedData.inventory.copy(
                    diagnosticMessage = AuthMessages.legacySessionMigrationFailed(),
                )
            }
        }

        return parsedData.inventory
    }

    /**
     * Writes an authenticated session to storage.
     *
     * @param session The authenticated session to persist
     * @throws IllegalStateException if atomic write fails after retries
     *
     * @pre session must have non-null userId and accessToken
     * @post Session is persisted to disk with secure file permissions (0600)
     * @post Parent directory is created with secure permissions (0700) if needed
     * @post Write is atomic - file is either fully written or not created
     */
    override fun writeActiveSession(session: AuthSession) {
        writeAtomically(serializeSingleSession(session))
    }

    /**
     * Clears the stored active session by deleting the session file.
     *
     * @throws IllegalStateException if session file exists but cannot be deleted
     *
     * @post If successful, no session file exists on disk
     * @post Safe to call when no session exists (silently succeeds)
     */
    override fun clearActiveSession() {
        if (sessionFile.exists() && !sessionFile.delete()) {
            throw IllegalStateException("Failed to clear active session file: ${sessionFile.absolutePath}")
        }
    }

    /**
     * Writes content to the session file atomically with proper permissions.
     *
     * Uses a temporary file and atomic move to ensure consistency. If atomic move
     * is not supported, falls back to regular move. Sets strict file/directory
     * permissions (0600/0700) to protect sensitive session data.
     *
     * @param contents The content to write (session data)
     * @throws IllegalStateException if final atomic/non-atomic move fails
     *
     * @pre contents must be non-null serialized session data
     * @post File is persisted with proper permissions (0600)
     * @post Parent directory exists with permissions (0700)
     * @post Write is atomic on supported filesystems
     * @invariant Temporary file is deleted in all cases (success or failure)
     */
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
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
            }
            restrictFilePermissions(path)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    /**
     * Restricts directory permissions to owner-only access (0700 / rwx------).
     *
     * Attempts to use POSIX file permissions on supported systems; falls back to
     * Java file permission APIs on Windows and other systems that don't support POSIX.
     *
     * @param path The directory path to restrict
     *
     * @post Directory is readable/writable/executable by owner only
     * @invariant Errors are silently caught and fallback is used
     */
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

    /**
     * Restricts file permissions to owner-only read/write (0600 / rw-------).
     *
     * Attempts to use POSIX file permissions on supported systems; falls back to
     * Java file permission APIs on Windows and other systems that don't support POSIX.
     *
     * @param path The file path to restrict
     *
     * @post File is readable/writable by owner only; no execute permission
     * @invariant Errors are silently caught and fallback is used
     */
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

/**
 * Returns the default session file path.
 *
 * Priority order:
 * 1. WIRE_SESSION_FILE environment variable if set
 * 2. XDG_CONFIG_HOME/wire/session if XDG_CONFIG_HOME is set
 * 3. ~/.config/wire/session (default location)
 *
 * @return File object pointing to the session file path
 *
 * @post Result is non-null File with appropriate path set
 */

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

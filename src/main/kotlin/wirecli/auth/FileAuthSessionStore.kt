package wirecli.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

private val logger = KotlinLogging.logger {}

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
        check(sessionFile.path.isNotBlank()) { "Session file path must not be blank." }
        logger.debug { "Reading active session from store" }
        val session = readSessionInventory().activeSession
        if (session != null) {
            logger.debug { "Active session found for userId: ${session.userId}" }
        } else {
            logger.debug { "No active session found in store" }
        }
        check(session == null || session.userId.isNotBlank()) {
            "Stored active session must include a non-blank user ID."
        }
        check(session == null || session.accessToken.isNotBlank()) {
            "Stored active session must include a non-blank access token."
        }
        return session
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
        check(sessionFile.path.isNotBlank()) { "Session inventory file path must not be blank." }
        logger.debug { "Reading session inventory from: ${sessionFile.absolutePath}" }

        if (!sessionFile.exists()) {
            logger.debug { "Session file does not exist: ${sessionFile.absolutePath}" }
            return SessionInventory(activeSession = null, validSessions = 0, invalidSessions = 0)
        }

        val parsedData = parseSessionFile() ?: return emptyInventoryWithError()

        val inventory = handleLegacyFormatMigration(parsedData)
        validateInventory(inventory)

        logger.debug {
            "Session inventory loaded: " +
                "active=${inventory.activeSession != null}, " +
                "valid=${inventory.validSessions}, " +
                "invalid=${inventory.invalidSessions}"
        }

        return inventory
    }

    /**
     * Parses the session file contents, handling read and parse errors gracefully.
     *
     * @return ParsedSessionData if successful, null if an error occurred
     */
    private fun parseSessionFile(): ParsedSessionData? {
        logger.debug { "Session file found, parsing contents" }
        return try {
            parseStoredSessions(sessionFile.readLines())
        } catch (e: IOException) {
            logger.error(e) { "Error reading session file: ${sessionFile.absolutePath}" }
            null
        } catch (e: IllegalStateException) {
            logger.error(e) { "Error parsing session file: ${sessionFile.absolutePath}" }
            null
        }
    }

    /**
     * Returns an empty session inventory with an error diagnostic message.
     *
     * @return Empty SessionInventory with failure diagnostic
     */
    private fun emptyInventoryWithError(): SessionInventory =
        SessionInventory(
            activeSession = null,
            validSessions = 0,
            invalidSessions = 0,
            diagnosticMessage = "Failed to read or parse session file",
        )

    /**
     * Handles migration of legacy session format to versioned format if needed.
     *
     * @param parsedData The parsed session data (may be in legacy format)
     * @return Updated inventory after migration (if any)
     */
    private fun handleLegacyFormatMigration(parsedData: ParsedSessionData): SessionInventory {
        logger.debug { "Parsed session format: ${parsedData.format}" }

        if (parsedData.format == SessionFileFormat.LEGACY) {
            logger.info { "Legacy session format detected - attempting migration" }
            val migrated =
                runCatching {
                    val serialized = serializeVersionedSessions(parsedData.rawPayloadLines)
                    writeAtomically(serialized)
                    logger.debug { "Legacy session migration completed successfully" }
                }

            if (migrated.isFailure) {
                logger.warn { "Legacy session migration failed: ${migrated.exceptionOrNull()?.message}" }
                return parsedData.inventory.copy(
                    diagnosticMessage = AuthMessages.LEGACY_SESSION_MIGRATION_FAILED,
                )
            }
        }

        return parsedData.inventory
    }

    /**
     * Validates the inventory data for consistency.
     *
     * @param inventory The session inventory to validate
     * @throws AssertionError if validation fails
     */
    private fun validateInventory(inventory: SessionInventory) {
        check(inventory.validSessions >= 0) {
            "Session inventory valid session count must be non-negative."
        }
        check(inventory.invalidSessions >= 0) {
            "Session inventory invalid session count must be non-negative."
        }
        check(inventory.activeSession == null || inventory.activeSession.userId.isNotBlank()) {
            "Session inventory active session must include a non-blank user ID."
        }
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
        require(session.userId.isNotBlank()) { "Session user ID must not be blank when persisting." }
        require(session.accessToken.isNotBlank()) { "Session access token must not be blank when persisting." }

        logger.debug { "Persisting session to file: ${sessionFile.absolutePath}" }
        logger.debug { "Session userId: ${session.userId}, server: ${session.server}" }
        try {
            writeAtomically(serializeSingleSession(session))
            check(sessionFile.exists()) {
                "Session file must exist after successful session persistence."
            }
            logger.info { "Session persisted successfully to ${sessionFile.absolutePath}" }
        } catch (e: IllegalStateException) {
            logger.error(e) { "Session write validation failed" }
            throw e
        } catch (e: IOException) {
            logger.error(e) { "Failed to write session atomically to ${sessionFile.absolutePath}" }
            throw e
        }
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
        check(sessionFile.path.isNotBlank()) { "Session file path must not be blank when clearing." }
        logger.debug { "Clearing active session from: ${sessionFile.absolutePath}" }
        if (sessionFile.exists()) {
            if (!sessionFile.delete()) {
                logger.error { "Failed to delete session file: ${sessionFile.absolutePath}" }
                throw IllegalStateException("Failed to clear active session file: ${sessionFile.absolutePath}")
            }
            logger.info { "Session file deleted successfully" }
        } else {
            logger.debug { "No session file to delete" }
        }
        check(!sessionFile.exists()) {
            "Session file must not exist after clearActiveSession completes."
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

        logger.debug { "Atomic write starting to: $path" }

        if (parent != null) {
            logger.debug { "Creating/verifying parent directory: $parent with permissions 0700" }
            Files.createDirectories(parent)
            restrictDirectoryPermissions(parent)
        }

        logger.debug { "Creating temporary file for atomic write" }
        val tempFile = Files.createTempFile(parent ?: Path.of("."), "session-", ".tmp")
        try {
            logger.debug { "Setting restricted permissions on temporary file: 0600" }
            restrictFilePermissions(tempFile)

            logger.debug { "Writing session content to temporary file (${contents.length} bytes)" }
            Files.writeString(tempFile, contents, StandardCharsets.UTF_8)

            logger.debug { "Attempting atomic move from $tempFile to $path" }
            try {
                Files.move(
                    tempFile,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
                logger.debug { "Atomic move succeeded" }
            } catch (e: AtomicMoveNotSupportedException) {
                logger.warn { "Atomic move not supported - falling back to non-atomic move: ${e.message}" }
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING)
            }

            logger.debug { "Setting final file permissions: 0600" }
            restrictFilePermissions(path)
            logger.info { "Atomic write completed successfully" }
        } catch (e: IOException) {
            logger.error(e) { "Atomic write failed" }
            throw e
        } finally {
            logger.debug { "Cleaning up temporary file: $tempFile" }
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
            logger.debug { "Setting POSIX directory permissions (0700) on: $path" }
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
        }.recover {
            logger.debug { "POSIX permissions not supported - using fallback for: $path" }
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
            logger.debug { "Setting POSIX file permissions (0600) on: $path" }
            Files.setPosixFilePermissions(path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }.recover {
            logger.debug { "POSIX permissions not supported - using fallback for: $path" }
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

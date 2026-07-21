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
 * File-based, multi-account storage for Wire CLI authentication sessions.
 *
 * Persists one or more authenticated accounts to disk with owner-only file
 * permissions (0600 for files, 0700 for directories) to protect token data.
 * The active account is an explicit pointer (v2 schema), so switching accounts
 * is a local file operation. Supports atomic writes and v1/legacy migration.
 *
 * @invariant sessionFile path is always initialized (uses default if not provided)
 * @invariant File operations are atomic to prevent partial writes
 * @invariant File permissions restrict access to owner only
 * @invariant Legacy/v1 session format is automatically migrated to v2 on read
 */
class FileAuthSessionStore(
    private val sessionFile: File = defaultSessionFile(),
) : AuthSessionStore {
    /**
     * Reads the currently active authenticated session.
     *
     * @return AuthSession if an active account exists in storage, null otherwise
     * @throws Nothing - Returns null on read errors
     *
     * @post Result is either a valid AuthSession or null (never throws)
     * @post Session contains non-blank userId and accessToken if returned
     */
    override fun readActiveSession(): AuthSession? {
        check(sessionFile.path.isNotBlank()) { "Session file path must not be blank." }
        logger.debug { "Reading active session from store" }
        val session = readAccounts().activeAccount
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
     * Reads the full account inventory including validation status and diagnostics.
     *
     * Automatically migrates v1/legacy session format to v2 if needed.
     * Returns an empty inventory if no session file exists.
     *
     * @return AccountInventory with accounts, active pointer, and diagnostics
     * @throws Nothing - Returns an inventory with diagnostics on errors
     *
     * @post Result is non-null AccountInventory
     * @post If v1/legacy format exists, automatic migration is attempted
     * @post diagnosticMessage is set if migration failed or the format is unsupported
     */
    override fun readAccounts(): AccountInventory {
        check(sessionFile.path.isNotBlank()) { "Session inventory file path must not be blank." }
        logger.debug { "Reading account inventory from: ${sessionFile.absolutePath}" }

        val inventory =
            if (!sessionFile.exists()) {
                logger.debug { "Session file does not exist: ${sessionFile.absolutePath}" }
                AccountInventory(accounts = emptyList(), activeUserId = null)
            } else {
                val parsedData = parseSessionFile()
                if (parsedData == null) {
                    emptyInventoryWithError()
                } else {
                    handleFormatMigration(parsedData).also(::validateInventory)
                }
            }

        logger.debug {
            "Account inventory loaded: " +
                "accounts=${inventory.accounts.size}, " +
                "active=${inventory.activeUserId != null}, " +
                "invalid=${inventory.invalidAccounts}"
        }

        return inventory
    }

    /**
     * Adds (or replaces) an account. When [makeActive] is true the new account
     * becomes the active one. Existing accounts are preserved.
     *
     * @param account The authenticated account to persist
     * @param makeActive Whether to mark this account active (default true)
     * @throws IllegalArgumentException if userId or accessToken is blank
     *
     * @pre account must have non-blank userId and accessToken
     * @post Account is persisted to disk with secure file permissions (0600)
     * @post Other accounts are preserved
     */
    override fun addAccount(
        account: AuthSession,
        makeActive: Boolean,
    ) {
        require(account.userId.isNotBlank()) { "Account user ID must not be blank when persisting." }
        require(account.accessToken.isNotBlank()) { "Account access token must not be blank when persisting." }

        logger.debug { "Adding account to file: ${sessionFile.absolutePath}, userId=${account.userId}, makeActive=$makeActive" }
        val current = readAccounts()
        val others = current.accounts.filterNot { it.userId == account.userId }
        val updated =
            AccountInventory(
                accounts = others + account,
                activeUserId = if (makeActive) account.userId else current.activeUserId,
            )
        writeAccounts(updated)
        logger.info { "Account persisted for userId: ${account.userId}" }
    }

    /**
     * Switches the active account by userId. Local-only: never contacts Wire.
     *
     * @return The activated account, or null if no account matches [userId]
     *
     * @post If successful, [userId] is the active pointer and other accounts are unchanged
     */
    override fun setActiveAccount(userId: String): AuthSession? {
        logger.debug { "Setting active account to: $userId" }
        val current = readAccounts()
        val target = current.accounts.firstOrNull { it.userId == userId } ?: return null
        writeAccounts(current.copy(activeUserId = target.userId))
        logger.info { "Active account switched to: ${target.userId}" }
        return target
    }

    /**
     * Removes a single account by userId. Local-only. If the removed account was
     * active, the active pointer is cleared (no account becomes active automatically).
     *
     * @return The removed account, or null if no account matches [userId]
     *
     * @post Other accounts and the file are preserved
     * @post If the active account is removed, active pointer becomes null
     */
    override fun removeAccount(userId: String): AuthSession? {
        logger.debug { "Removing account: $userId" }
        val current = readAccounts()
        val target = current.accounts.firstOrNull { it.userId == userId } ?: return null
        val remaining = current.accounts.filterNot { it.userId == userId }
        val activeUserId = if (current.activeUserId == userId) null else current.activeUserId
        writeAccounts(current.copy(accounts = remaining, activeUserId = activeUserId))
        logger.info { "Account removed: ${target.userId}" }
        return target
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
     * Returns an empty account inventory with an error diagnostic message.
     */
    private fun emptyInventoryWithError(): AccountInventory =
        AccountInventory(
            accounts = emptyList(),
            activeUserId = null,
            diagnosticMessage = "Failed to read or parse session file",
        )

    /**
     * Migrates v1/legacy session format to v2 if needed by rewriting the file.
     * v2 and unsupported formats are returned unchanged.
     */
    private fun handleFormatMigration(parsedData: ParsedSessionData): AccountInventory {
        logger.debug { "Parsed session format: ${parsedData.format}" }

        if (parsedData.format != SessionFileFormat.VERSION_1 && parsedData.format != SessionFileFormat.LEGACY) {
            return parsedData.inventory
        }

        logger.info { "Outdated session format detected - migrating to v2" }
        val migrated =
            runCatching {
                writeAtomically(serializeAccounts(parsedData.inventory))
                logger.debug { "Session migration to v2 completed successfully" }
            }

        return if (migrated.isFailure) {
            logger.warn { "Session migration failed: ${migrated.exceptionOrNull()?.message}" }
            parsedData.inventory.copy(diagnosticMessage = AuthMessages.LEGACY_SESSION_MIGRATION_FAILED)
        } else {
            parsedData.inventory
        }
    }

    /**
     * Validates the inventory data for consistency.
     */
    private fun validateInventory(inventory: AccountInventory) {
        check(inventory.accounts.size + inventory.invalidAccounts >= 0) {
            "Session inventory counts must be non-negative."
        }
        check(inventory.invalidAccounts >= 0) {
            "Session inventory invalid account count must be non-negative."
        }
        val active = inventory.activeAccount
        check(active == null || active.userId.isNotBlank()) {
            "Active account must include a non-blank user ID."
        }
    }

    /**
     * Writes an account inventory to storage atomically with secure permissions.
     *
     * @throws IllegalStateException if the file does not exist after writing
     * @throws IOException if the atomic write fails
     *
     * @post Inventory is persisted to disk with secure file permissions (0600)
     * @post Parent directory is created with secure permissions (0700) if needed
     * @post Write is atomic - file is either fully written or not updated
     */
    private fun writeAccounts(inventory: AccountInventory) {
        writeAtomically(serializeAccounts(inventory))
        check(sessionFile.exists()) {
            "Session file must exist after successful account persistence."
        }
    }

    /**
     * Writes content to the session file atomically with proper permissions.
     *
     * Uses a temporary file and atomic move to ensure consistency. If atomic move
     * is not supported, falls back to regular move. Sets strict file/directory
     * permissions (0600/0700) to protect sensitive session data.
     *
     * @param contents The content to write (serialized session data)
     * @throws IOException if the write or final move fails
     *
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
 */
private fun defaultSessionFile(): File {
    val explicitFile = System.getenv("WIRE_SESSION_FILE")
    val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")
    return when {
        !explicitFile.isNullOrBlank() -> File(explicitFile)
        !xdgConfigHome.isNullOrBlank() -> File(xdgConfigHome, "wire/session")
        else -> File(System.getenv("HOME") ?: ".", ".config/wire/session")
    }
}

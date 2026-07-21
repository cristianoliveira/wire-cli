package wirecli.auth

internal const val SESSION_SCHEMA_PREFIX = "wire-cli-session-store:"
internal const val SESSION_SCHEMA_VERSION_1 = "1"
internal const val SESSION_SCHEMA_VERSION_2 = "2"
internal const val SESSION_SCHEMA_HEADER_V2 = "$SESSION_SCHEMA_PREFIX$SESSION_SCHEMA_VERSION_2"
private const val ACTIVE_MARKER = "active:"
private const val LINES_PER_RECORD = 3

internal enum class SessionFileFormat {
    VERSION_2,
    VERSION_1,
    LEGACY,
    UNSUPPORTED_VERSION,
}

internal data class ParsedSessionData(
    val inventory: AccountInventory,
    val format: SessionFileFormat,
)

/**
 * Parses stored sessions from [lines], accepting v2, v1, and legacy (pre-schema)
 * formats. v1 and legacy files have no explicit active pointer; their active
 * account is derived as the lexicographically-first record so migration is
 * lossless and deterministic.
 */
internal fun parseStoredSessions(lines: List<String>): ParsedSessionData {
    if (lines.isEmpty()) {
        return ParsedSessionData(
            inventory = AccountInventory(accounts = emptyList(), activeUserId = null),
            format = SessionFileFormat.VERSION_2,
        )
    }

    val firstLine = lines.first()
    return if (firstLine.startsWith(SESSION_SCHEMA_PREFIX)) {
        when (val version = firstLine.removePrefix(SESSION_SCHEMA_PREFIX)) {
            SESSION_SCHEMA_VERSION_2 -> ParsedSessionData(parseVersion2(lines), SessionFileFormat.VERSION_2)
            SESSION_SCHEMA_VERSION_1 ->
                ParsedSessionData(
                    inventory = withActiveSortedFirst(parseRecords(lines.drop(1))),
                    format = SessionFileFormat.VERSION_1,
                )
            else -> ParsedSessionData(unsupportedVersion(version), SessionFileFormat.UNSUPPORTED_VERSION)
        }
    } else {
        ParsedSessionData(
            inventory = withActiveSortedFirst(parseRecords(lines)),
            format = SessionFileFormat.LEGACY,
        )
    }
}

/**
 * Serializes an [AccountInventory] to the v2 line format.
 *
 * Layout: schema header, an `active:` pointer line, then 3-line records
 * (userId, accessToken, server). Always terminates with a newline.
 */
internal fun serializeAccounts(inventory: AccountInventory): String {
    val lines = mutableListOf(SESSION_SCHEMA_HEADER_V2)
    lines += "$ACTIVE_MARKER ${inventory.activeUserId.orEmpty()}"
    inventory.accounts.forEach { account ->
        lines += account.userId
        lines += account.accessToken
        lines += account.server.orEmpty()
    }
    return lines.joinToString(separator = "\n") + "\n"
}

private fun parseVersion2(lines: List<String>): AccountInventory {
    val activeRaw = lines.getOrNull(1)?.trim().orEmpty()
    val activeUserId =
        activeRaw
            .removePrefix("$ACTIVE_MARKER ")
            .removePrefix(ACTIVE_MARKER)
            .trim()
            .ifBlank { null }

    val (accounts, invalid) = parseRecords(lines.drop(2))
    return AccountInventory(accounts = accounts, activeUserId = activeUserId, invalidAccounts = invalid)
}

/**
 * Parses positional 3-line records into accounts plus an invalid-record count.
 * A trailing partial record or a blank userId/accessToken counts as invalid.
 */
private fun parseRecords(lines: List<String>): Pair<List<AuthSession>, Int> {
    val accounts = mutableListOf<AuthSession>()
    var invalid = 0
    var index = 0
    while (index < lines.size) {
        val userId = lines.getOrNull(index)
        val accessToken = lines.getOrNull(index + 1)
        val server = lines.getOrNull(index + 2)?.ifBlank { null }

        if (userId == null || accessToken == null) {
            invalid += 1
            break
        }
        if (userId.isBlank() || accessToken.isBlank()) {
            invalid += 1
        } else {
            accounts += AuthSession(userId = userId, accessToken = accessToken, server = server)
        }
        index += LINES_PER_RECORD
    }
    return accounts to invalid
}

/**
 * For v1/legacy inputs there is no active pointer, so the active account is the
 * lexicographically-first record. This preserves the historical selection rule
 * during migration to v2.
 */
private fun withActiveSortedFirst(parsed: Pair<List<AuthSession>, Int>): AccountInventory {
    val (accounts, invalid) = parsed
    val activeUserId =
        accounts
            .sortedWith(compareBy({ it.userId }, { it.server.orEmpty() }, { it.accessToken }))
            .firstOrNull()
            ?.userId
    return AccountInventory(accounts = accounts, activeUserId = activeUserId, invalidAccounts = invalid)
}

private fun unsupportedVersion(version: String): AccountInventory =
    AccountInventory(
        accounts = emptyList(),
        activeUserId = null,
        invalidAccounts = 1,
        diagnosticMessage = AuthMessages.unsupportedSessionSchema(version),
    )

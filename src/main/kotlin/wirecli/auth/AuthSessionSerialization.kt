package wirecli.auth

internal const val SESSION_SCHEMA_PREFIX = "wire-cli-session-store:"
internal const val SESSION_SCHEMA_VERSION_1 = "1"
internal const val SESSION_SCHEMA_VERSION_2 = "2"
internal const val SESSION_SCHEMA_VERSION_3 = "3"
internal const val SESSION_SCHEMA_HEADER_V3 = "$SESSION_SCHEMA_PREFIX$SESSION_SCHEMA_VERSION_3"
internal const val SESSION_SCHEMA_HEADER_LATEST = SESSION_SCHEMA_HEADER_V3
private const val ACTIVE_MARKER = "active:"
private const val LINES_PER_RECORD_V3 = 4
private const val LINES_PER_RECORD_LEGACY = 3

internal enum class SessionFileFormat {
    VERSION_3,
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
 * Parses stored sessions from [lines], accepting v3, v2, v1, and legacy
 * (pre-schema) formats. Older formats carry no label; their active account is
 * derived as the lexicographically-first record so migration is lossless and
 * deterministic.
 */
internal fun parseStoredSessions(lines: List<String>): ParsedSessionData {
    if (lines.isEmpty()) {
        return ParsedSessionData(
            inventory = AccountInventory(accounts = emptyList(), activeUserId = null),
            format = SessionFileFormat.VERSION_3,
        )
    }

    val firstLine = lines.first()
    return if (firstLine.startsWith(SESSION_SCHEMA_PREFIX)) {
        when (val version = firstLine.removePrefix(SESSION_SCHEMA_PREFIX)) {
            SESSION_SCHEMA_VERSION_3 -> ParsedSessionData(parseVersion3(lines), SessionFileFormat.VERSION_3)
            SESSION_SCHEMA_VERSION_2 ->
                ParsedSessionData(
                    inventory = parseVersion2(lines),
                    format = SessionFileFormat.VERSION_2,
                )
            SESSION_SCHEMA_VERSION_1 ->
                ParsedSessionData(
                    inventory = withActiveSortedFirst(parseRecordsLegacy(lines.drop(1))),
                    format = SessionFileFormat.VERSION_1,
                )
            else -> ParsedSessionData(unsupportedVersion(version), SessionFileFormat.UNSUPPORTED_VERSION)
        }
    } else {
        ParsedSessionData(
            inventory = withActiveSortedFirst(parseRecordsLegacy(lines)),
            format = SessionFileFormat.LEGACY,
        )
    }
}

/**
 * Serializes an [AccountInventory] to the v3 line format.
 *
 * Layout: schema header, an `active:` pointer line, then 4-line records
 * (label, userId, accessToken, server). Blank label/server lines mean absent.
 * Always terminates with a newline.
 */
internal fun serializeAccounts(inventory: AccountInventory): String {
    val lines = mutableListOf(SESSION_SCHEMA_HEADER_V3)
    lines += "$ACTIVE_MARKER ${inventory.activeUserId.orEmpty()}"
    inventory.accounts.forEach { account ->
        lines += account.label.orEmpty()
        lines += account.userId
        lines += account.accessToken
        lines += account.server.orEmpty()
    }
    return lines.joinToString(separator = "\n") + "\n"
}

private fun parseVersion3(lines: List<String>): AccountInventory {
    val activeUserId = readActiveLine(lines)

    val records = lines.drop(2)
    val accounts = mutableListOf<StoredAccount>()
    var invalid = 0
    var index = 0
    while (index < records.size) {
        val label = records.getOrNull(index)?.ifBlank { null }
        val userId = records.getOrNull(index + 1)
        val accessToken = records.getOrNull(index + 2)
        val server = records.getOrNull(index + 3)?.ifBlank { null }

        if (userId == null || accessToken == null) {
            invalid += 1
            break
        }
        if (userId.isBlank() || accessToken.isBlank()) {
            invalid += 1
        } else {
            accounts += StoredAccount(userId = userId, accessToken = accessToken, server = server, label = label)
        }
        index += LINES_PER_RECORD_V3
    }
    return AccountInventory(accounts = accounts, activeUserId = activeUserId, invalidAccounts = invalid)
}

/**
 * v2 layout: header, `active:` line, then 3-line records (no label).
 */
private fun parseVersion2(lines: List<String>): AccountInventory {
    val activeUserId = readActiveLine(lines)
    val (accounts, invalid) = parseRecordsLegacy(lines.drop(2))
    return AccountInventory(accounts = accounts, activeUserId = activeUserId, invalidAccounts = invalid)
}

private fun readActiveLine(lines: List<String>): String? {
    val activeRaw = lines.getOrNull(1)?.trim().orEmpty()
    return activeRaw
        .removePrefix("$ACTIVE_MARKER ")
        .removePrefix(ACTIVE_MARKER)
        .trim()
        .ifBlank { null }
}

/**
 * Parses positional 3-line records (userId, accessToken, server) for v1/v2/legacy
 * inputs. No label is present in these formats.
 */
private fun parseRecordsLegacy(lines: List<String>): Pair<List<StoredAccount>, Int> {
    val accounts = mutableListOf<StoredAccount>()
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
            accounts += StoredAccount(userId = userId, accessToken = accessToken, server = server, label = null)
        }
        index += LINES_PER_RECORD_LEGACY
    }
    return accounts to invalid
}

/**
 * For older inputs there is no active pointer, so the active account is the
 * lexicographically-first record. This preserves the historical selection rule
 * during migration to v3.
 */
private fun withActiveSortedFirst(parsed: Pair<List<StoredAccount>, Int>): AccountInventory {
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

package wirecli.auth

internal const val SESSION_SCHEMA_PREFIX = "wire-cli-session-store:"
internal const val SESSION_SCHEMA_VERSION = "1"
internal const val SESSION_SCHEMA_HEADER = "$SESSION_SCHEMA_PREFIX$SESSION_SCHEMA_VERSION"
private const val LINES_PER_RECORD = 3

internal enum class SessionFileFormat {
    VERSIONED,
    LEGACY,
    UNSUPPORTED_VERSION
}

internal data class ParsedSessionData(
    val inventory: SessionInventory,
    val format: SessionFileFormat,
    val rawPayloadLines: List<String>
)

internal fun parseStoredSessions(lines: List<String>): ParsedSessionData {
    if (lines.isEmpty()) {
        return ParsedSessionData(
            inventory = SessionInventory(activeSession = null, validSessions = 0, invalidSessions = 0),
            format = SessionFileFormat.VERSIONED,
            rawPayloadLines = emptyList()
        )
    }

    val firstLine = lines.first()
    return if (firstLine.startsWith(SESSION_SCHEMA_PREFIX)) {
        parseVersionedSessions(lines)
    } else {
        ParsedSessionData(
            inventory = parseSessionRecords(lines),
            format = SessionFileFormat.LEGACY,
            rawPayloadLines = lines
        )
    }
}

internal fun serializeVersionedSessions(payloadLines: List<String>): String {
    val body = payloadLines.joinToString(separator = "\n")
    return if (body.isEmpty()) "$SESSION_SCHEMA_HEADER\n" else "$SESSION_SCHEMA_HEADER\n$body\n"
}

internal fun serializeSingleSession(session: AuthSession): String {
    val serverLine = session.server.orEmpty()
    return serializeVersionedSessions(
        payloadLines = listOf(session.userId, session.accessToken, serverLine)
    )
}

private fun parseVersionedSessions(lines: List<String>): ParsedSessionData {
    val header = lines.first()
    val version = header.removePrefix(SESSION_SCHEMA_PREFIX)
    if (version != SESSION_SCHEMA_VERSION) {
        val message = AuthMessages.unsupportedSessionSchema(version)
        return ParsedSessionData(
            inventory = SessionInventory(
                activeSession = null,
                validSessions = 0,
                invalidSessions = 1,
                diagnosticMessage = message
            ),
            format = SessionFileFormat.UNSUPPORTED_VERSION,
            rawPayloadLines = emptyList()
        )
    }

    val payload = lines.drop(1)
    return ParsedSessionData(
        inventory = parseSessionRecords(payload),
        format = SessionFileFormat.VERSIONED,
        rawPayloadLines = payload
    )
}

private fun parseSessionRecords(lines: List<String>): SessionInventory {
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

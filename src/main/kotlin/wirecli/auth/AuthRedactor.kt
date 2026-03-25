package wirecli.auth

object AuthRedactor {
    private const val REDACTED = "<redacted>"
    private const val BEARER_GROUP = 1
    private const val URL_SCHEME_GROUP = 1
    private const val URL_DELIMITER_GROUP = 3
    private const val KEY_NAME_GROUP = 1
    private const val KEY_SEPARATOR_GROUP = 2
    private const val LEADING_QUOTE_GROUP = 3
    private const val KEY_VALUE_GROUP = 4
    private const val TRAILING_QUOTE_GROUP = 5

    private val bearerTokenRegex = Regex("""(?i)\b(Bearer)\s+([A-Za-z0-9._~+\-/=]{8,})""")
    private val jwtRegex = Regex("""\b[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b""")
    private val keyValueRegex =
        Regex(
            """(?i)\b(password|pass|pwd|token|access[_-]?token|""" +
                """refresh[_-]?token|authorization|api[_-]?key|secret)""" +
                """\b(\s*[:=]\s*)(["'])?([^\s,"']+)(["'])?""",
        )
    private val urlCredentialRegex = Regex("""(https?://[^:\s/]+:)([^@\s/]+)(@)""")

    fun redact(value: String): String {
        if (value.isBlank()) return value

        return value
            .replace(bearerTokenRegex) { "${it.groupValues[BEARER_GROUP]} $REDACTED" }
            .replace(jwtRegex, REDACTED)
            .replace(urlCredentialRegex) { "${it.groupValues[URL_SCHEME_GROUP]}$REDACTED${it.groupValues[URL_DELIMITER_GROUP]}" }
            .replace(keyValueRegex) {
                val leadingQuote = it.groupValues[LEADING_QUOTE_GROUP]
                val trailingQuote = it.groupValues[TRAILING_QUOTE_GROUP]
                "${it.groupValues[KEY_NAME_GROUP]}${it.groupValues[KEY_SEPARATOR_GROUP]}$leadingQuote$REDACTED$trailingQuote"
            }
    }
}

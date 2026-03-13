package wirecli.auth

object AuthRedactor {
    private const val REDACTED = "<redacted>"
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
            .replace(bearerTokenRegex) { "${it.groupValues[1]} $REDACTED" }
            .replace(jwtRegex, REDACTED)
            .replace(urlCredentialRegex) { "${it.groupValues[1]}$REDACTED${it.groupValues[3]}" }
            .replace(keyValueRegex) {
                val leadingQuote = it.groupValues[3]
                val trailingQuote = it.groupValues[5]
                "${it.groupValues[1]}${it.groupValues[2]}$leadingQuote$REDACTED$trailingQuote"
            }
    }
}

package wirecli.user

/**
 * Formats user views for CLI output in plain, JSON, and JSON Lines forms.
 *
 * JSON output is schema-versioned so scripts can detect breaking changes:
 * `{"schemaVersion":1,"users":[...]}`.
 */
class UserFormatter {
    fun toTable(users: List<UserView>): String {
        if (users.isEmpty()) return UserMessages.NO_RESULTS

        val sb = StringBuilder()
        sb.appendLine(String.format(FORMAT, "ID", "NAME", "HANDLE", "CONNECTION"))
        sb.appendLine("-".repeat(TABLE_WIDTH))
        for (user in users) {
            sb.appendLine(
                String.format(
                    FORMAT,
                    user.id.take(ID_TRUNCATE),
                    user.name.orDash().take(NAME_TRUNCATE),
                    user.handle.orDash().take(HANDLE_TRUNCATE),
                    user.connection.value,
                ),
            )
        }
        return sb.toString().trimEnd()
    }

    fun toJson(users: List<UserView>): String {
        val items = users.joinToString(",") { buildUserJsonObject(it) }
        return """{"schemaVersion":${UserListView.SCHEMA_VERSION},"users":[$items]}"""
    }

    fun toJsonLines(users: List<UserView>): String = users.joinToString("\n") { buildUserJsonObject(it) }

    fun toDetail(user: UserView): String =
        buildString {
            appendLine("ID: ${user.id}")
            appendLine("Name: ${user.name.orDash()}")
            appendLine("Handle: ${user.handle.orDash()}")
            appendLine("Email: ${user.email.orDash()}")
            appendLine("Team: ${user.team.orDash()}")
            appendLine("Connection: ${user.connection.value}")
        }.trimEnd()

    private fun buildUserJsonObject(user: UserView): String {
        val id = escapeJson(user.id)
        val name = user.name?.let { escapeJson(it) } ?: "null"
        val handle = user.handle?.let { escapeJson(it) } ?: "null"
        val email = user.email?.let { escapeJson(it) } ?: "null"
        val team = user.team?.let { escapeJson(it) } ?: "null"
        val connection = escapeJson(user.connection.value)

        val nameJson = if (user.name != null) "\"$name\"" else "null"
        val handleJson = if (user.handle != null) "\"$handle\"" else "null"
        val emailJson = if (user.email != null) "\"$email\"" else "null"
        val teamJson = if (user.team != null) "\"$team\"" else "null"

        return """{"id":"$id","name":$nameJson,"handle":$handleJson,"email":$emailJson,"team":$teamJson,"connection":"$connection"}"""
    }

    private fun String?.orDash(): String = if (isNullOrBlank()) "-" else this

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    companion object {
        private const val ID_TRUNCATE = 32
        private const val NAME_TRUNCATE = 22
        private const val HANDLE_TRUNCATE = 16
        private const val TABLE_WIDTH = 80
        private const val FORMAT = "%-34s %-24s %-17s %-14s"
    }
}

package wirecli.config

object CommandAccess {
    private val rootCommands =
        setOf(
            "login",
            "logout",
            "daemon",
            "backup",
            "profile",
            "me",
            "presence",
            "device",
            "conversation",
            "message",
            "doctor",
            "user",
            "connection",
            "download",
        )

    fun requiredCapability(args: Array<String>): String? {
        if (args.any { it == "--help" || it == "-h" || it == "--version" }) return null

        val rootIndex = args.indexOfFirst { it in rootCommands }
        if (rootIndex < 0) return null
        val root = args[rootIndex]
        val child = args.getOrNull(rootIndex + 1)?.takeUnless { it.startsWith("-") }

        return when (root) {
            "login" -> "auth.login"
            "logout" -> "auth.logout"
            "daemon" -> "daemon.run"
            "backup" -> childCapability("backup", child, setOf("import", "export", "create"))
            "me" -> "profile.read"
            "profile" ->
                if (child == null) {
                    "profile.read"
                } else {
                    childCapability("profile", child, setOf("update", "name"), "update")
                }
            "presence" ->
                when (child) {
                    "get" -> "presence.read"
                    "set" -> "presence.update"
                    else -> null
                }
            "device" ->
                when (child) {
                    "list", "info" -> "device.read"
                    "delete" -> "device.delete"
                    "verify" -> "device.verify"
                    else -> null
                }
            "conversation" -> if (child in setOf("list", "search", "get")) "conversation.read" else null
            "message" ->
                when (child) {
                    "fetch", "watch", "search" -> "message.read"
                    "send", "react", "typing", "delete", "set" -> "message.$child"
                    else -> null
                }
            "doctor" ->
                when (child) {
                    null, "status", "diagnose" -> "sync.read"
                    "sync" -> "sync.execute"
                    else -> null
                }
            "user" -> if (child in setOf("search", "get")) "user.read" else null
            "connection" ->
                when (child) {
                    "request" -> "connection.request"
                    "block", "unblock" -> "connection.block"
                    else -> null
                }
            "download" -> "download"
            else -> null
        }
    }

    private fun childCapability(
        domain: String,
        child: String?,
        knownChildren: Set<String>,
        operation: String? = null,
    ): String? = if (child in knownChildren) "$domain.${operation ?: child}" else null
}

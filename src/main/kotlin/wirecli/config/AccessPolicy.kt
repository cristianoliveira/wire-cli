package wirecli.config

import java.nio.file.Files
import java.nio.file.Path

/** Fail-closed capability allowlist when access control is enabled. */
data class AccessPolicy(
    val enabled: Boolean = false,
    val allowedCapabilities: Set<String> = emptySet(),
) {
    fun allows(capability: String): Boolean {
        if (!enabled) return true
        if (capability in allowedCapabilities) return true

        val domain = capability.substringBefore('.')
        if (domain in allowedCapabilities) return true

        return "read" in allowedCapabilities && capability.endsWith(".read")
    }
}

object AccessPolicyLoader {
    fun configPath(environment: Map<String, String> = System.getenv()): Path {
        environment["WIRECLI_CONFIG_FILE"]?.takeIf { it.isNotBlank() }?.let { return Path.of(it) }
        environment["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() }?.let {
            return Path.of(it, "wire", "config.yaml")
        }
        return Path.of(environment["HOME"] ?: ".", ".config", "wire", "config.yaml")
    }

    /** Human-facing denial text. Intentionally omits the config file path so the file location is not surfaced. */
    fun denialMessage(capability: String): String = "Access denied: '$capability' is not permitted by the access policy."

    fun load(path: Path = configPath()): AccessPolicy {
        if (!Files.exists(path)) return AccessPolicy()

        var inAccess = false
        var inAllow = false
        var enabled = false
        val allowed = mutableSetOf<String>()

        Files.readAllLines(path).forEach { rawLine ->
            val line = rawLine.substringBefore('#')
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            val indentation = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            if (indentation == 0) {
                inAccess = trimmed == "access:"
                inAllow = false
                return@forEach
            }
            if (!inAccess) return@forEach

            when {
                trimmed.startsWith("enabled:") -> {
                    enabled = trimmed.substringAfter(':').trim().toBooleanStrictOrNull()
                        ?: error("access.enabled must be true or false in $path")
                    inAllow = false
                }
                trimmed == "allow:" || trimmed == "allow: []" -> inAllow = trimmed == "allow:"
                inAllow && trimmed.startsWith("-") -> {
                    val capability = trimmed.removePrefix("-").trim()
                    require(capability.matches(Regex("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)?"))) {
                        "Invalid access capability '$capability' in $path"
                    }
                    allowed += capability
                }
            }
        }

        return AccessPolicy(enabled = enabled, allowedCapabilities = allowed)
    }
}

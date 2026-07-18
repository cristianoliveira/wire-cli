package wirecli.config

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessPolicyTest {
    @Test
    fun `missing configuration keeps full access`() {
        val path = Files.createTempDirectory("wire-config-test").resolve("missing.yaml")

        val policy = AccessPolicyLoader.load(path)

        assertTrue(policy.allows("message.send"))
    }

    @Test
    fun `enabled access policy blocks everything by default`() {
        val path = Files.createTempFile("wire-config", ".yaml")
        path.writeText("access:\n  enabled: true\n")

        val policy = AccessPolicyLoader.load(path)

        assertFalse(policy.allows("message.read"))
        assertFalse(policy.allows("message.send"))
    }

    @Test
    fun `enabled access policy permits only explicitly allowed capabilities`() {
        val path = Files.createTempFile("wire-config", ".yaml")
        path.writeText(
            """
            access:
              enabled: true
              allow:
                - auth.login
                - conversation.read
            """.trimIndent(),
        )

        val policy = AccessPolicyLoader.load(path)

        assertTrue(policy.allows("auth.login"))
        assertTrue(policy.allows("conversation.read"))
        assertFalse(policy.allows("message.read"))
        assertFalse(policy.allows("future.operation"))
    }

    @Test
    fun `read and domain capabilities authorize segments`() {
        val readPolicy = AccessPolicy(enabled = true, allowedCapabilities = setOf("read"))
        val messagePolicy = AccessPolicy(enabled = true, allowedCapabilities = setOf("message"))

        assertTrue(readPolicy.allows("message.read"))
        assertFalse(readPolicy.allows("message.send"))
        assertTrue(messagePolicy.allows("message.read"))
        assertTrue(messagePolicy.allows("message.send"))
        assertFalse(messagePolicy.allows("profile.read"))
    }

    @Test
    fun `disabled access policy ignores allow list`() {
        val path = Files.createTempFile("wire-config", ".yaml")
        path.writeText("access:\n  enabled: false\n  allow: []\n")

        val policy = AccessPolicyLoader.load(path)

        assertTrue(policy.allows("message.send"))
    }

    @Test
    fun `denial message reports capability without revealing the config file path`() {
        val message = AccessPolicyLoader.denialMessage("backup.export")

        assertTrue(message.contains("backup.export"), "denial message should name the denied capability")
        assertFalse(
            message.contains(AccessPolicyLoader.configPath().toString()),
            "denial message should not expose the config file location",
        )
        assertFalse(message.contains("config.yaml"), "denial message should not name the config file")
        assertFalse(message.contains(".config"), "denial message should not hint at the config directory")
    }

    @Test
    fun `resolves xdg config path and environment override`() {
        assertEquals(
            java.nio.file.Path.of("/tmp/config.yaml"),
            AccessPolicyLoader.configPath(
                mapOf("WIRECLI_CONFIG_FILE" to "/tmp/../tmp/config.yaml", "HOME" to "/home/me"),
            ).normalize(),
        )
        assertEquals(
            java.nio.file.Path.of("/xdg/wire/config.yaml"),
            AccessPolicyLoader.configPath(mapOf("XDG_CONFIG_HOME" to "/xdg", "HOME" to "/home/me")),
        )
        assertEquals(
            java.nio.file.Path.of("/home/me/.config/wire/config.yaml"),
            AccessPolicyLoader.configPath(mapOf("HOME" to "/home/me")),
        )
    }
}

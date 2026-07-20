package wirecli.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandAccessTest {
    @Test
    fun `classifies read only commands`() {
        assertEquals(
            "message.read",
            CommandAccess.requiredCapability(arrayOf("message", "fetch", "conversation")),
        )
        assertEquals(
            "conversation.read",
            CommandAccess.requiredCapability(arrayOf("--verbose", "conversation", "list")),
        )
        assertEquals("sync.read", CommandAccess.requiredCapability(arrayOf("doctor", "diagnose")))
        assertEquals("profile.read", CommandAccess.requiredCapability(arrayOf("profile")))
        assertEquals("profile.read", CommandAccess.requiredCapability(arrayOf("me")))
    }

    @Test
    fun `classifies mutating commands`() {
        assertEquals(
            "message.send",
            CommandAccess.requiredCapability(arrayOf("message", "send", "conversation", "hello")),
        )
        assertEquals(
            "connection.block",
            CommandAccess.requiredCapability(arrayOf("connection", "unblock", "user@wire.test")),
        )
        assertEquals("auth.logout", CommandAccess.requiredCapability(arrayOf("logout")))
        assertEquals("backup.export", CommandAccess.requiredCapability(arrayOf("backup", "export")))
        assertEquals("sync.execute", CommandAccess.requiredCapability(arrayOf("doctor", "sync")))
        assertEquals(
            "message.set",
            CommandAccess.requiredCapability(arrayOf("message", "set", "conversation", "--read", "message")),
        )
    }

    @Test
    fun `help and command groups require no capability`() {
        assertNull(CommandAccess.requiredCapability(arrayOf("--help")))
        assertNull(CommandAccess.requiredCapability(arrayOf("message", "--help")))
        assertNull(CommandAccess.requiredCapability(arrayOf("message")))
    }
}

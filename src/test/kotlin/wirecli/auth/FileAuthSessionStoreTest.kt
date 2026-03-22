package wirecli.auth

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FileAuthSessionStoreTest {
    @Test
    fun `writeActiveSession throws when session user id is blank`() {
        val tempDir = Files.createTempDirectory("wirecli-auth-session-test")
        val store = FileAuthSessionStore(sessionFile = tempDir.resolve("session").toFile())

        assertFailsWith<IllegalArgumentException> {
            store.writeActiveSession(
                AuthSession(
                    userId = " ",
                    accessToken = "access-token",
                    server = null,
                ),
            )
        }
    }

    @Test
    fun `readActiveSession throws when session file path is blank`() {
        val store = FileAuthSessionStore(sessionFile = File(""))

        assertFailsWith<IllegalStateException> {
            store.readActiveSession()
        }
    }
}

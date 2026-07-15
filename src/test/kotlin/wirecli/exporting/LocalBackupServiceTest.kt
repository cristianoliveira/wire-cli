package wirecli.exporting

import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalBackupServiceTest {
    @Test
    fun `requires active session`() {
        val service = DefaultLocalBackupService(noSessionProvider, FailingRuntime)

        val result = service.create(Path.of("backup.wbu"), null)

        assertEquals(
            LocalBackupResult.Failure("No active session. Please login with 'wire auth login'.", ExitCodes.UNAUTHORIZED),
            result,
        )
    }

    @Test
    fun `moves generated backup to requested destination`() {
        val generated = Files.createTempFile("generated", ".wbu")
        Files.writeString(generated, "backup")
        val destination = Files.createTempDirectory("backup-destination").resolve("wire.wbu")
        val service = DefaultLocalBackupService(sessionProvider, SuccessfulRuntime(generated))

        val result = service.create(destination, null)

        assertEquals(LocalBackupResult.Success(destination), result)
        assertTrue(destination.exists())
        assertEquals("backup", Files.readString(destination))
        assertFalse(generated.exists())
    }

    @Test
    fun `passes password to runtime`() {
        val generated = Files.createTempFile("generated", ".wbu")
        val runtime = SuccessfulRuntime(generated)
        val destination = Files.createTempDirectory("backup-destination").resolve("wire.wbu")
        val service = DefaultLocalBackupService(sessionProvider, runtime)

        service.create(destination, "secret")

        assertEquals("secret", runtime.password)
    }

    private class SuccessfulRuntime(private val generated: Path) : LocalCacheBackupRuntime {
        var password: String? = null

        override fun create(
            session: AuthSession,
            password: String?,
        ): LocalCacheBackupResult {
            this.password = password
            return LocalCacheBackupResult.Success(generated)
        }
    }

    private object FailingRuntime : LocalCacheBackupRuntime {
        override fun create(
            session: AuthSession,
            password: String?,
        ): LocalCacheBackupResult = error("must not be called")
    }

    private companion object {
        val noSessionProvider =
            object : SessionProvider {
                override fun readActiveSession(): AuthSession? = null
            }
        val sessionProvider =
            object : SessionProvider {
                override fun readActiveSession() = AuthSession("user@example.com", "token", null)
            }
    }
}

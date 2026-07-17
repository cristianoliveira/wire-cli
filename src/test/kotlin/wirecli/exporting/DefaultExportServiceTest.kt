package wirecli.exporting

import wirecli.auth.AuthSession
import wirecli.auth.ExitCodes
import wirecli.auth.SessionProvider
import wirecli.importing.ImportSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DefaultExportServiceTest {
    @Test
    fun `external backup does not require active session`() {
        val backup = Files.createTempFile("external", ".wbu")
        val exporter = RecordingExporter()
        val service = DefaultExportService(noSessionProvider, FailingLocalCacheRuntime, listOf(exporter))

        val result =
            service.export(
                ExportInput.ExternalBackup(backup),
                ImportSource.WIRE_BACKUP,
                Path.of("out"),
                null,
                ExportOptions(includeNames = true),
            )

        assertEquals(ExportResult.Success(0, 0, 0, Path.of("out")), result)
        assertEquals(backup, exporter.input)
        assertEquals(ExportOptions(includeNames = true), exporter.options)
    }

    @Test
    fun `local cache requires active session`() {
        val service = DefaultExportService(noSessionProvider, FailingLocalCacheRuntime, listOf(RecordingExporter()))

        val result = service.export(ExportInput.LocalCache, ImportSource.WIRE_BACKUP, Path.of("out"), null)

        assertEquals(ExportResult.Failure("No active session. Please login with 'wire auth login'.", ExitCodes.UNAUTHORIZED), result)
    }

    @Test
    fun `deletes temporary backup after successful export`() {
        val backup = Files.createTempFile("local-cache", ".wbu")
        val service = DefaultExportService(sessionProvider, SuccessfulLocalCacheRuntime(backup), listOf(RecordingExporter()))

        service.export(ExportInput.LocalCache, ImportSource.WIRE_BACKUP, Path.of("out"), null)

        assertFalse(backup.exists())
    }

    @Test
    fun `deletes temporary backup when export fails`() {
        val backup = Files.createTempFile("local-cache", ".wbu")
        val exporter = RecordingExporter(ExportResult.Failure("broken export"))
        val service = DefaultExportService(sessionProvider, SuccessfulLocalCacheRuntime(backup), listOf(exporter))

        val result = service.export(ExportInput.LocalCache, ImportSource.WIRE_BACKUP, Path.of("out"), null)

        assertEquals(ExportResult.Failure("broken export"), result)
        assertFalse(backup.exists())
    }

    private class RecordingExporter(
        private val result: ExportResult = ExportResult.Success(0, 0, 0, Path.of("out")),
    ) : Exporter {
        override val source = ImportSource.WIRE_BACKUP
        var input: Path? = null
        var options: ExportOptions = ExportOptions.DEFAULT

        override fun export(
            input: Path,
            destination: Path,
            password: String?,
            options: ExportOptions,
        ): ExportResult {
            this.input = input
            this.options = options
            return result
        }
    }

    private class SuccessfulLocalCacheRuntime(private val backup: Path) : LocalCacheBackupRuntime {
        override fun create(
            session: AuthSession,
            password: String?,
        ): LocalCacheBackupResult = LocalCacheBackupResult.Success(backup)
    }

    private object FailingLocalCacheRuntime : LocalCacheBackupRuntime {
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

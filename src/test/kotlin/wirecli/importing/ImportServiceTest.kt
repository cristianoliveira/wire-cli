package wirecli.importing

import wirecli.auth.AuthSession
import wirecli.auth.SessionProvider
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportServiceTest {
    private val session = AuthSession("user@wire.com", "token", "wire.com")

    @Test
    fun `imports with adapter matching requested source and active session`() {
        val importer = RecordingImporter(ImportSource.WIRE_BACKUP)
        val service = DefaultImportService(sessionProvider(session), listOf(importer))

        val result = service.import(Path("backup.wbu"), ImportSource.WIRE_BACKUP, null)

        assertEquals(ImportResult.Success, result)
        assertEquals(session, importer.session)
        assertEquals(Path("backup.wbu"), importer.importedPath)
    }

    @Test
    fun `rejects import without active session`() {
        val service = DefaultImportService(sessionProvider(null), listOf(RecordingImporter(ImportSource.WIRE_BACKUP)))

        val result = service.import(Path("backup.wbu"), ImportSource.WIRE_BACKUP, null)

        assertEquals(ImportResult.Failure("No active session. Run wire login to re-authenticate.", 11), result)
    }

    @Test
    fun `reports unsupported source when no adapter matches`() {
        val service = DefaultImportService(sessionProvider(session), emptyList())

        val result = service.import(Path("backup.wbu"), ImportSource.WIRE_BACKUP, null)

        assertEquals(ImportResult.Failure("unsupported import source: wire-backup", 1), result)
    }

    private fun sessionProvider(session: AuthSession?) =
        object : SessionProvider {
            override fun readActiveSession(): AuthSession? = session
        }

    private class RecordingImporter(override val source: ImportSource) : Importer {
        var session: AuthSession? = null
        var importedPath: Path? = null

        override fun import(
            session: AuthSession,
            input: Path,
            password: String?,
        ): ImportResult {
            this.session = session
            importedPath = input
            return ImportResult.Success
        }
    }
}

package wirecli.importing

import wirecli.auth.AuthSession
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class WireBackupImporterTest {
    private val session = AuthSession("user@wire.com", "token", "wire.com")

    @Test
    fun `restores wire backup into active Kalium cache`() {
        val runtime = RecordingWireBackupRuntime(WireBackupRestoreResult.Success)
        val importer = WireBackupImporter(runtime)
        val backup = java.nio.file.Files.createTempFile("wire-import-test", ".wbu")

        val result = importer.import(session, backup, null)

        assertEquals(ImportResult.Success, result)
        assertEquals(session, runtime.session)
        assertEquals(backup, runtime.path)
    }

    @Test
    fun `rejects missing backup file`() {
        val importer = WireBackupImporter(RecordingWireBackupRuntime(WireBackupRestoreResult.Success))

        val result = importer.import(session, Path("fixtures/missing.wbu"), null)

        assertEquals(ImportResult.Failure("backup file not found: fixtures/missing.wbu", 1), result)
    }

    @Test
    fun `maps invalid password failure`() {
        val importer =
            WireBackupImporter(
                RecordingWireBackupRuntime(WireBackupRestoreResult.Failure("missing or wrong backup password")),
            )
        val backup = java.nio.file.Files.createTempFile("wire-import-test", ".wbu")

        val result = importer.import(session, backup, "wrong")

        assertEquals(ImportResult.Failure("missing or wrong backup password", 1), result)
    }

    private class RecordingWireBackupRuntime(private val result: WireBackupRestoreResult) : WireBackupRuntime {
        var session: AuthSession? = null
        var path: Path? = null

        override fun restore(
            session: AuthSession,
            input: Path,
            password: String?,
        ): WireBackupRestoreResult {
            this.session = session
            path = input
            return result
        }
    }
}

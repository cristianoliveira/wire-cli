package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.exporting.ExportInput
import wirecli.exporting.ExportResult
import wirecli.exporting.ExportService
import wirecli.exporting.LocalBackupResult
import wirecli.exporting.LocalBackupService
import wirecli.importing.ImportResult
import wirecli.importing.ImportService
import wirecli.importing.ImportSource
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupCommandTest {
    @Test
    fun `routes import through backup command`() {
        val result =
            execute(
                BackupCommand(
                    importServiceProvider = { SuccessfulImportService },
                    exportServiceProvider = { SuccessfulExportService },
                    localBackupServiceProvider = { SuccessfulLocalBackupService },
                ),
                listOf("import", "backup.wbu"),
            )

        assertEquals(0, result.exitCode)
        assertEquals("Imported backup into local cache", result.stdout.trim())
    }

    private object SuccessfulImportService : ImportService {
        override fun import(
            input: Path,
            source: ImportSource,
            password: String?,
        ): ImportResult = ImportResult.Success
    }

    private object SuccessfulLocalBackupService : LocalBackupService {
        override fun create(
            destination: Path,
            password: String?,
        ): LocalBackupResult = LocalBackupResult.Success(destination)
    }

    private object SuccessfulExportService : ExportService {
        override fun export(
            input: ExportInput,
            source: ImportSource,
            destination: Path,
            password: String?,
        ): ExportResult = ExportResult.Success(0, 0, 0, destination)
    }

    private data class Result(val exitCode: Int, val stdout: String)

    private fun execute(
        command: BackupCommand,
        args: List<String>,
    ): Result {
        val output = ByteArrayOutputStream()
        val original = System.out
        var exitCode = 0
        try {
            System.setOut(PrintStream(output))
            command.parse(args)
        } catch (result: ProgramResult) {
            exitCode = result.statusCode
        } finally {
            System.setOut(original)
        }
        return Result(exitCode, output.toString(Charsets.UTF_8))
    }
}

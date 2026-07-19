package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.importing.ImportResult
import wirecli.importing.ImportService
import wirecli.importing.ImportSource
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportCommandTest {
    @Test
    fun `imports wire backup into local cache`() {
        val service = FakeImportService(ImportResult.Success)
        val command = ImportCommand { service }

        val result = execute(command, listOf("backup.wbu"))

        assertEquals(0, result.exitCode)
        assertEquals("Imported backup into local cache", result.stdout.trim())
        assertEquals(ImportSource.WIRE_BACKUP, service.source)
    }

    @Test
    fun `maps import failure exit code to stderr`() {
        val command = ImportCommand { FakeImportService(ImportResult.Failure("login first", 11)) }

        val result = execute(command, listOf("backup.wbu"))

        assertEquals(1, result.exitCode)
        assertEquals("login first", result.stderr.trim())
    }

    private class FakeImportService(private val result: ImportResult) : ImportService {
        var source: ImportSource? = null

        override fun import(
            input: Path,
            source: ImportSource,
            password: String?,
        ): ImportResult {
            this.source = source
            return result
        }
    }

    private data class ExecutionResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun execute(
        command: ImportCommand,
        args: List<String>,
    ): ExecutionResult {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        var exitCode = 0
        try {
            System.setOut(PrintStream(stdout))
            System.setErr(PrintStream(stderr))
            command.parse(args)
        } catch (result: ProgramResult) {
            exitCode = result.statusCode
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
        return ExecutionResult(exitCode, stdout.toString(Charsets.UTF_8), stderr.toString(Charsets.UTF_8))
    }
}

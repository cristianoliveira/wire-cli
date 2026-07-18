package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.exporting.ExportInput
import wirecli.exporting.ExportOptions
import wirecli.exporting.ExportResult
import wirecli.exporting.ExportService
import wirecli.importing.ImportSource
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportCommandTest {
    @Test
    fun `provided backup selects external backup`() {
        val service = FakeExportService(ExportResult.Success(2, 12, 4, Path("out")))
        val result =
            execute(
                ExportCommand { service },
                listOf("--format", "jsonl", "--destination", "out", "backup.wbu"),
            )

        assertEquals(0, result.exitCode)
        assertEquals(ExportInput.ExternalBackup(Path("backup.wbu")), service.input)
        assertEquals("Exported 2 conversations, 12 messages, and 4 users into out", result.stdout.trim())
    }

    @Test
    fun `omitted backup selects local cache`() {
        val service = FakeExportService(ExportResult.Success(1, 2, 3, Path("out")))

        val result = execute(ExportCommand { service }, listOf("--format", "jsonl", "--destination", "out"))

        assertEquals(0, result.exitCode)
        assertEquals(ExportInput.LocalCache, service.input)
        assertEquals(ExportOptions.DEFAULT, service.options)
    }

    @Test
    fun `include-names flag threads resolver option to service`() {
        val service = FakeExportService(ExportResult.Success(1, 2, 3, Path("out")))

        val result =
            execute(
                ExportCommand { service },
                listOf("--format", "jsonl", "--destination", "out", "backup.wbu", "--include-names"),
            )

        assertEquals(0, result.exitCode)
        assertEquals(ExportOptions(includeNames = true), service.options)
    }

    private class FakeExportService(private val result: ExportResult) : ExportService {
        var input: ExportInput? = null
        var options: ExportOptions = ExportOptions.DEFAULT

        override fun export(
            input: ExportInput,
            source: ImportSource,
            destination: Path,
            password: String?,
            options: ExportOptions,
        ): ExportResult {
            this.input = input
            this.options = options
            return result
        }
    }

    private data class ExecutionResult(val exitCode: Int, val stdout: String)

    private fun execute(
        command: ExportCommand,
        args: List<String>,
    ): ExecutionResult {
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        var exitCode = 0
        try {
            System.setOut(PrintStream(output))
            command.parse(args)
        } catch (result: ProgramResult) {
            exitCode = result.statusCode
        } finally {
            System.setOut(originalOut)
        }
        return ExecutionResult(exitCode, output.toString(Charsets.UTF_8))
    }
}

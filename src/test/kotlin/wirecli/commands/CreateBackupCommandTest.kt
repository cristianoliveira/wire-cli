package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.exporting.LocalBackupResult
import wirecli.exporting.LocalBackupService
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateBackupCommandTest {
    @Test
    fun `creates wbu at destination`() {
        val service = RecordingLocalBackupService()

        val result = execute(CreateBackupCommand { service }, listOf("--destination", "backup.wbu", "--password", "secret"))

        assertEquals(0, result.exitCode)
        assertEquals(Path.of("backup.wbu"), service.destination)
        assertEquals("secret", service.password)
        assertEquals("Created Wire backup at backup.wbu", result.stdout.trim())
    }

    private class RecordingLocalBackupService : LocalBackupService {
        var destination: Path? = null
        var password: String? = null

        override fun create(
            destination: Path,
            password: String?,
        ): LocalBackupResult {
            this.destination = destination
            this.password = password
            return LocalBackupResult.Success(destination)
        }
    }

    private data class ExecutionResult(val exitCode: Int, val stdout: String)

    private fun execute(
        command: CreateBackupCommand,
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

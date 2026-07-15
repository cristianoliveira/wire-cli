package wirecli.exporting

import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WireBackupJsonExporterTest {
    @Test
    fun `rejects missing backup file`() {
        val destination = Files.createTempDirectory("wire-export-test")

        val result = WireBackupJsonExporter().export(Path("fixtures/missing.wbu"), destination, null)

        assertEquals(ExportResult.Failure("backup file not found: fixtures/missing.wbu"), result)
    }

    @Test
    fun `rejects malformed backup file`() {
        val input = Files.createTempFile("wire-export-test", ".wbu")
        Files.writeString(input, "synthetic invalid backup")

        val result =
            WireBackupJsonExporter().export(
                input,
                Files.createTempDirectory("wire-export-test"),
                null,
            )

        assertIs<ExportResult.Failure>(result)
    }
}

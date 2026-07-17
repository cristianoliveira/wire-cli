package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.download.DownloadAssetResult
import wirecli.download.DownloadService
import wirecli.download.DownloadedAsset
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadCommandTest {
    @Test
    fun `download command prints asset path on success`() {
        val command =
            DownloadCommand {
                FakeDownloadService(
                    result =
                        DownloadAssetResult.Success(
                            DownloadedAsset(
                                path = "/tmp/downloads/photo.jpg",
                                size = 2456789,
                                name = "photo.jpg",
                            ),
                        ),
                )
            }

        val result = execute(command, listOf("conv-123", "msg-456"))

        assertEquals(0, result.exitCode)
        assertEquals(
            "Downloaded \"photo.jpg\" (2.3 MB) -> /tmp/downloads/photo.jpg",
            result.stdout.trim(),
        )
    }

    @Test
    fun `download command maps service failure to exit code and stderr`() {
        val command =
            DownloadCommand {
                FakeDownloadService(
                    result =
                        DownloadAssetResult.Failure(
                            message = "message is not an asset",
                            exitCode = 13,
                        ),
                )
            }

        val result = execute(command, listOf("conv-123", "msg-456"))

        assertEquals(13, result.exitCode)
        assertEquals("message is not an asset", result.stderr.trim())
    }

    @Test
    fun `download command passes output dir flag to service`() {
        var capturedOutputDir: String? = null
        val command =
            DownloadCommand {
                object : DownloadService {
                    override fun downloadAsset(
                        conversationId: String,
                        messageId: String,
                        outputDir: String,
                    ): DownloadAssetResult {
                        capturedOutputDir = outputDir
                        return DownloadAssetResult.Success(
                            DownloadedAsset(
                                path = "/custom/photo.jpg",
                                size = 100,
                                name = "photo.jpg",
                            ),
                        )
                    }
                }
            }

        val result = execute(command, listOf("conv-123", "msg-456", "--output", "/custom"))

        assertEquals(0, result.exitCode)
        assertEquals("/custom", capturedOutputDir)
    }

    @Test
    fun `download command uses current directory as default output`() {
        var capturedOutputDir: String? = null
        val command =
            DownloadCommand {
                object : DownloadService {
                    override fun downloadAsset(
                        conversationId: String,
                        messageId: String,
                        outputDir: String,
                    ): DownloadAssetResult {
                        capturedOutputDir = outputDir
                        return DownloadAssetResult.Success(
                            DownloadedAsset(
                                path = "/cwd/photo.jpg",
                                size = 100,
                                name = "photo.jpg",
                            ),
                        )
                    }
                }
            }

        val result = execute(command, listOf("conv-123", "msg-456"))

        assertEquals(0, result.exitCode)
        assertEquals(".", capturedOutputDir)
    }

    @Test
    fun `download command shows validation error for blank conversation ID`() {
        val command =
            DownloadCommand {
                FakeDownloadService()
            }

        val result = execute(command, listOf("", "msg-456"))

        assertEquals(14, result.exitCode)
    }

    @Test
    fun `download command shows validation error for blank message ID`() {
        val command =
            DownloadCommand {
                FakeDownloadService()
            }

        val result = execute(command, listOf("conv-123", ""))

        assertEquals(14, result.exitCode)
    }

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun execute(
        command: DownloadCommand,
        args: List<String>,
    ): ExecutionResult {
        val stdoutBuffer = java.io.ByteArrayOutputStream()
        val stderrBuffer = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err

        var exitCode = 0
        try {
            System.setOut(java.io.PrintStream(stdoutBuffer))
            System.setErr(java.io.PrintStream(stderrBuffer))
            command.parse(args)
        } catch (programResult: ProgramResult) {
            exitCode = programResult.statusCode
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        return ExecutionResult(
            exitCode = exitCode,
            stdout = stdoutBuffer.toString(Charsets.UTF_8),
            stderr = stderrBuffer.toString(Charsets.UTF_8),
        )
    }

    private class FakeDownloadService(
        private val result: DownloadAssetResult = DownloadAssetResult.Failure("unexpected", 99),
    ) : DownloadService {
        override fun downloadAsset(
            conversationId: String,
            messageId: String,
            outputDir: String,
        ): DownloadAssetResult = result
    }
}

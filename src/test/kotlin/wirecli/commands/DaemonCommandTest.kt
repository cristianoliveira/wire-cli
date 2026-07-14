package wirecli.commands

import com.github.ajalt.clikt.core.ProgramResult
import wirecli.sync.ConversationSyncStatusResult
import wirecli.sync.DiagnosticsResult
import wirecli.sync.HealthMetrics
import wirecli.sync.PerConversationDiagnosticsResult
import wirecli.sync.ResetResult
import wirecli.sync.SyncService
import wirecli.sync.SyncStatus
import wirecli.sync.SyncStatusResult
import wirecli.sync.SyncStatusView
import kotlin.test.Test
import kotlin.test.assertEquals

class DaemonCommandTest {
    @Test
    fun `daemon starts continuous sync and waits until process termination`() {
        var awaitedTermination = false
        val service =
            FakeSyncService(
                startResult =
                    SyncStatusResult.Success(
                        SyncStatusView(
                            status = SyncStatus.READY,
                            metrics = HealthMetrics(0L, 0, 100, "2026-07-14T10:00:00Z"),
                        ),
                    ),
            )
        val command =
            DaemonCommand(
                syncServiceProvider = { service },
                awaitTermination = { awaitedTermination = true },
            )

        val result = execute(command)

        assertEquals(0, result.exitCode)
        assertEquals(1, service.startCalls)
        assertEquals(true, awaitedTermination)
        assertEquals("Message sync daemon is active.", result.stdout.trim())
    }

    @Test
    fun `daemon reports sync startup failure without waiting`() {
        var awaitedTermination = false
        val service =
            FakeSyncService(
                startResult = SyncStatusResult.Failure("unable to start sync", 12),
            )
        val command =
            DaemonCommand(
                syncServiceProvider = { service },
                awaitTermination = { awaitedTermination = true },
            )

        val result = execute(command)

        assertEquals(12, result.exitCode)
        assertEquals(1, service.startCalls)
        assertEquals(false, awaitedTermination)
        assertEquals("unable to start sync", result.stderr.trim())
    }

    private fun execute(command: DaemonCommand): ExecutionResult {
        val stdoutBuffer = java.io.ByteArrayOutputStream()
        val stderrBuffer = java.io.ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        var exitCode = 0

        try {
            System.setOut(java.io.PrintStream(stdoutBuffer))
            System.setErr(java.io.PrintStream(stderrBuffer))
            command.parse(emptyList())
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

    private data class ExecutionResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    private class FakeSyncService(
        private val startResult: SyncStatusResult,
    ) : SyncService {
        var startCalls: Int = 0
            private set

        override fun startContinuousSync(): SyncStatusResult {
            startCalls += 1
            return startResult
        }

        override fun forceSyncAndWait(): SyncStatusResult = error("not used")

        override fun getCurrentSyncStatus(): SyncStatusResult = error("not used")

        override fun getDiagnosticsReport(): DiagnosticsResult = error("not used")

        override fun resetSync(force: Boolean): ResetResult = error("not used")

        override fun getConversationSyncStatus(conversationId: String): ConversationSyncStatusResult = error("not used")

        override fun getPerConversationDiagnostics(conversationId: String): PerConversationDiagnosticsResult = error("not used")
    }
}

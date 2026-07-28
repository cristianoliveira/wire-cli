package wirecli.hooks

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class FileHookRunnerTest {
    @Test
    fun `runs executable hook from xdg config directory`() {
        val configHome = createTempDirectory("wire-hooks-test")
        val hook = configHome.resolve("wire/hooks/ongoing-call.sh")
        hook.parent.createDirectories()
        hook.createFile()
        check(hook.toFile().setExecutable(true))
        val executor = RecordingHookProcessExecutor()
        val runner =
            FileHookRunner(
                environment = mapOf("XDG_CONFIG_HOME" to configHome.toString()),
                processExecutor = executor,
            )

        val result = runner.run(HookEvent.ONGOING_CALL, mapOf("WIRE_CALLER_ID" to "alice@example.com"))

        assertEquals(HookRunResult.Completed(0), result)
        assertEquals(hook, executor.path)
        assertEquals("alice@example.com", executor.variables["WIRE_CALLER_ID"])
    }

    @Test
    fun `uses home config directory when xdg config home is absent`() {
        val home = createTempDirectory("wire-hooks-home-test")
        val hook = home.resolve(".config/wire/hooks/ongoing-call.sh")
        hook.parent.createDirectories()
        hook.createFile()
        check(hook.toFile().setExecutable(true))
        val executor = RecordingHookProcessExecutor()
        val runner =
            FileHookRunner(
                environment = mapOf("HOME" to home.toString()),
                processExecutor = executor,
            )

        runner.run(HookEvent.ONGOING_CALL, emptyMap())

        assertEquals(hook, executor.path)
    }

    @Test
    fun `skips hook when file is absent`() {
        val runner =
            FileHookRunner(
                environment = mapOf("XDG_CONFIG_HOME" to createTempDirectory("wire-hooks-missing").toString()),
                processExecutor = RecordingHookProcessExecutor(),
            )

        val result = runner.run(HookEvent.ONGOING_CALL, emptyMap())

        assertEquals(HookRunResult.NotConfigured, result)
    }

    @Test
    fun `reports hook that is not executable`() {
        val configHome = createTempDirectory("wire-hooks-non-executable")
        val hook = configHome.resolve("wire/hooks/ongoing-call.sh")
        hook.parent.createDirectories()
        hook.createFile()
        check(hook.toFile().setExecutable(false))
        val runner =
            FileHookRunner(
                environment = mapOf("XDG_CONFIG_HOME" to configHome.toString()),
                processExecutor = RecordingHookProcessExecutor(),
            )

        val result = runner.run(HookEvent.ONGOING_CALL, emptyMap())

        assertEquals(HookRunResult.NotExecutable(hook), result)
    }

    private class RecordingHookProcessExecutor : HookProcessExecutor {
        var path: Path? = null
        var variables: Map<String, String> = emptyMap()

        override fun execute(
            path: Path,
            variables: Map<String, String>,
        ): HookRunResult {
            this.path = path
            this.variables = variables
            return HookRunResult.Completed(0)
        }
    }
}

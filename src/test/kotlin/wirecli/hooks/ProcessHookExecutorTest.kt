package wirecli.hooks

import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessHookExecutorTest {
    @Test
    fun `passes hook variables without exposing hook output`() {
        val directory = createTempDirectory("wire-hook-process")
        val output = directory.resolve("result.txt")
        val hook = directory.resolve("hook.sh")
        hook.writeText("#!/usr/bin/env bash\nprintf '%s' \"${'$'}WIRE_CALLER_ID\" > \"${'$'}TEST_OUTPUT\"\n")
        check(hook.toFile().setExecutable(true))
        val executor = ProcessHookExecutor(timeoutMillis = 2_000)

        val result =
            executor.execute(
                hook,
                mapOf(
                    "WIRE_CALLER_ID" to "alice@example.com",
                    "TEST_OUTPUT" to output.toString(),
                ),
            )

        assertEquals(HookRunResult.Completed(0), result)
        assertEquals("alice@example.com", output.readText())
    }

    @Test
    fun `terminates hook after configured timeout`() {
        val directory = createTempDirectory("wire-hook-process-timeout")
        val hook = directory.resolve("hook.sh")
        hook.writeText("#!/usr/bin/env bash\nwhile :; do :; done\n")
        check(hook.toFile().setExecutable(true))
        val executor = ProcessHookExecutor(timeoutMillis = 50)

        val result = executor.execute(hook, emptyMap())

        assertEquals(HookRunResult.TimedOut(50), result)
    }

    @Test
    fun `returns non-zero hook exit code`() {
        val directory = createTempDirectory("wire-hook-process-failure")
        val hook = directory.resolve("hook.sh")
        hook.writeText("#!/usr/bin/env bash\nexit 9\n")
        check(hook.toFile().setExecutable(true))
        val executor = ProcessHookExecutor(timeoutMillis = 2_000)

        val result = executor.execute(hook, emptyMap())

        assertEquals(HookRunResult.Completed(9), result)
    }
}

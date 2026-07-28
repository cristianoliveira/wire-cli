package wirecli.hooks

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

enum class HookEvent(val fileName: String) {
    ONGOING_CALL("ongoing-call.sh"),
}

sealed interface HookRunResult {
    data object NotConfigured : HookRunResult

    data class NotExecutable(val path: Path) : HookRunResult

    data class Completed(val exitCode: Int) : HookRunResult

    data class TimedOut(val timeoutMillis: Long) : HookRunResult

    data class Failed(val message: String) : HookRunResult
}

interface HookRunner {
    fun run(
        event: HookEvent,
        variables: Map<String, String>,
    ): HookRunResult
}

internal interface HookProcessExecutor {
    fun execute(
        path: Path,
        variables: Map<String, String>,
    ): HookRunResult
}

internal class FileHookRunner(
    private val environment: Map<String, String>,
    private val processExecutor: HookProcessExecutor = ProcessHookExecutor(),
) : HookRunner {
    override fun run(
        event: HookEvent,
        variables: Map<String, String>,
    ): HookRunResult {
        val hookPath = resolveHookPath(event)
        return when {
            hookPath == null || !Files.isRegularFile(hookPath) -> HookRunResult.NotConfigured
            !Files.isExecutable(hookPath) -> HookRunResult.NotExecutable(hookPath)
            else -> processExecutor.execute(hookPath, variables)
        }
    }

    private fun resolveHookPath(event: HookEvent): Path? {
        val configHome =
            environment["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() }
                ?: environment["HOME"]?.takeIf { it.isNotBlank() }?.let { "$it/.config" }
                ?: return null
        return Path.of(configHome, "wire", "hooks", event.fileName)
    }
}

internal class ProcessHookExecutor(
    private val timeoutMillis: Long = DEFAULT_HOOK_TIMEOUT_MS,
) : HookProcessExecutor {
    override fun execute(
        path: Path,
        variables: Map<String, String>,
    ): HookRunResult {
        return runCatching {
            val processBuilder =
                ProcessBuilder(path.toAbsolutePath().toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
            processBuilder.environment().putAll(variables)
            val process = processBuilder.start()
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly().waitFor()
                return HookRunResult.TimedOut(timeoutMillis)
            }
            HookRunResult.Completed(process.exitValue())
        }.getOrElse { error ->
            HookRunResult.Failed(error.message ?: "Hook process failed to start.")
        }
    }

    private companion object {
        const val DEFAULT_HOOK_TIMEOUT_MS = 10_000L
    }
}

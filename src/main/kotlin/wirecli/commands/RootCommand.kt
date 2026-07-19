package wirecli.commands

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.LoggerFactory
import wirecli.profile.ProfileResult
import wirecli.profile.ProfileService
import java.io.File

private val logger by lazy { KotlinLogging.logger {} }

private const val MAX_LIVE_STATE_BYTES = 256

class RootCommand(
    private val profileServiceProvider: () -> ProfileService,
) : CliktCommand(
        name = "wire",
        invokeWithoutSubcommand = true,
        help =
            "Wire CLI for account management, conversations, messages, devices, sync, and backups.\n\n" +
                "Logs are saved to: ~/.cache/wire-cli/logs/\n" +
                "Console logs are off by default; use --verbose or --log-level to enable them.",
    ) {
    private val verbose by option(
        "--verbose",
        "-v",
        help = "Enable console debug logs (also sets file log level to DEBUG)",
    ).flag()

    private val logLevel by option(
        "--log-level",
        help = "Log level: TRACE, DEBUG, INFO, WARN, ERROR (also enables console logs)",
    )

    private val logDir by option("--log-dir", help = "Custom log directory (default: ~/.cache/wire-cli/logs)")

    override fun run() {
        configureLogging()
        // Show live state only when no subcommand is invoked.
        // When a subcommand is invoked, it owns stdout.
        if (currentContext.invokedSubcommand == null) {
            echo(buildLiveState())
        }
    }

    private fun buildLiveState(): String {
        val profileService = runCatching { profileServiceProvider() }.getOrNull()
        val profile = profileService?.let { runCatching { it.getCurrentProfile() }.getOrNull() }

        return when (profile) {
            is ProfileResult.Success -> {
                val handle = profile.profile.handle ?: profile.profile.email ?: "unknown"
                """
                    |wire: authenticated as $handle
                    |next: wire message list --json
                """.trimMargin().trimEnd()
            }
            else -> {
                """
                    |wire: not authenticated — run wire login to start
                    |next: wire login --email <you@example.com> --password <password>
                """.trimMargin().trimEnd()
            }
        }.also { output ->
            check(output.toByteArray().size <= MAX_LIVE_STATE_BYTES) {
                "live state exceeded byte budget"
            }
        }
    }

    private fun configureLogging() {
        // Apply log level from --log-level or WIRECLI_LOG_LEVEL env var
        val effectiveLevel =
            when {
                verbose -> "DEBUG" // --verbose takes priority
                logLevel != null -> logLevel!!.uppercase()
                else -> System.getenv("WIRECLI_LOG_LEVEL")?.uppercase() ?: "INFO"
            }

        try {
            val logbackContext = LoggerFactory.getILoggerFactory() as LoggerContext
            val rootLogger = logbackContext.getLogger(Logger.ROOT_LOGGER_NAME)
            rootLogger.level = Level.valueOf(effectiveLevel)
            System.setProperty("WIRECLI_LOG_LEVEL", effectiveLevel)
        } catch (e: ClassCastException) {
            logger.debug(e) { "Skipping custom log-level setup due to logger backend mismatch." }
        } catch (e: IllegalArgumentException) {
            logger.debug(e) { "Skipping custom log-level setup due to invalid log level value." }
        } catch (e: SecurityException) {
            logger.debug(e) { "Skipping custom log-level setup due to restricted system properties." }
        }

        // Set log directory from option or default
        val logDirPath =
            logDir
                ?: File(System.getProperty("user.home"), ".cache/wire-cli/logs").absolutePath
        System.setProperty("WIRECLI_LOG_DIR", logDirPath)

        // Create log directory if it doesn't exist
        try {
            File(logDirPath).mkdirs()
        } catch (e: SecurityException) {
            // Directory creation failure is intentionally caught and logged to stderr.
            // This is non-critical - logging will still function with system defaults.
            // Reason: Permission issues or filesystem errors should not block CLI startup.
            System.err.println(
                "Warning: Failed to create log directory at $logDirPath: ${e.message}",
            )
        }

        logger.info { "Wire CLI initialized (log level: $effectiveLevel, logs: $logDirPath)" }
    }
}

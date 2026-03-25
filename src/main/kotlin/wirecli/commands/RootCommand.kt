package wirecli.commands

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.LoggerFactory
import java.io.File

private val logger by lazy { KotlinLogging.logger {} }

class RootCommand : NoOpCliktCommand(
    name = "wire",
    help =
        "Wire CLI for authentication, profile, and presence commands.\n\n" +
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
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            // Logging configuration failure is intentionally caught and ignored.
            // This is non-critical to CLI operation - the application continues with default settings.
            // Reason: Logback may not be fully configured in all environments; this is acceptable.
        }

        // Set log directory from option or default
        val logDirPath =
            logDir
                ?: File(System.getProperty("user.home"), ".cache/wire-cli/logs").absolutePath
        System.setProperty("WIRECLI_LOG_DIR", logDirPath)

        // Create log directory if it doesn't exist
        try {
            File(logDirPath).mkdirs()
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
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

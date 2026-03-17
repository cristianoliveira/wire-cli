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
            "Use --verbose to see debug messages.",
) {
    private val verbose by option("--verbose", "-v", help = "Enable verbose (DEBUG) logging").flag()

    private val logLevel by option("--log-level", help = "Log level: TRACE, DEBUG, INFO, WARN, ERROR")

    private val logDir by option("--log-dir", help = "Custom log directory (default: ~/.cache/wire-cli/logs)")

    override fun run() {
        configureLogging()
    }

    private fun configureLogging() {
        // Apply log level from --log-level or WIRE_LOG_LEVEL env var
        val effectiveLevel =
            when {
                verbose -> "DEBUG" // --verbose takes priority
                logLevel != null -> logLevel!!.uppercase()
                else -> System.getenv("WIRE_LOG_LEVEL")?.uppercase() ?: "INFO"
            }

        try {
            val logbackContext = LoggerFactory.getILoggerFactory() as LoggerContext
            val rootLogger = logbackContext.getLogger(Logger.ROOT_LOGGER_NAME)
            rootLogger.level = Level.valueOf(effectiveLevel)
            System.setProperty("wire.log.level", effectiveLevel)
        } catch (e: Exception) {
            // Silently continue if logging setup fails
        }

        // Disable console logging if JSON output is enabled (to ensure pure JSON output)
        if (shouldSuppressConsoleLog()) {
            disableConsoleAppender()
        }

        // Set log directory from option or default
        val logDirPath =
            logDir
                ?: File(System.getProperty("user.home"), ".cache/wire-cli/logs").absolutePath
        System.setProperty("LOG_DIR", logDirPath)

        // Create log directory if it doesn't exist
        try {
            File(logDirPath).mkdirs()
        } catch (e: Exception) {
            System.err.println(
                "Warning: Failed to create log directory at $logDirPath: ${e.message}",
            )
        }

        logger.info { "Wire CLI initialized (logs: $logDirPath)" }
    }

    /**
     * Checks if console logging should be suppressed.
     * This is determined by the WIRE_CLI_SUPPRESS_CONSOLE_LOG system property,
     * which is set in Main.kt when --json or --json-lines flags are detected.
     *
     * @return true if console logging should be suppressed, false otherwise
     */
    private fun shouldSuppressConsoleLog(): Boolean {
        // Suppress console logging if:
        // 1. WIRE_CLI_SUPPRESS_CONSOLE_LOG property is set (JSON output mode)
        // 2. Running in test mode (WIRE_BACKEND environment variable is set to "stub")
        val suppressProperty = "true".equals(System.getProperty("WIRE_CLI_SUPPRESS_CONSOLE_LOG", "false"), ignoreCase = true)
        val testMode = "stub".equals(System.getenv("WIRE_BACKEND"), ignoreCase = true)
        return suppressProperty || testMode
    }

    /**
     * Disables the CONSOLE appender from the logback configuration.
     * This ensures that logging output doesn't interfere with JSON output.
     *
     * The FILE appender is kept enabled for debugging purposes.
     *
     * @post CONSOLE appender is removed from root logger
     */
    private fun disableConsoleAppender() {
        try {
            val logbackContext = LoggerFactory.getILoggerFactory() as LoggerContext
            val rootLogger = logbackContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)

            // Find and remove the CONSOLE appender
            val appenderIterator = rootLogger.iteratorForAppenders()
            while (appenderIterator.hasNext()) {
                val appender = appenderIterator.next()
                if ("CONSOLE".equals(appender.name, ignoreCase = true)) {
                    rootLogger.detachAppender(appender)
                    logger.debug { "Disabled CONSOLE appender for JSON output" }
                    break
                }
            }
        } catch (e: Exception) {
            System.err.println(
                "Warning: Failed to disable console appender: ${e.message}",
            )
        }
    }
}

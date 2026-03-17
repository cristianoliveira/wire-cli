package wirecli

import ch.qos.logback.classic.LoggerContext
import com.github.ajalt.clikt.core.subcommands
import io.github.oshai.kotlinlogging.KotlinLogging
import org.slf4j.LoggerFactory
import wirecli.commands.ConversationCommand
import wirecli.commands.DeviceCommand
import wirecli.commands.LoginCommand
import wirecli.commands.LogoutCommand
import wirecli.commands.PresenceCommand
import wirecli.commands.ProfileCommand
import wirecli.commands.RootCommand
import wirecli.commands.SyncCommand
import wirecli.runtime.KaliumRuntimeBootstrap
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Set log level from environment variable before logback initialization
    val logLevel = System.getenv("WIRECLI_LOG_LEVEL") ?: "INFO"
    System.setProperty("WIRECLI_LOG_LEVEL", logLevel.uppercase())

    // Detect if --json or --json-lines flags are present early
    // This allows us to suppress console logging before initialization
    val hasJsonOutput = args.contains("--json") || args.contains("--json-lines")
    System.setProperty("WIRE_CLI_SUPPRESS_CONSOLE_LOG", hasJsonOutput.toString())

    // Detect if running in test mode (WIRE_BACKEND environment variable)
    // In test mode, disable console logging to keep test output clean
    val isTestMode = "stub".equals(System.getenv("WIRE_BACKEND"), ignoreCase = true)
    if (isTestMode) {
        disableConsoleAppender()
    }

    // Create logger after potentially disabling console appender
    val logger = KotlinLogging.logger {}

    logger.debug { "Starting Wire CLI application" }
    logger.debug { "Command line arguments: ${args.joinToString(" ") { it.take(20) }}" }
    logger.debug { "JSON output mode: $hasJsonOutput" }

    val runtime =
        try {
            logger.debug { "Initializing Kalium runtime" }
            KaliumRuntimeBootstrap.create()
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize Kalium runtime" }
            throw e
        }

    var completed = false

    try {
        logger.debug { "Setting up Wire CLI command structure" }
        RootCommand()
            .subcommands(
                LoginCommand(runtime.authSessionService),
                LogoutCommand(runtime.authSessionService),
                ProfileCommand { runtime.profileService },
                PresenceCommand { runtime.presenceService },
                DeviceCommand { runtime.deviceService },
                ConversationCommand { runtime.conversationService },
                SyncCommand { runtime.syncService },
            )
            .main(args)
        completed = true
        logger.info { "Wire CLI execution completed successfully" }
    } catch (e: Exception) {
        logger.error(e) { "Uncaught exception during Wire CLI execution" }
        throw e
    } finally {
        logger.debug { "Shutting down Wire CLI application" }
        runCatching {
            runtime.close()
            logger.debug { "Kalium runtime shutdown complete" }
        }.onFailure { e ->
            logger.warn(e) { "Error during runtime shutdown" }
        }

        if (completed) {
            logger.info { "Application shutdown complete - exit code 0" }
            exitProcess(0)
        }
    }
}

/**
 * Disables the CONSOLE appender from the logback configuration.
 * This is used when JSON output is enabled or in test mode to prevent logs
 * from interfering with structured output or test assertions.
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
                break
            }
        }
    } catch (e: Exception) {
        System.err.println(
            "Warning: Failed to disable console appender: ${e.message}",
        )
    }
}

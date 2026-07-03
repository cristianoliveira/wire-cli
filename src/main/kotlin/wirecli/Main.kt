package wirecli

import com.github.ajalt.clikt.core.subcommands
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.commands.ConnectionCommand
import wirecli.commands.ConversationCommand
import wirecli.commands.DeviceCommand
import wirecli.commands.LoginCommand
import wirecli.commands.LogoutCommand
import wirecli.commands.MessageCommand
import wirecli.commands.PresenceCommand
import wirecli.commands.ProfileCommand
import wirecli.commands.RootCommand
import wirecli.commands.SyncCommand
import wirecli.commands.UserCommand
import wirecli.runtime.KaliumRuntimeBootstrap
import kotlin.system.exitProcess

private const val MAX_ARG_LOG_LENGTH = 20

fun main(args: Array<String>) {
    // Configure logging system properties BEFORE any logger is created.
    // - WIRECLI_LOG_LEVEL controls file logging and root level.
    // - WIRECLI_CONSOLE_LOG_LEVEL controls console logging (default OFF).
    val fileLogLevel = (System.getenv("WIRECLI_LOG_LEVEL") ?: "INFO").uppercase()
    System.setProperty("WIRECLI_LOG_LEVEL", fileLogLevel)

    val consoleLogLevel = determineConsoleLogLevel(args)
    System.setProperty("WIRECLI_CONSOLE_LOG_LEVEL", consoleLogLevel)

    // Create logger after setting properties
    val logger = KotlinLogging.logger {}

    val hasJsonOutput = args.contains("--json") || args.contains("--json-lines")

    logger.debug { "Starting Wire CLI application" }
    logger.debug { "Command line arguments: ${args.joinToString(" ") { it.take(MAX_ARG_LOG_LENGTH) }}" }
    logger.debug { "JSON output mode: $hasJsonOutput" }

    val runtime =
        try {
            logger.debug { "Initializing Kalium runtime" }
            KaliumRuntimeBootstrap.create()
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
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
                MessageCommand { runtime.messageService },
                SyncCommand { runtime.syncService },
                UserCommand { runtime.userService },
                ConnectionCommand { runtime.connectionService },
            )
            .main(args)
        completed = true
        logger.info { "Wire CLI execution completed successfully" }
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
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

private fun determineConsoleLogLevel(args: Array<String>): String {
    // Keep JSON output clean regardless of other flags.
    val hasJsonOutput = args.contains("--json") || args.contains("--json-lines")
    val hasVerbose = args.contains("--verbose") || args.contains("-v")
    return when {
        hasJsonOutput -> "OFF"
        // Explicit CLI flags take priority over environment.
        hasVerbose -> "DEBUG"
        // Check explicit log level from CLI, system property, or environment.
        else ->
            parseCliLogLevel(args)
                ?: System.getProperty("WIRECLI_CONSOLE_LOG_LEVEL")?.takeIf { it.isNotBlank() }?.uppercase()
                ?: System.getenv("WIRECLI_CONSOLE_LOG_LEVEL")?.takeIf { it.isNotBlank() }?.uppercase()
                ?: "OFF"
    }
}

private fun parseCliLogLevel(args: Array<String>): String? {
    val allowed = setOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF")
    var i = 0
    var result: String? = null
    while (i < args.size && result == null) {
        val arg = args[i]
        when {
            arg.startsWith("--log-level=") -> {
                val value = arg.substringAfter("--log-level=").trim().uppercase()
                if (value in allowed) result = value
            }
            arg == "--log-level" -> {
                val value = args.getOrNull(i + 1)?.trim()?.uppercase()
                if (!value.isNullOrBlank() && value in allowed) result = value
            }
        }
        i++
    }
    return result
}

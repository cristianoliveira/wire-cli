package wirecli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.subcommands
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.commands.AccountCommand
import wirecli.commands.BackupCommand
import wirecli.commands.ConnectionCommand
import wirecli.commands.ConversationCommand
import wirecli.commands.DaemonCommand
import wirecli.commands.DeviceCommand
import wirecli.commands.DownloadCommand
import wirecli.commands.LoginCommand
import wirecli.commands.LogoutCommand
import wirecli.commands.MeCommand
import wirecli.commands.MessageCommand
import wirecli.commands.PresenceCommand
import wirecli.commands.ProfileCommand
import wirecli.commands.RootCommand
import wirecli.commands.SyncCommand
import wirecli.commands.TeamCommand
import wirecli.commands.UserCommand
import wirecli.commands.WhoamiCommand
import wirecli.commands.processExitCode
import wirecli.config.AccessPolicyLoader
import wirecli.config.CommandAccess
import wirecli.runtime.FileDaemonProcessMarker
import wirecli.runtime.KaliumRuntimeBootstrap
import kotlin.system.exitProcess

private const val MAX_ARG_LOG_LENGTH = 20
private const val ACCESS_DENIED_EXIT_CODE = 11

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

    val accessPolicy = AccessPolicyLoader.load()
    val requiredCapability = CommandAccess.requiredCapability(args)
    if (requiredCapability != null && !accessPolicy.allows(requiredCapability)) {
        System.err.println(AccessPolicyLoader.denialMessage(requiredCapability))
        exitProcess(ACCESS_DENIED_EXIT_CODE)
    }

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

    val runtimeShutdownHook =
        Thread(
            { runtime.close() },
            "wire-cli-runtime-shutdown",
        )
    Runtime.getRuntime().addShutdownHook(runtimeShutdownHook)
    var completed = false

    try {
        logger.debug { "Setting up Wire CLI command structure" }
        RootCommand { runtime.profileService }
            .subcommands(
                LoginCommand(runtime.authSessionService),
                LogoutCommand(runtime.authSessionService),
                DaemonCommand(
                    syncServiceProvider = { runtime.syncService },
                    processMarkerProvider = { FileDaemonProcessMarker() },
                ),
                BackupCommand(
                    importServiceProvider = { runtime.importService },
                    exportServiceProvider = { runtime.exportService },
                    localBackupServiceProvider = { runtime.localBackupService },
                ),
                ProfileCommand { runtime.profileService },
                MeCommand { runtime.profileService },
                PresenceCommand { runtime.presenceService },
                DeviceCommand { runtime.deviceService },
                ConversationCommand { runtime.conversationService },
                MessageCommand { runtime.messageService },
                SyncCommand { runtime.syncService },
                UserCommand { runtime.userService },
                ConnectionCommand { runtime.connectionService },
                DownloadCommand { runtime.downloadService },
                TeamCommand { runtime.teamService },
                AccountCommand { runtime.accountService },
                WhoamiCommand { runtime.accountService },
            )
            .mainWithAxiExitCodes(args)
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
            Runtime.getRuntime().removeShutdownHook(runtimeShutdownHook)
        }
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

private fun CliktCommand.mainWithAxiExitCodes(args: Array<String>) {
    try {
        parse(args)
    } catch (error: CliktError) {
        echoFormattedHelp(error)
        val exitCode =
            if (error is UsageError) {
                wirecli.auth.ExitCodes.VALIDATION_ERROR
            } else {
                processExitCode(error.statusCode)
            }
        exitProcess(exitCode)
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

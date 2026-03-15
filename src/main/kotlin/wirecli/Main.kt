package wirecli

import com.github.ajalt.clikt.core.subcommands
import wirecli.commands.ConversationCommand
import wirecli.commands.DeviceCommand
import wirecli.commands.LoginCommand
import wirecli.commands.LogoutCommand
import wirecli.commands.MessageCommand
import wirecli.commands.PresenceCommand
import wirecli.commands.ProfileCommand
import wirecli.commands.RootCommand
import wirecli.commands.SyncCommand
import wirecli.runtime.KaliumRuntimeBootstrap
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val runtime = KaliumRuntimeBootstrap.create()
    var completed = false

    try {
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
            )
            .main(args)
        completed = true
    } finally {
        runCatching { runtime.close() }

        // Give threads 100ms to shutdown gracefully before forcing JVM exit
        // This ensures background Kalium SDK threads have time to wind down
        try {
            Thread.sleep(100)
        } catch (e: InterruptedException) {
            // Ignore interruption
        }

        // Force JVM exit to ensure background threads don't prevent process termination
        // This is necessary because Kalium SDK may spawn background threads that don't
        // respond to normal shutdown signals
        exitProcess(if (completed) 0 else 1)
    }
}

package wirecli

import com.github.ajalt.clikt.core.subcommands
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
                SyncCommand { runtime.syncService },
            )
            .main(args)
        completed = true
    } finally {
        runCatching { runtime.close() }

        if (completed) {
            exitProcess(0)
        }
    }
}

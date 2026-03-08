package wirecli

import wirecli.commands.LoginCommand
import wirecli.commands.LogoutCommand
import wirecli.commands.RootCommand
import wirecli.commands.ProfileCommand
import wirecli.runtime.KaliumRuntimeBootstrap
import com.github.ajalt.clikt.core.subcommands

fun main(args: Array<String>) {
    val runtime = KaliumRuntimeBootstrap.create()

    RootCommand()
        .subcommands(
            LoginCommand(runtime.authSessionService),
            LogoutCommand(runtime.authSessionService),
            ProfileCommand(runtime.profileService)
        )
        .main(args)
}

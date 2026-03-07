package com.example.wirecli

import com.example.wirecli.commands.LoginCommand
import com.example.wirecli.commands.LogoutCommand
import com.example.wirecli.commands.RootCommand
import com.example.wirecli.commands.ProfileCommand
import com.example.wirecli.runtime.KaliumRuntimeBootstrap
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

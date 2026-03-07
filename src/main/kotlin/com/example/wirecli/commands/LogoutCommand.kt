package com.example.wirecli.commands

import com.example.wirecli.auth.AuthResult
import com.example.wirecli.auth.AuthSessionService
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult

class LogoutCommand(
    private val authSessionService: AuthSessionService
) : CliktCommand(name = "logout", help = "Remove local authenticated session.") {
    override fun run() {
        when (val result = authSessionService.logout()) {
            is AuthResult.Success -> echo(result.message)
            is AuthResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

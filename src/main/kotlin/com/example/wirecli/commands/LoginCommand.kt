package com.example.wirecli.commands
// TODO: Password should be prompted interactively or read from environment variable for security.
// TODO: Add validation for email format and server URL.

import com.example.wirecli.auth.AuthResult
import com.example.wirecli.auth.AuthSessionService
import com.example.wirecli.auth.LoginInput
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required

class LoginCommand(
    private val authSessionService: AuthSessionService
) : CliktCommand(name = "login", help = "Authenticate and persist a local session.") {
    private val email by option("--email", help = "Email used to authenticate.").required()
    private val password by option("--password", help = "Password used to authenticate.").required()
    private val server by option("--server", help = "Wire backend URL override.")

    override fun run() {
        when (
            val result = authSessionService.login(
                LoginInput(
                    email = email,
                    password = password,
                    server = server
                )
            )
        ) {
            is AuthResult.Success -> echo(result.message)
            is AuthResult.Failure -> {
                echo(result.message, err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }
}

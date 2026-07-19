package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.github.oshai.kotlinlogging.KotlinLogging
import wirecli.auth.AuthRedactor
import wirecli.team.TeamReadResult
import wirecli.team.TeamService

private val logger = KotlinLogging.logger {}

class TeamCommand(
    private val teamServiceProvider: () -> TeamService,
) : CliktCommand(
        name = "team",
        help = "Manage team information (read).",
        invokeWithoutSubcommand = true,
    ) {
    init {
        subcommands(
            TeamReadCommand(teamServiceProvider),
        )
    }

    override fun run() {
        logger.debug { "Current subcommand: ${currentContext.invokedSubcommand}" }
        if (currentContext.invokedSubcommand == null) failWithUsage()
    }
}

class TeamReadCommand(
    private val teamServiceProvider: () -> TeamService,
) : CliktCommand(name = "read", help = "Show team details for the authenticated user.") {
    private val json by option("--json", help = "Output as JSON").flag(default = false)

    override fun run() {
        val teamService = teamServiceProvider()

        when (val result = teamService.read()) {
            is TeamReadResult.Success -> {
                val team = result.view
                if (json) {
                    outputAsJson(team)
                } else {
                    outputAsText(team)
                }
            }

            is TeamReadResult.Failure -> {
                echo(AuthRedactor.redact(result.message), err = true)
                throw ProgramResult(result.exitCode)
            }
        }
    }

    private fun outputAsText(team: wirecli.team.TeamView) {
        echo("ID: ${team.id}")
        echo("Name: ${team.name}")
        echo("Icon: ${team.icon}")
        echo("Creator: ${team.creator}")
        echo("Binding: ${team.binding}")
    }

    private fun outputAsJson(team: wirecli.team.TeamView) {
        val id = escapeJson(team.id)
        val name = escapeJson(team.name)
        val icon = escapeJson(team.icon)
        val creator = escapeJson(team.creator)

        val json = """{
  "id": "$id",
  "name": "$name",
  "icon": "$icon",
  "creator": "$creator",
  "binding": ${team.binding}
}"""
        echo(json)
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

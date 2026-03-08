package wirecli.commands

import com.github.ajalt.clikt.core.NoOpCliktCommand

class RootCommand : NoOpCliktCommand(
    name = "wire",
    help = "Wire CLI for authentication and profile commands."
)

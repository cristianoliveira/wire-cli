package wirecli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import wirecli.auth.ExitCodes
import wirecli.message.MessageService
import wirecli.message.SendTypingResult
import wirecli.message.TypingStatus

class MessageTypingCommand(
    private val messageServiceProvider: () -> MessageService,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) : CliktCommand(
        name = "typing",
        help = "Send typing started/stopped status for a conversation.",
        epilog =
            """
            EXAMPLES:
              Send STARTED and auto-stop after 10 seconds (default):
                wire message typing <conversation-id>

              Send STARTED without auto-stop:
                wire message typing <conversation-id> --state started --auto-stop-seconds 0

              Send STOPPED explicitly:
                wire message typing <conversation-id> --state stopped
            """.trimIndent(),
    ) {
    private val conversationId by argument(name = "CONVERSATION_ID", help = "The conversation ID")
    private val state by option("--state", help = "Typing state: started|stopped").default("started")
    private val autoStopSeconds by
        option("--auto-stop-seconds", help = "Auto-send STOPPED after STARTED (0 disables)").int().default(10)

    override fun run() {
        if (conversationId.isBlank()) {
            echo("validation error: conversation required", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }
        if (autoStopSeconds < 0) {
            echo("validation error: auto-stop-seconds must be >= 0", err = true)
            throw ProgramResult(ExitCodes.VALIDATION_ERROR)
        }

        val normalizedState = state.lowercase()
        val messageService = messageServiceProvider()

        when (normalizedState) {
            "started" -> {
                sendOrExit(messageService.sendTypingStatus(conversationId, TypingStatus.STARTED))
                echo("Typing started.")

                if (autoStopSeconds > 0) {
                    sleep(autoStopSeconds * 1_000L)
                    sendOrExit(messageService.sendTypingStatus(conversationId, TypingStatus.STOPPED))
                    echo("Typing stopped.")
                }
            }

            "stopped" -> {
                sendOrExit(messageService.sendTypingStatus(conversationId, TypingStatus.STOPPED))
                echo("Typing stopped.")
            }

            else -> {
                echo("validation error: state must be started or stopped", err = true)
                throw ProgramResult(ExitCodes.VALIDATION_ERROR)
            }
        }
    }

    private fun sendOrExit(result: SendTypingResult) {
        if (result is SendTypingResult.Failure) {
            echo(result.message, err = true)
            throw ProgramResult(result.exitCode)
        }
    }
}
